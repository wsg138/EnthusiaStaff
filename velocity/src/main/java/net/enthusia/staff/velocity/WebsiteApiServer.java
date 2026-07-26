package net.enthusia.staff.velocity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class WebsiteApiServer implements AutoCloseable {
    private static final UUID WEBSITE_SERVICE_ACTOR_ID = UUID.nameUUIDFromBytes(
            "enthusia:website-appeal-service".getBytes(StandardCharsets.UTF_8)
    );
    private static final Actor WEBSITE_SERVICE_ACTOR = new Actor(
            WEBSITE_SERVICE_ACTOR_ID,
            "Website Appeals",
            StaffRank.ADMIN
    );
    private static final Set<String> CLAIM_FIELDS =
            Set.of("accountId", "username", "punishmentCode");
    private static final Set<String> REVALIDATE_FIELDS =
            Set.of("accountId", "punishmentId", "codeGeneration");
    private static final Set<String> ACCEPT_FIELDS = Set.of(
            "appealId",
            "punishmentId",
            "caseId",
            "playerAccountId",
            "actorAccountId",
            "reason"
    );

    private final InetAddress bindAddress;
    private final int port;
    private final int maximumBodyBytes;
    private final int queueCapacity;
    private final int workerThreads;
    private final WebsiteModerationStore store;
    private final SanctionChangeService sanctionChanges;
    private final Supplier<OperationalMode> authorityMode;
    private final Clock clock;
    private final ErrorReporter errors;
    private final WebsiteApiAuthenticator authenticator;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;

    WebsiteApiServer(
            InetAddress bindAddress,
            int port,
            int maximumBodyBytes,
            int workerThreads,
            int queueCapacity,
            String bearerToken,
            String hmacSecret,
            Duration maximumSkew,
            WebsiteModerationStore store,
            SanctionChangeService sanctionChanges,
            Supplier<OperationalMode> authorityMode,
            Clock clock,
            ErrorReporter errors
    ) {
        if (bindAddress == null || !bindAddress.isLoopbackAddress()
                || port < 1 || port > 65_535
                || maximumBodyBytes < 1_024 || workerThreads < 1 || queueCapacity < 8
                || store == null || sanctionChanges == null || authorityMode == null
                || clock == null || errors == null) {
            throw new IllegalArgumentException("Website API server configuration is invalid");
        }
        this.bindAddress = bindAddress;
        this.port = port;
        this.maximumBodyBytes = maximumBodyBytes;
        this.workerThreads = workerThreads;
        this.queueCapacity = queueCapacity;
        this.store = store;
        this.sanctionChanges = sanctionChanges;
        this.authorityMode = authorityMode;
        this.clock = clock;
        this.errors = errors;
        this.authenticator = new WebsiteApiAuthenticator(
                bearerToken,
                hmacSecret,
                maximumSkew,
                store
        );
    }

    synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("Website API server is already running");
        }
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "EnthusiaStaff-Website-API-" + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor bounded = new ThreadPoolExecutor(
                workerThreads,
                workerThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threads,
                new ThreadPoolExecutor.AbortPolicy()
        );
        HttpServer created = null;
        try {
            created = HttpServer.create(new InetSocketAddress(bindAddress, port), queueCapacity);
            created.createContext("/", this::handle);
            created.setExecutor(bounded);
            created.start();
            executor = bounded;
            server = created;
        } catch (IOException | RuntimeException exception) {
            if (created != null) {
                created.stop(0);
            }
            bounded.shutdownNow();
            throw exception;
        }
    }

    private void handle(HttpExchange exchange) {
        String requestId = UUID.randomUUID().toString();
        try {
            if (exchange.getRemoteAddress() == null
                    || exchange.getRemoteAddress().getAddress() == null
                    || !exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
                throw new WebsiteApiException(403, "LOOPBACK_REQUIRED", "The API is loopback-only");
            }
            byte[] body = readBody(exchange);
            String target = rawTarget(exchange);
            authenticator.authenticate(
                    exchange.getRequestMethod(),
                    target,
                    exchange.getRequestHeaders(),
                    body,
                    clock.instant()
            );
            Object response = route(exchange, body);
            send(exchange, 200, response, requestId);
        } catch (WebsiteApiException exception) {
            sendError(exchange, exception.status(), exception.code(), exception.getMessage(), requestId);
        } catch (WebsiteModerationException exception) {
            int status = switch (exception.kind()) {
                case INVALID -> 400;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case INELIGIBLE -> 422;
                case UNAVAILABLE -> 503;
            };
            sendError(exchange, status, exception.code(), exception.getMessage(), requestId);
        } catch (RejectedExecutionException exception) {
            sendError(
                    exchange,
                    503,
                    "API_OVERLOADED",
                    "The moderation API is temporarily busy",
                    requestId
            );
        } catch (RuntimeException exception) {
            errors.report("Website API request " + requestId + " failed", exception);
            sendError(
                    exchange,
                    503,
                    "MODERATION_API_UNAVAILABLE",
                    "The moderation service could not complete the request",
                    requestId
            );
        } finally {
            exchange.close();
        }
    }

    private Object route(HttpExchange exchange, byte[] body) {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        if ("GET".equals(method) && path.equals("/v1/public/punishments")) {
            requireEmptyBody(body);
            requireQueryKeys(query, Set.of("type", "cursor", "limit"));
            PublicPunishmentFilter filter;
            try {
                filter = PublicPunishmentFilter.valueOf(
                        query.getOrDefault("type", "ALL").toUpperCase(java.util.Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                throw badRequest("INVALID_FILTER", "The punishment filter is invalid");
            }
            int limit = positiveInteger(query.get("limit"), 30, 1, 100);
            String cursor = query.get("cursor");
            PublicPunishmentPage page = store.listPublic(
                    filter,
                    cursor == null || cursor.isBlank() ? Optional.empty() : Optional.of(cursor),
                    limit,
                    clock.instant()
            );
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("items", page.items().stream().map(WebsiteApiServer::publicResponse).toList());
            response.put("nextCursor", page.nextCursor().orElse(null));
            return response;
        }
        if ("GET".equals(method) && path.equals("/v1/public/search")) {
            requireEmptyBody(body);
            requireQueryKeys(query, Set.of("q"));
            String search = query.get("q");
            if (search == null) {
                throw badRequest("INVALID_SEARCH", "Search for a username or case ID");
            }
            return Map.of("items", store.searchPublic(search, 100, clock.instant())
                    .stream()
                    .map(WebsiteApiServer::publicResponse)
                    .toList());
        }
        String casePrefix = "/v1/public/cases/";
        if ("GET".equals(method) && path.startsWith(casePrefix)
                && path.length() > casePrefix.length()
                && path.indexOf('/', casePrefix.length()) < 0) {
            requireEmptyBody(body);
            requireQueryKeys(query, Set.of());
            CaseId caseId;
            try {
                caseId = new CaseId(urlDecode(path.substring(casePrefix.length())));
            } catch (IllegalArgumentException exception) {
                throw badRequest("INVALID_CASE_ID", "The case ID is invalid");
            }
            PublicPunishment punishment = store.publicCase(caseId, clock.instant())
                    .orElseThrow(() -> new WebsiteApiException(
                            404,
                            "PUNISHMENT_NOT_FOUND",
                            "The punishment could not be found"
                    ));
            return publicResponse(punishment);
        }
        if ("POST".equals(method) && path.equals("/v1/website/punishment-codes/claim")) {
            requireQueryKeys(query, Set.of());
            ObjectNode input = jsonBody(exchange, body, CLAIM_FIELDS);
            PunishmentCodeBinding binding = store.claimCode(
                    text(input, "punishmentCode", 64),
                    uuidText(input, "accountId"),
                    minecraftUsername(input, "username"),
                    clock.instant()
            );
            return bindingResponse(binding);
        }
        if ("POST".equals(method) && path.equals("/v1/website/punishment-codes/revalidate")) {
            requireQueryKeys(query, Set.of());
            ObjectNode input = jsonBody(exchange, body, REVALIDATE_FIELDS);
            PunishmentCodeBinding binding = store.revalidateCode(
                    uuid(input, "punishmentId"),
                    integer(input, "codeGeneration", 1, Integer.MAX_VALUE),
                    uuidText(input, "accountId"),
                    clock.instant()
            );
            return bindingResponse(binding);
        }
        if ("POST".equals(method) && path.equals("/v1/website/appeals/accept")) {
            requireQueryKeys(query, Set.of());
            return acceptAppeal(exchange, jsonBody(exchange, body, ACCEPT_FIELDS));
        }
        throw new WebsiteApiException(404, "NOT_FOUND", "The requested API resource was not found");
    }

    private Object acceptAppeal(HttpExchange exchange, ObjectNode input) {
        String idempotencyKey = singleHeader(exchange.getRequestHeaders(), "idempotency-key", 120);
        UUID appealId = uuid(input, "appealId");
        UUID punishmentId = uuid(input, "punishmentId");
        CaseId caseId;
        try {
            caseId = new CaseId(text(input, "caseId", 16));
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_CASE_ID", "The case ID is invalid");
        }
        String playerAccountId = uuidText(input, "playerAccountId");
        String reviewerAccountId = uuidText(input, "actorAccountId");
        String reason = text(input, "reason", 1_000).trim();
        if (reason.length() < 10) {
            throw badRequest("INVALID_REASON", "The appeal decision reason is too short");
        }
        AppealAcceptancePreparation preparation = store.prepareAppealAcceptance(
                appealId,
                punishmentId,
                caseId,
                playerAccountId,
                idempotencyKey,
                clock.instant()
        );
        if (preparation instanceof AppealAcceptancePreparation.Rejected rejected) {
            int status = "PUNISHMENT_NOT_FOUND".equals(rejected.code()) ? 404 : 409;
            throw new WebsiteApiException(status, rejected.code(), rejected.message());
        }

        String internalReason = "Appeal " + appealId + " accepted by website reviewer "
                + reviewerAccountId + ": " + reason;
        SanctionChangeRequest request = new SanctionChangeRequest(
                new IdempotencyKey("website-appeal:" + digestIdempotency(idempotencyKey)),
                caseId,
                WEBSITE_SERVICE_ACTOR,
                SanctionChangeAction.END_EARLY,
                Optional.empty(),
                internalReason
        );
        SanctionChangeResult result = sanctionChanges.apply(request, authorityMode.get());
        if (result instanceof SanctionChangeResult.Applied applied) {
            String outcome = applied.replayed() ? "REPLAYED" : "APPLIED";
            store.completeAppealAcceptance(appealId, "APPLIED", outcome, clock.instant());
            return Map.of(
                    "applied", true,
                    "replayed", applied.replayed(),
                    "affectedSanctions", applied.affectedSanctions()
            );
        }
        SanctionChangeResult.Rejected rejected = (SanctionChangeResult.Rejected) result;
        if ("MODE_BLOCKED".equals(rejected.code())) {
            throw new WebsiteApiException(
                    503,
                    "AUTHORITY_NOT_ACTIVE",
                    "Punishment changes are temporarily unavailable"
            );
        }
        store.completeAppealAcceptance(
                appealId,
                "REJECTED",
                safeOutcomeCode(rejected.code()),
                clock.instant()
        );
        throw new WebsiteApiException(
                409,
                safeOutcomeCode(rejected.code()),
                "The accepted appeal could not change the punishment"
        );
    }

    private ObjectNode jsonBody(HttpExchange exchange, byte[] body, Set<String> allowedFields) {
        String contentType = exchange.getRequestHeaders().getFirst("content-type");
        if (contentType == null
                || !contentType.toLowerCase(java.util.Locale.ROOT).matches(
                        "application/json(?:\\s*;\\s*charset=utf-8)?")) {
            throw new WebsiteApiException(
                    415,
                    "JSON_REQUIRED",
                    "The request must use application/json with UTF-8"
            );
        }
        if (body.length == 0) {
            throw badRequest("INVALID_JSON", "A JSON request body is required");
        }
        JsonNode parsed;
        try {
            parsed = json.readTree(body);
        } catch (JsonProcessingException exception) {
            throw badRequest("INVALID_JSON", "The JSON request body is invalid");
        } catch (IOException exception) {
            throw badRequest("INVALID_JSON", "The JSON request body could not be read");
        }
        if (!(parsed instanceof ObjectNode object)) {
            throw badRequest("INVALID_JSON", "The JSON request body must be an object");
        }
        object.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw badRequest("UNKNOWN_FIELD", "The JSON request contains an unsupported field");
            }
        });
        return object;
    }

    private byte[] readBody(HttpExchange exchange) {
        String declared = exchange.getRequestHeaders().getFirst("content-length");
        if (declared != null) {
            try {
                long length = Long.parseLong(declared);
                if (length < 0 || length > maximumBodyBytes) {
                    throw new WebsiteApiException(
                            413,
                            "REQUEST_TOO_LARGE",
                            "The request body exceeds the accepted limit"
                    );
                }
            } catch (NumberFormatException exception) {
                throw badRequest("INVALID_CONTENT_LENGTH", "The request content length is invalid");
            }
        }
        try (InputStream input = exchange.getRequestBody()) {
            byte[] body = input.readNBytes(maximumBodyBytes + 1);
            if (body.length > maximumBodyBytes) {
                throw new WebsiteApiException(
                        413,
                        "REQUEST_TOO_LARGE",
                        "The request body exceeds the accepted limit"
                );
            }
            return body;
        } catch (IOException exception) {
            throw badRequest("REQUEST_READ_FAILED", "The request body could not be read");
        }
    }

    private static String rawTarget(HttpExchange exchange) {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        return query == null ? path : path + '?' + query;
    }

    private static Map<String, String> query(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }
        String[] pairs = rawQuery.split("&", -1);
        if (pairs.length > 16) {
            throw badRequest("INVALID_QUERY", "The request query is invalid");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            String name = urlDecode(separator < 0 ? pair : pair.substring(0, separator));
            String value = urlDecode(separator < 0 ? "" : pair.substring(separator + 1));
            if (name.isBlank() || name.length() > 64 || value.length() > 256
                    || values.putIfAbsent(name, value) != null) {
                throw badRequest("INVALID_QUERY", "The request query is invalid");
            }
        }
        return Map.copyOf(values);
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_ENCODING", "The request encoding is invalid");
        }
    }

    private static void requireQueryKeys(Map<String, String> query, Set<String> allowed) {
        if (!allowed.containsAll(query.keySet())) {
            throw badRequest("UNKNOWN_QUERY_PARAMETER", "The request query contains an unsupported parameter");
        }
    }

    private static int positiveInteger(String raw, int fallback, int minimum, int maximum) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum) {
                throw new NumberFormatException("Value outside range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw badRequest("INVALID_NUMBER", "A numeric query value is invalid");
        }
    }

    private static void requireEmptyBody(byte[] body) {
        if (body.length != 0) {
            throw badRequest("UNEXPECTED_BODY", "This request does not accept a body");
        }
    }

    private static String text(ObjectNode input, String field, int maximumLength) {
        JsonNode value = input.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maximumLength) {
            throw badRequest("INVALID_" + safeOutcomeCode(field), "A required request field is invalid");
        }
        return value.textValue();
    }

    private static String minecraftUsername(ObjectNode input, String field) {
        String value = text(input, field, 16);
        if (!value.matches("[A-Za-z0-9_]{3,16}")) {
            throw badRequest("INVALID_USERNAME", "The Minecraft username is invalid");
        }
        return value;
    }

    private static UUID uuid(ObjectNode input, String field) {
        String value = text(input, field, 36);
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_" + safeOutcomeCode(field), "A request identifier is invalid");
        }
    }

    private static String uuidText(ObjectNode input, String field) {
        return uuid(input, field).toString();
    }

    private static int integer(ObjectNode input, String field, int minimum, int maximum) {
        JsonNode value = input.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw badRequest("INVALID_" + safeOutcomeCode(field), "A numeric request field is invalid");
        }
        int converted = value.intValue();
        if (converted < minimum || converted > maximum) {
            throw badRequest("INVALID_" + safeOutcomeCode(field), "A numeric request field is invalid");
        }
        return converted;
    }

    private static Map<String, Object> publicResponse(PublicPunishment punishment) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("player", punishment.player());
        response.put("punishmentType", punishment.punishmentType());
        response.put("broadReason", punishment.broadReason());
        response.put("publicReason", punishment.publicReason());
        response.put("issuedAt", punishment.issuedAt().toString());
        response.put("expiresAt", punishment.expiresAt().map(Instant::toString).orElse(null));
        response.put(
                "remainingSeconds",
                punishment.remainingSeconds().isPresent()
                        ? punishment.remainingSeconds().getAsLong()
                        : null
        );
        response.put("state", punishment.state().name());
        response.put("caseId", punishment.caseId().value());
        response.put("appealAvailable", punishment.appealAvailable());
        return response;
    }

    private static Map<String, Object> bindingResponse(PunishmentCodeBinding binding) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("punishmentId", binding.punishmentId().toString());
        response.put("caseId", binding.caseId().value());
        response.put("codeGeneration", binding.codeGeneration());
        response.put("punishmentType", binding.punishmentType());
        response.put("boundUsername", binding.boundUsername());
        response.put("eligible", binding.eligible());
        response.put("eligibilityState", binding.eligibilityState());
        return response;
    }

    private static String singleHeader(Headers headers, String name, int maximumLength) {
        List<String> values = headers.get(name);
        if (values == null || values.size() != 1) {
            throw badRequest("IDEMPOTENCY_KEY_REQUIRED", "A single idempotency key is required");
        }
        String value = values.getFirst();
        if (value == null || value.isBlank() || value.length() > maximumLength
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw badRequest("INVALID_IDEMPOTENCY_KEY", "The idempotency key is invalid");
        }
        return value;
    }

    private static String digestIdempotency(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safeOutcomeCode(String value) {
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < value.length() && safe.length() < 64; index++) {
            char character = Character.toUpperCase(value.charAt(index));
            safe.append(Character.isLetterOrDigit(character) ? character : '_');
        }
        String result = safe.toString();
        return result.length() >= 3 ? result : "INVALID_VALUE";
    }

    private void sendError(
            HttpExchange exchange,
            int status,
            String code,
            String message,
            String requestId
    ) {
        send(
                exchange,
                status,
                Map.of(
                        "error", Map.of("code", code, "message", message),
                        "requestId", requestId
                ),
                requestId
        );
    }

    private void send(HttpExchange exchange, int status, Object payload, String requestId) {
        byte[] encoded;
        try {
            encoded = json.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            errors.report("Website API response " + requestId + " could not be encoded", exception);
            encoded = "{\"error\":{\"code\":\"RESPONSE_ENCODING_FAILED\",\"message\":\"The response could not be encoded\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            status = 500;
        }
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.set("content-type", "application/json; charset=utf-8");
            headers.set("cache-control", "no-store");
            headers.set("x-content-type-options", "nosniff");
            headers.set("referrer-policy", "no-referrer");
            headers.set("x-request-id", requestId);
            exchange.sendResponseHeaders(status, encoded.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(encoded);
            }
        } catch (IOException exception) {
            errors.report("Website API response " + requestId + " could not be sent", exception);
        }
    }

    private static WebsiteApiException badRequest(String code, String message) {
        return new WebsiteApiException(400, code, message);
    }

    @Override
    public synchronized void close() {
        HttpServer currentServer = server;
        ExecutorService currentExecutor = executor;
        server = null;
        executor = null;
        if (currentServer != null) {
            currentServer.stop(1);
        }
        if (currentExecutor != null) {
            currentExecutor.shutdown();
            try {
                if (!currentExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    currentExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                currentExecutor.shutdownNow();
            }
        }
    }

    @FunctionalInterface
    interface ErrorReporter {
        void report(String message, Throwable failure);
    }
}
