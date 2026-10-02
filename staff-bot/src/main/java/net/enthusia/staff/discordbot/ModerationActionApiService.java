package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.sanction.SanctionLength;

/** Website confirmations reuse the durable D07 service and its current-authority checks. */
final class ModerationActionApiService {
    private static final int MAX_DRAFTS = 1000;
    private static final String CAPABILITIES = "capabilities";
    private static final String PREPARE = "prepare";
    private static final String CONFIRM = "confirm";
    private static final String STATUS = "status";
    private final StaffModerationRuntime moderation;
    private final ModerationReadRequestAuthorizer authorizer;
    private final Map<UUID, Binding> drafts = new ConcurrentHashMap<>();
    private final Object draftLock = new Object();

    record Request(String actorId, String guildId, String targetKey, String sessionBinding,
            Optional<IntentInput> intent, Optional<UUID> confirmationId,
            Optional<String> minecraftTarget, Optional<MinecraftIntent> minecraftIntent, Optional<String> scope) {
        Request {
            intent = intent == null ? Optional.empty() : intent;
            confirmationId = confirmationId == null ? Optional.empty() : confirmationId;
            minecraftTarget = minecraftTarget == null ? Optional.empty() : minecraftTarget;
            minecraftIntent = minecraftIntent == null ? Optional.empty() : minecraftIntent;
            scope = scope == null ? Optional.empty() : scope;
            scope.ifPresent(value -> {
                if (!"BOTH".equals(value)) throw new IllegalArgumentException("unsupported action scope");
            });
            minecraftTarget.ifPresent(target -> {
                if (!target.matches("(?:[A-Za-z0-9_]{1,16}|[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})")) {
                    throw new IllegalArgumentException("invalid Minecraft player name or UUID");
                }
            });
            if (sessionBinding == null || !sessionBinding.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("session binding is invalid");
            }
        }

        Request(String actorId, String guildId, String targetKey, String sessionBinding,
                Optional<IntentInput> intent, Optional<UUID> confirmationId) {
            this(actorId, guildId, targetKey, sessionBinding, intent, confirmationId,
                    Optional.empty(), Optional.empty(), Optional.empty());
        }

        Request(String actorId, String guildId, String targetKey, String sessionBinding,
                Optional<IntentInput> intent, Optional<UUID> confirmationId,
                Optional<String> minecraftTarget, Optional<MinecraftIntent> minecraftIntent) {
            this(actorId, guildId, targetKey, sessionBinding, intent, confirmationId,
                    minecraftTarget, minecraftIntent, Optional.empty());
        }
    }

    record MinecraftIntent(String reasonId, String explanation) { }

    record IntentInput(String type, String duration, String reason, String explanation,
            Optional<DiscordRestrictionTarget> restriction) {
        IntentInput {
            restriction = restriction == null ? Optional.empty() : restriction;
        }

        DiscordPunishmentIntent toIntent() {
            DiscordConsequenceType consequence = consequenceType();
            ParsedLength parsed = parsedLength(consequence);
            return new DiscordPunishmentIntent(
                    consequence, parsed.length(), parsed.custom(), false,
                    restriction, reason, explanation, 0, true);
        }

        private DiscordConsequenceType consequenceType() {
            if (type == null) {
                throw new IllegalArgumentException("consequence type is required");
            }
            return DiscordConsequenceType.valueOf(type);
        }

        private ParsedLength parsedLength(DiscordConsequenceType consequence) {
            if (instant(consequence)) {
                requireInstantDuration();
                return new ParsedLength(SanctionLength.instant(), false);
            }
            DiscordDurationParser.Parsed parsed = new DiscordDurationParser().parse(duration, true);
            return new ParsedLength(parsed.length(), parsed.custom());
        }

        private void requireInstantDuration() {
            if (duration != null && !"instant".equals(duration)) {
                throw new IllegalArgumentException("instant consequence cannot carry a duration");
            }
        }

        private static boolean instant(DiscordConsequenceType consequence) {
            return consequence == DiscordConsequenceType.WARNING || consequence == DiscordConsequenceType.KICK;
        }

        private record ParsedLength(SanctionLength length, boolean custom) {
        }
    }

    record Prepared(UUID confirmationId, String targetUserId, DiscordPunishmentIntent intent, Instant expiresAt) {
    }

    record Status(UUID punishmentId, String state, boolean externalApplied, String dmOutcome) {
    }

    private record Binding(String actorId, String guildId, String targetKey, String sessionBinding, Instant expiresAt) {
        boolean matches(Request request) {
            return actorId.equals(request.actorId()) && guildId.equals(request.guildId())
                    && targetKey.equals(request.targetKey()) && sessionBinding.equals(request.sessionBinding());
        }
    }

    ModerationActionApiService(StaffModerationRuntime moderation, ModerationReadRequestAuthorizer authorizer) {
        this.moderation = moderation;
        this.authorizer = authorizer;
    }

    Object execute(String operation, Request request) {
        synchronized (draftLock) {
            return executeGuarded(operation, request);
        }
    }

    private Object executeGuarded(String operation, Request request) {
        ModerationReadContext context = authorizer.authorize(new ModerationReadApiModel.ReadRequest(
                request.actorId(), request.guildId(), request.targetKey(), Optional.empty()));
        if (CAPABILITIES.equals(operation)) {
            return capabilities(request, context);
        }
        if (bothOperation(request)) {
            return crossPlatformRequest(operation, request, context);
        }
        if (minecraftOperation(request)) {
            return minecraftRequest(operation, request, context);
        }
        return discordRequest(operation, request, context);
    }

    private Object capabilities(Request request, ModerationReadContext context) {
        com.fasterxml.jackson.databind.JsonNode minecraft;
        try {
            minecraft = minecraftRequest(CAPABILITIES, request, context);
        } catch (StaffAuthorityClient.UnavailableException | SecurityException exception) {
            minecraft = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                    .put("enabled", false).set("reasons",
                            new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode());
        }
        boolean discordEnabled = moderation.punishmentService().isPresent();
        boolean minecraftEnabled = minecraft.path("enabled").asBoolean(false);
        return Map.of("discordEnabled", discordEnabled,
                "minecraftEnabled", minecraftEnabled,
                "bothEnabled", discordEnabled && minecraftEnabled && moderation.crossPlatformActions().isPresent(),
                "minecraftReasons", minecraft.path("reasons"), "messageDeletionEnabled", false);
    }

    private static boolean bothOperation(Request request) {
        return request.scope().filter("BOTH"::equals).isPresent();
    }

    private static boolean minecraftOperation(Request request) {
        return request.minecraftTarget().isPresent() || request.minecraftIntent().isPresent();
    }

    private Object crossPlatformRequest(
            String operation,
            Request request,
            ModerationReadContext context
    ) {
        long discordTarget = context.readTarget().userId()
                .orElseThrow(() -> new IllegalArgumentException("select a Discord target"));
        CrossPlatformActionService service = moderation.crossPlatformActions()
                .orElseThrow(() -> new IllegalStateException("Both-platform enforcement is disabled"));
        UUID minecraftTarget = minecraftTarget(operation, request);
        if (PREPARE.equals(operation)) {
            return prepareCrossPlatform(service, request, context, discordTarget, minecraftTarget);
        }
        validateCrossPlatformConfirmation(operation, request);
        return CONFIRM.equals(operation)
                ? service.confirm(request, context, discordTarget, minecraftTarget)
                : service.status(request, context, discordTarget, minecraftTarget);
    }

    private static Object prepareCrossPlatform(
            CrossPlatformActionService service,
            Request request,
            ModerationReadContext context,
            long discordTarget,
            UUID minecraftTarget
    ) {
        if (request.confirmationId().isPresent()
                || request.intent().isEmpty()
                || request.minecraftIntent().isEmpty()) {
            throw new IllegalArgumentException("Both preparation requires Discord and Minecraft intent");
        }
        DiscordPunishmentIntent intent = request.intent().orElseThrow().toIntent();
        validateRestriction(context, intent);
        MinecraftIntent minecraft = request.minecraftIntent().orElseThrow();
        return service.prepare(
                request, context, discordTarget, minecraftTarget,
                minecraft.reasonId(), minecraft.explanation(), intent
        );
    }

    private static void validateCrossPlatformConfirmation(String operation, Request request) {
        if (!CONFIRM.equals(operation) && !STATUS.equals(operation)) {
            throw new IllegalArgumentException("invalid Both operation");
        }
        if (request.confirmationId().isEmpty()
                || request.intent().isPresent()
                || request.minecraftIntent().isPresent()) {
            throw new IllegalArgumentException("invalid Both confirmation");
        }
    }

    private Object discordRequest(String operation, Request request, ModerationReadContext context) {
        long target = context.readTarget().userId()
                .orElseThrow(() -> new IllegalArgumentException("select a target"));
        DiscordPunishmentService service = moderation.punishmentService()
                .orElseThrow(() -> new IllegalStateException("Discord enforcement is disabled"));
        return switch (operation) {
            case PREPARE -> prepare(request, context, service, target);
            case CONFIRM -> confirm(request, context, service, target);
            case STATUS -> status(request, context, service, target);
            default -> throw new IllegalArgumentException("unknown moderation action operation");
        };
    }

    private com.fasterxml.jackson.databind.JsonNode minecraftRequest(
            String operation, Request request, ModerationReadContext context) {
        rejectMixedIntent(request);
        var actor = moderation.actors().invoker(
                new net.enthusia.staff.domain.moderation.DiscordUserId(request.actorId()),
                context.actorMember().getEffectiveName());
        UUID target = minecraftTarget(operation, request);
        validateMinecraftOperation(operation, request);
        return moderation.authority().punishment(operation, minecraftInput(request, actor.id(), target));
    }

    private static void rejectMixedIntent(Request request) {
        if (request.intent().isPresent()) {
            throw new IllegalArgumentException("cannot mix Discord and Minecraft intent");
        }
    }

    private UUID minecraftTarget(String operation, Request request) {
        if (CAPABILITIES.equals(operation)) {
            return null;
        }
        var resolved = moderation.reads().resolveMinecraft(request.minecraftTarget().orElseThrow());
        if (!(resolved instanceof StaffModerationReadService.MinecraftResolution.Resolved found)) {
            throw new IllegalArgumentException("Minecraft player is unknown or ambiguous; use its exact UUID");
        }
        return found.target().minecraftId().orElseThrow();
    }

    private static void validateMinecraftOperation(String operation, Request request) {
        if (CAPABILITIES.equals(operation)) {
            validateCapabilitiesRequest(request);
            return;
        }
        if (PREPARE.equals(operation)) {
            validateMinecraftPrepare(request);
            return;
        }
        validateMinecraftConfirmation(operation, request);
    }

    private static void validateCapabilitiesRequest(Request request) {
        if (request.minecraftTarget().isPresent() || request.minecraftIntent().isPresent()
                || request.confirmationId().isPresent()) {
            throw new IllegalArgumentException("capabilities cannot carry intent");
        }
    }

    private static void validateMinecraftPrepare(Request request) {
        if (request.confirmationId().isPresent() || request.minecraftIntent().isEmpty()) {
            throw new IllegalArgumentException("configured Minecraft intent is required");
        }
    }

    private static void validateMinecraftConfirmation(String operation, Request request) {
        if (request.minecraftIntent().isPresent()) {
            throw new IllegalArgumentException("confirmation cannot change Minecraft intent");
        }
        if ((CONFIRM.equals(operation) || STATUS.equals(operation)) && request.confirmationId().isEmpty()) {
            throw new IllegalArgumentException("Minecraft confirmation is required");
        }
    }

    private static java.util.Map<String, Object> minecraftInput(Request request, UUID actorId, UUID target) {
        var input = new java.util.LinkedHashMap<String, Object>();
        input.put("actorId", actorId);
        input.put("targetId", target);
        input.put("sessionBinding", minecraftSessionBinding(request));
        input.put("confirmationId", request.confirmationId().orElse(null));
        input.put("reasonId", request.minecraftIntent().map(MinecraftIntent::reasonId).orElse(null));
        input.put("explanation", request.minecraftIntent().map(MinecraftIntent::explanation).orElse(null));
        return input;
    }

    private static String minecraftSessionBinding(Request request) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    (request.sessionBinding() + ":" + request.guildId() + ":" + request.targetKey())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Prepared prepare(Request request, ModerationReadContext context, DiscordPunishmentService service, long target) {
        drafts.entrySet().removeIf(entry -> !Instant.now().isBefore(entry.getValue().expiresAt()));
        if (request.confirmationId().isPresent() || drafts.size() >= MAX_DRAFTS) {
            throw new IllegalArgumentException("cannot prepare this request");
        }
        DiscordPunishmentIntent intent = request.intent().orElseThrow().toIntent();
        validateRestriction(context, intent);
        DiscordPunishmentService.Confirmation prepared = service.prepareIssue(
                context.actorId(), context.actorMember().getEffectiveName(), target, intent);
        Instant expiresAt = Instant.now().plusSeconds(120);
        drafts.put(prepared.token(), new Binding(request.actorId(), request.guildId(), request.targetKey(),
                request.sessionBinding(), expiresAt));
        return new Prepared(prepared.token(), prepared.targetUserId(), intent, expiresAt);
    }

    private static void validateRestriction(ModerationReadContext context, DiscordPunishmentIntent intent) {
        intent.restriction().ifPresent(restriction -> {
            var visible = new ModerationDiscordMessageReader().visibleChannels(context);
            boolean allowed = visible.stream().anyMatch(channel ->
                    restriction.kind() == DiscordRestrictionTarget.Kind.CHANNEL
                            ? channel.id().equals(restriction.snowflake())
                            : channel.categoryId().filter(restriction.snowflake()::equals).isPresent());
            if (!allowed) throw new IllegalArgumentException("restriction scope is not visible to the actor");
        });
    }

    private Status confirm(Request request, ModerationReadContext context, DiscordPunishmentService service, long target) {
        if (request.intent().isPresent()) throw new IllegalArgumentException("confirmation cannot alter intent");
        UUID id = request.confirmationId().orElseThrow();
        Binding binding = drafts.get(id);
        if (binding == null || !binding.matches(request) || !Instant.now().isBefore(binding.expiresAt())) {
            throw new IllegalArgumentException("confirmation expired or belongs to a different session");
        }
        service.confirmWebIssue(context.actorId(), context.actorMember().getEffectiveName(), target, id);
        return status(request, context, service, target);
    }

    private Status status(Request request, ModerationReadContext context, DiscordPunishmentService service, long target) {
        if (request.intent().isPresent()) throw new IllegalArgumentException("status cannot alter intent");
        var stored = service.webPunishment(context.actorId(), context.actorMember().getEffectiveName(), target,
                request.confirmationId().orElseThrow()).orElseThrow(() -> new IllegalArgumentException("punishment not found"));
        var punishment = stored.punishment();
        return new Status(punishment.punishmentId(), punishment.state().name(), punishment.externalApplied(),
                punishment.dmOutcome().name());
    }
}
