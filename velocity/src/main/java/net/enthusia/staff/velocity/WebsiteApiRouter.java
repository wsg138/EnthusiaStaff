package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;

final class WebsiteApiRouter {
    private static final String ACCOUNT_ID = "accountId";
    private static final String CASE_ID = "caseId";
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String PUNISHMENT_ID = "punishmentId";
    private static final String PUBLIC_LIST_PATH = "/v1/public/punishments";
    private static final String PUBLIC_SEARCH_PATH = "/v1/public/search";
    private static final String PUBLIC_CASE_PREFIX = "/v1/public/cases/";
    private static final String CLAIM_PATH = "/v1/website/punishment-codes/claim";
    private static final String REVALIDATE_PATH = "/v1/website/punishment-codes/revalidate";
    private static final String ACCEPT_APPEAL_PATH = "/v1/website/appeals/accept";
    private static final Set<String> NO_QUERY = Set.of();
    private static final Set<String> CLAIM_FIELDS =
            Set.of(ACCOUNT_ID, "username", "punishmentCode");
    private static final Set<String> REVALIDATE_FIELDS =
            Set.of(ACCOUNT_ID, PUNISHMENT_ID, "codeGeneration");
    private static final Set<String> ACCEPT_FIELDS = Set.of(
            "appealId",
            PUNISHMENT_ID,
            CASE_ID,
            "playerAccountId",
            "actorAccountId",
            "actorRank",
            "reason"
    );

    private final WebsiteModerationStore store;
    private final Clock clock;
    private final WebsiteApiRequestDecoder decoder;
    private final WebsiteAppealEndpoint appeals;

    WebsiteApiRouter(
            WebsiteModerationStore store,
            AuthorizationPolicy authorization,
            SanctionChangeService sanctionChanges,
            Supplier<OperationalMode> authorityMode,
            Clock clock
    ) {
        if (store == null || clock == null) {
            throw new IllegalArgumentException("Website API router dependencies are required");
        }
        this.store = store;
        this.clock = clock;
        this.decoder = new WebsiteApiRequestDecoder();
        this.appeals = new WebsiteAppealEndpoint(
                store,
                authorization,
                sanctionChanges,
                authorityMode,
                clock,
                decoder
        );
    }

    Object route(String method, URI uri, Headers headers, byte[] body) {
        Map<String, String> query = decoder.query(uri.getRawQuery());
        if (GET.equals(method)) {
            return routeGet(uri.getRawPath(), query, body);
        }
        if (POST.equals(method)) {
            return routePost(uri.getRawPath(), query, headers, body);
        }
        throw notFound();
    }

    private Object routeGet(String path, Map<String, String> query, byte[] body) {
        if (PUBLIC_LIST_PATH.equals(path)) {
            return listPublic(query, body);
        }
        if (PUBLIC_SEARCH_PATH.equals(path)) {
            return searchPublic(query, body);
        }
        if (isPublicCasePath(path)) {
            return publicCase(path, query, body);
        }
        throw notFound();
    }

    private Object routePost(
            String path,
            Map<String, String> query,
            Headers headers,
            byte[] body
    ) {
        if (CLAIM_PATH.equals(path)) {
            decoder.requireQueryKeys(query, NO_QUERY);
            return claim(headers, body);
        }
        if (REVALIDATE_PATH.equals(path)) {
            decoder.requireQueryKeys(query, NO_QUERY);
            return revalidate(headers, body);
        }
        if (ACCEPT_APPEAL_PATH.equals(path)) {
            decoder.requireQueryKeys(query, NO_QUERY);
            ObjectNode input = decoder.jsonBody(headers, body, ACCEPT_FIELDS);
            return appeals.accept(headers, input);
        }
        throw notFound();
    }

    private Object listPublic(Map<String, String> query, byte[] body) {
        decoder.requireEmptyBody(body);
        decoder.requireQueryKeys(query, Set.of("type", "cursor", "limit"));
        PublicPunishmentFilter filter = filter(query.get("type"));
        int limit = decoder.positiveInteger(query.get("limit"), 30, 1, 100);
        String cursor = query.get("cursor");
        PublicPunishmentPage page = store.listPublic(
                filter,
                cursor == null || cursor.isBlank() ? Optional.empty() : Optional.of(cursor),
                limit,
                clock.instant()
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "items",
                page.items().stream().map(WebsiteApiResponses::publicPunishment).toList()
        );
        response.put("nextCursor", page.nextCursor().orElse(null));
        return response;
    }

    private Object searchPublic(Map<String, String> query, byte[] body) {
        decoder.requireEmptyBody(body);
        decoder.requireQueryKeys(query, Set.of("q"));
        String search = query.get("q");
        if (search == null) {
            throw badRequest("INVALID_SEARCH", "Search for a username or case ID");
        }
        return Map.of(
                "items",
                store.searchPublic(search, 100, clock.instant())
                        .stream()
                        .map(WebsiteApiResponses::publicPunishment)
                        .toList()
        );
    }

    private Object publicCase(String path, Map<String, String> query, byte[] body) {
        decoder.requireEmptyBody(body);
        decoder.requireQueryKeys(query, NO_QUERY);
        CaseId caseId;
        try {
            caseId = new CaseId(URLDecoder.decode(
                    path.substring(PUBLIC_CASE_PREFIX.length()),
                    StandardCharsets.UTF_8
            ));
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_CASE_ID", "The case ID is invalid");
        }
        PublicPunishment punishment = store.publicCase(caseId, clock.instant())
                .orElseThrow(() -> new WebsiteApiException(
                        404,
                        "PUNISHMENT_NOT_FOUND",
                        "The punishment could not be found"
                ));
        return WebsiteApiResponses.publicPunishment(punishment);
    }

    private Object claim(Headers headers, byte[] body) {
        ObjectNode input = decoder.jsonBody(headers, body, CLAIM_FIELDS);
        PunishmentCodeBinding binding = store.claimCode(
                decoder.text(input, "punishmentCode", 64),
                decoder.uuidText(input, ACCOUNT_ID),
                decoder.minecraftUsername(input, "username"),
                clock.instant()
        );
        return WebsiteApiResponses.binding(binding);
    }

    private Object revalidate(Headers headers, byte[] body) {
        ObjectNode input = decoder.jsonBody(headers, body, REVALIDATE_FIELDS);
        PunishmentCodeBinding binding = store.revalidateCode(
                decoder.uuid(input, PUNISHMENT_ID),
                decoder.integer(input, "codeGeneration", 1, Integer.MAX_VALUE),
                decoder.uuidText(input, ACCOUNT_ID),
                clock.instant()
        );
        return WebsiteApiResponses.binding(binding);
    }

    private static PublicPunishmentFilter filter(String raw) {
        try {
            return PublicPunishmentFilter.valueOf(
                    raw == null ? "ALL" : raw.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_FILTER", "The punishment filter is invalid");
        }
    }

    private static boolean isPublicCasePath(String path) {
        return path.startsWith(PUBLIC_CASE_PREFIX)
                && path.length() > PUBLIC_CASE_PREFIX.length()
                && path.indexOf('/', PUBLIC_CASE_PREFIX.length()) < 0;
    }

    private static WebsiteApiException badRequest(String code, String message) {
        return new WebsiteApiException(400, code, message);
    }

    private static WebsiteApiException notFound() {
        return new WebsiteApiException(
                404,
                "NOT_FOUND",
                "The requested API resource was not found"
        );
    }
}
