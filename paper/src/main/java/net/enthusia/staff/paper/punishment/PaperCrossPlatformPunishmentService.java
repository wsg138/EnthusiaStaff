package net.enthusia.staff.paper.punishment;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentOutcome;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentRequest;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentService;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentPreparation;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDecision;
import net.enthusia.staff.domain.auth.DiscordAuthorizationRequest;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.moderation.DiscordGuildId;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CrossPlatformIdentityLookup;
import net.enthusia.staff.domain.ports.CrossPlatformPunishmentStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.JdbcCrossPlatformPunishmentStatusReader;

/**
 * Minecraft-origin D08 coordinator. It persists Discord intent only; StaffBot remains the
 * exclusive Discord side-effect runtime.
 */
public final class PaperCrossPlatformPunishmentService {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int DISCORD_EXPLANATION_LIMIT = 2_000;

    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final PunishmentService minecraft;
    private final ReasonPolicyRepository policies;
    private final DiscordModerationPersistenceStore identities;
    private final CrossPlatformPunishmentStore crossPlatformStore;
    private final CrossPlatformIdentityLookup crossPlatformIdentities;
    private final DiscordPunishmentRepository discord;
    private final JdbcCrossPlatformPunishmentStatusReader minecraftStatus;
    private final DiscordModerationAuthorizationService authorization;
    private final DiscordGuildId guildId;
    private final Function<UUID, Optional<Actor>> targetStaff;

    public PaperCrossPlatformPunishmentService(
            Clock clock,
            Supplier<OperationalMode> mode,
            PunishmentService minecraft,
            ReasonPolicyRepository policies,
            DiscordModerationPersistenceStore identities,
            CrossPlatformPunishmentStore crossPlatformStore,
            CrossPlatformIdentityLookup crossPlatformIdentities,
            DiscordPunishmentRepository discord,
            JdbcCrossPlatformPunishmentStatusReader minecraftStatus,
            PaperCrossPlatformConfiguration configuration,
            net.enthusia.staff.domain.auth.AuthorizationPolicy minecraftAuthorization,
            Function<UUID, Optional<Actor>> targetStaff
    ) {
        if (clock == null || mode == null || minecraft == null || policies == null || identities == null
                || crossPlatformStore == null || crossPlatformIdentities == null || discord == null
                || minecraftStatus == null || configuration == null || minecraftAuthorization == null
                || targetStaff == null) {
            throw new IllegalArgumentException("Paper cross-platform dependencies must be present");
        }
        this.clock = clock;
        this.mode = mode;
        this.minecraft = minecraft;
        this.policies = policies;
        this.identities = identities;
        this.crossPlatformStore = crossPlatformStore;
        this.crossPlatformIdentities = crossPlatformIdentities;
        this.discord = discord;
        this.minecraftStatus = minecraftStatus;
        this.authorization = new DiscordModerationAuthorizationService(
                minecraftAuthorization, configuration.authorizationLimits());
        this.guildId = configuration.guildId();
        this.targetStaff = targetStaff;
    }

    Optional<TargetLink> linkedTarget(UUID minecraftTargetId) {
        if (minecraftTargetId == null) {
            throw new IllegalArgumentException("Minecraft target must be present");
        }
        return identities.subjectForMinecraft(minecraftTargetId).flatMap(versioned -> {
            Set<DiscordUserId> discordIds = versioned.subject().discordUserIds();
            if (discordIds.size() != 1) {
                return Optional.empty();
            }
            return Optional.of(new TargetLink(versioned.subject().subjectId(), discordIds.iterator().next()));
        });
    }

    Optional<DiscordPunishmentIntent> previewIntent(PunishmentDraft draft) {
        if (draft == null) {
            return Optional.empty();
        }
        return policies.find(draft.reasonId()).flatMap(policy -> intent(
                draft.expectation().sanctions(),
                draft.expectation().customDuration(),
                policy.publicReason(),
                draft.internalExplanation()
        ));
    }

    Outcome confirm(PaperPunishmentScope scope, Actor actor, PunishmentDraft draft) {
        if (scope == null || scope == PaperPunishmentScope.MINECRAFT || actor == null || draft == null) {
            throw new IllegalArgumentException("Discord/Both confirmation fields must be present");
        }
        if (!actor.id().equals(draft.actorId()) || draft.expiredAt(clock.instant())) {
            return new Outcome.Rejected("STALE_DRAFT", "The punishment draft is expired or belongs to another actor");
        }
        TargetLink link = linkedTarget(draft.targetId()).orElse(null);
        if (link == null) {
            return new Outcome.Rejected(
                    "DISCORD_IDENTITY_UNAVAILABLE",
                    "This Minecraft player does not have exactly one current linked Discord identity"
            );
        }
        UUID punishmentId = punishmentId(draft.draftId(), scope);
        Optional<DiscordPunishmentRepository.StoredPunishment> replay = discord.find(punishmentId);
        if (replay.isPresent()) {
            return recovered(scope, replay.orElseThrow().punishment());
        }
        return scope == PaperPunishmentScope.BOTH
                ? confirmBoth(actor, draft, link, punishmentId)
                : confirmDiscord(actor, draft, link, punishmentId);
    }

    Outcome status(PaperPunishmentScope scope, UUID punishmentId) {
        if (scope == null || punishmentId == null || scope == PaperPunishmentScope.MINECRAFT) {
            throw new IllegalArgumentException("Discord/Both status fields must be present");
        }
        return discord.find(punishmentId)
                .<Outcome>map(stored -> recovered(scope, stored.punishment()))
                .orElseGet(() -> new Outcome.Rejected("NOT_FOUND", "No durable Discord punishment exists"));
    }

    private Outcome confirmDiscord(
            Actor actor,
            PunishmentDraft draft,
            TargetLink link,
            UUID punishmentId
    ) {
        CaseId evaluationCase = caseId(draft.draftId());
        CreatePunishmentRequest minecraftRequest = request(draft, actor, "d08:paper-discord:" + draft.draftId());
        PunishmentPreparation preparation = minecraft.prepareConfirmed(
                minecraftRequest, mode.get(), evaluationCase, clock.instant());
        if (preparation instanceof PunishmentPreparation.Rejected rejected) {
            return new Outcome.Rejected(rejected.code(), rejected.message());
        }
        PunishmentPlan current = ((PunishmentPreparation.Prepared) preparation).plan();
        if (!draft.expectation().matches(current)) {
            return recommendationChanged();
        }
        DiscordPunishmentIntent intent = intent(
                current.sanctions(),
                draft.expectation().customDuration(),
                current.publicReason(),
                draft.internalExplanation()
        ).orElse(null);
        if (intent == null) {
            return unsupportedDiscordConsequence();
        }
        Optional<Actor> currentTargetStaff = currentTargetStaff(draft.targetId());
        DiscordAuthorizationDecision decision = authorization.authorize(
                actor,
                currentTargetStaff,
                new DiscordAuthorizationRequest(
                        DiscordModerationOperation.ISSUE_SANCTION,
                        Set.of(ModerationPlatform.DISCORD),
                        List.of(intent.authorizationIntent())
                )
        );
        if (!decision.permitted()) {
            return denied(decision);
        }
        String operationKey = "d08:paper-discord:" + draft.draftId();
        DiscordPunishment punishment = DiscordPunishment.pending(
                punishmentId,
                link.subjectId(),
                link.discordUserId(),
                guildId,
                actor,
                intent,
                current.issuedAt(),
                operationKey
        );
        try {
            DiscordPunishmentRepository.StoredPunishment stored =
                    discord.create(punishment, operationKey, current.issuedAt());
            return recovered(PaperPunishmentScope.DISCORD, stored.punishment());
        } catch (RuntimeException failure) {
            return discord.find(punishmentId)
                    .<Outcome>map(stored -> recovered(PaperPunishmentScope.DISCORD, stored.punishment()))
                    .orElseThrow(() -> failure);
        }
    }

    private Outcome confirmBoth(
            Actor actor,
            PunishmentDraft draft,
            TargetLink link,
            UUID punishmentId
    ) {
        DiscordPunishmentIntent intent = previewIntent(draft).orElse(null);
        if (intent == null) {
            return unsupportedDiscordConsequence();
        }
        Optional<Actor> currentTargetStaff = currentTargetStaff(draft.targetId());
        CaseId caseId = caseId(draft.draftId());
        String operationKey = "d08:paper-both:" + draft.draftId();
        CreatePunishmentRequest minecraftRequest = request(draft, actor, operationKey);
        CrossPlatformPunishmentService both = new CrossPlatformPunishmentService(
                (request, requestedCase) -> minecraft.prepareConfirmed(
                        request, mode.get(), requestedCase, clock.instant()),
                crossPlatformStore,
                crossPlatformIdentities,
                authorization
        );
        CrossPlatformPunishmentOutcome result;
        try {
            result = both.createBoth(new CrossPlatformPunishmentRequest(
                    caseId,
                    punishmentId,
                    operationKey,
                    link.subjectId(),
                    link.discordUserId(),
                    guildId,
                    minecraftRequest,
                    draft.expectation(),
                    intent,
                    currentTargetStaff
            ));
        } catch (RuntimeException failure) {
            Optional<DiscordPunishmentRepository.StoredPunishment> recovered = discord.find(punishmentId);
            if (recovered.isPresent()) {
                return recovered(PaperPunishmentScope.BOTH, recovered.orElseThrow().punishment());
            }
            throw failure;
        }
        if (result instanceof CrossPlatformPunishmentOutcome.Rejected rejected) {
            return new Outcome.Rejected(rejected.code(), rejected.message());
        }
        return discord.find(punishmentId)
                .<Outcome>map(stored -> recovered(PaperPunishmentScope.BOTH, stored.punishment()))
                .orElseGet(() -> new Outcome.Rejected(
                        "COMMIT_UNREADABLE",
                        "The cross-platform transaction committed but its Discord intent could not be read"
                ));
    }

    private Optional<Actor> currentTargetStaff(UUID targetId) {
        return targetStaff.apply(targetId);
    }

    private Outcome recovered(PaperPunishmentScope scope, DiscordPunishment punishment) {
        Optional<CaseId> caseId = punishment.caseId();
        String minecraftDelivery = caseId.flatMap(minecraftStatus::find)
                .map(value -> value.state().name())
                .orElse(scope == PaperPunishmentScope.BOTH ? "UNKNOWN" : "NOT_SELECTED");
        String minecraftError = caseId.flatMap(minecraftStatus::find)
                .map(JdbcCrossPlatformPunishmentStatusReader.Status::lastErrorCode)
                .orElse(null);
        return new Outcome.Accepted(new Status(
                scope,
                caseId,
                punishment.punishmentId(),
                minecraftDelivery,
                minecraftError,
                punishment.state().name(),
                punishment.externalApplied(),
                punishment.dmOutcome().name()
        ));
    }

    private static CreatePunishmentRequest request(
            PunishmentDraft draft,
            Actor actor,
            String operationKey
    ) {
        return new CreatePunishmentRequest(
                new IdempotencyKey(operationKey),
                draft.targetId(),
                actor,
                draft.reasonId(),
                draft.internalExplanation(),
                draft.visibility(),
                draft.expectation().customDuration()
                        ? draft.expectation().sanctions()
                        : List.of()
        );
    }

    private Optional<DiscordPunishmentIntent> intent(
            List<SanctionSpec> sanctions,
            boolean customDuration,
            String publicReason,
            String explanation
    ) {
        if (explanation == null || explanation.trim().length() > DISCORD_EXPLANATION_LIMIT) {
            return Optional.empty();
        }
        SanctionSpec representative = sanctions.stream()
                .filter(PaperCrossPlatformPunishmentService::discordSupported)
                .findFirst()
                .orElse(null);
        if (representative == null) {
            return Optional.empty();
        }
        DiscordConsequenceType type = switch (representative.type()) {
            case WARNING -> DiscordConsequenceType.WARNING;
            case KICK -> DiscordConsequenceType.KICK;
            case MUTE, PUBLIC_MUTE -> DiscordConsequenceType.MUTE;
            case BAN, NETWORK_BAN, NETWORK_IDENTITY_BAN -> DiscordConsequenceType.BAN;
            default -> throw new IllegalStateException("unsupported Discord representative");
        };
        return Optional.of(new DiscordPunishmentIntent(
                type,
                representative.length(),
                customDuration,
                false,
                Optional.empty(),
                publicReason,
                explanation,
                0,
                true
        ));
    }

    private static boolean discordSupported(SanctionSpec sanction) {
        SanctionType type = sanction.type();
        return type == SanctionType.WARNING
                || type == SanctionType.KICK
                || type == SanctionType.MUTE
                || type == SanctionType.PUBLIC_MUTE
                || type.isBan();
    }

    private static Outcome.Rejected denied(DiscordAuthorizationDecision decision) {
        return new Outcome.Rejected(
                "AUTHORIZATION_" + decision.denial().name(),
                "Current staff authority does not permit this Discord consequence"
        );
    }

    private static Outcome.Rejected recommendationChanged() {
        return new Outcome.Rejected(
                "RECOMMENDATION_CHANGED",
                "The punishment recommendation changed; review the updated ladder before confirming"
        );
    }

    private static Outcome.Rejected unsupportedDiscordConsequence() {
        return new Outcome.Rejected(
                "DISCORD_CONSEQUENCE_UNAVAILABLE",
                "This configured punishment step has no supported Discord consequence, or its explanation exceeds 2000 characters"
        );
    }

    static UUID punishmentId(UUID draftId, PaperPunishmentScope scope) {
        String seed = "enthusia:d08:paper:" + scope.name() + ':' + draftId;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    static CaseId caseId(UUID draftId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("enthusia:d08:paper:case:" + draftId).getBytes(StandardCharsets.UTF_8));
            BigInteger value = new BigInteger(1, java.util.Arrays.copyOf(digest, 10));
            char[] encoded = new char[16];
            for (int index = encoded.length - 1; index >= 0; index--) {
                encoded[index] = CROCKFORD[value.and(BigInteger.valueOf(31)).intValue()];
                value = value.shiftRight(5);
            }
            return new CaseId(new String(encoded));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record TargetLink(ModerationSubjectId subjectId, DiscordUserId discordUserId) {
        TargetLink {
            if (subjectId == null || discordUserId == null) {
                throw new IllegalArgumentException("target link fields must be present");
            }
        }
    }

    record Status(
            PaperPunishmentScope scope,
            Optional<CaseId> caseId,
            UUID discordPunishmentId,
            String minecraftDelivery,
            String minecraftError,
            String discordState,
            boolean discordExternalApplied,
            String discordDmOutcome
    ) {
        Status {
            if (scope == null || caseId == null || discordPunishmentId == null
                    || minecraftDelivery == null || discordState == null || discordDmOutcome == null) {
                throw new IllegalArgumentException("cross-platform status fields must be present");
            }
        }
    }

    sealed interface Outcome {
        record Accepted(Status status) implements Outcome {
            public Accepted {
                if (status == null) {
                    throw new IllegalArgumentException("accepted status must be present");
                }
            }
        }

        record Rejected(String code, String message) implements Outcome {
            public Rejected {
                if (code == null || code.isBlank() || message == null || message.isBlank()) {
                    throw new IllegalArgumentException("rejection fields must be present");
                }
            }
        }
    }
}
