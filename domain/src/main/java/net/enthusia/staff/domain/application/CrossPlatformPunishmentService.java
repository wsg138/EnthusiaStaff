package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.auth.DiscordAuthorizationDecision;
import net.enthusia.staff.domain.auth.DiscordAuthorizationRequest;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordConsequenceIntent;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.auth.DiscordModerationAuthorizationService;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CrossPlatformPunishmentStore;
import net.enthusia.staff.domain.ports.CrossPlatformIdentityLookup;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

/** Final D08 orchestration for an explicitly selected Both-platform punishment. */
public final class CrossPlatformPunishmentService {
    private final MinecraftPunishmentPreparer minecraft;
    private final CrossPlatformPunishmentStore store;
    private final CrossPlatformIdentityLookup identities;
    private final DiscordModerationAuthorizationService authorization;

    public CrossPlatformPunishmentService(
            MinecraftPunishmentPreparer minecraft,
            CrossPlatformPunishmentStore store,
            CrossPlatformIdentityLookup identities,
            DiscordModerationAuthorizationService authorization
    ) {
        if (minecraft == null || store == null || identities == null || authorization == null) {
            throw new IllegalArgumentException("cross-platform punishment dependencies must be present");
        }
        this.minecraft = minecraft;
        this.store = store;
        this.identities = identities;
        this.authorization = authorization;
    }

    public CrossPlatformPunishmentOutcome createBoth(CrossPlatformPunishmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must be present");
        }
        CrossPlatformPunishmentOutcome identityIssue = identityIssue(request);
        if (identityIssue != null) {
            return identityIssue;
        }
        PunishmentPreparation preparation = minecraft.prepareConfirmed(
                request.minecraftRequest(), request.caseId()
        );
        if (preparation instanceof PunishmentPreparation.Rejected rejected) {
            return new CrossPlatformPunishmentOutcome.Rejected(rejected.code(), rejected.message());
        }
        PunishmentPlan minecraftPlan = ((PunishmentPreparation.Prepared) preparation).plan();
        if (!request.minecraftExpectation().matches(minecraftPlan)) {
            return invalidConsequence();
        }
        DiscordAuthorizationDecision decision = authorize(request, minecraftPlan.actor());
        if (!decision.permitted()) {
            return new CrossPlatformPunishmentOutcome.Rejected(
                    "AUTHORIZATION_" + decision.denial().name(),
                    "The current staff authority does not permit this cross-platform punishment"
            );
        }
        DiscordPunishment discord = discordPunishment(request, minecraftPlan.actor(), minecraftPlan.issuedAt());
        CrossPlatformPunishmentResult result = store.create(
                new CrossPlatformPunishmentPlan(minecraftPlan, discord, request.operationKey())
        );
        return new CrossPlatformPunishmentOutcome.Accepted(result, decision.requiredPreconditions());
    }

    private CrossPlatformPunishmentOutcome identityIssue(CrossPlatformPunishmentRequest request) {
        Optional<ModerationSubjectId> minecraftSubject =
                identities.subjectForMinecraft(request.minecraftRequest().targetId());
        Optional<ModerationSubjectId> discordSubject =
                identities.subjectForDiscord(request.discordUserId());
        boolean same = minecraftSubject.filter(request.subjectId()::equals).isPresent()
                && discordSubject.filter(request.subjectId()::equals).isPresent();
        return same ? null : new CrossPlatformPunishmentOutcome.Rejected(
                "IDENTITY_MISMATCH",
                "The selected Minecraft and Discord identities are no longer linked"
        );
    }

    private DiscordAuthorizationDecision authorize(
            CrossPlatformPunishmentRequest request,
            Actor currentActor
    ) {
        DiscordAuthorizationRequest authorizationRequest = new DiscordAuthorizationRequest(
                DiscordModerationOperation.ISSUE_SANCTION,
                Set.of(ModerationPlatform.MINECRAFT, ModerationPlatform.DISCORD),
                List.of(minecraftAuthorizationIntent(request.minecraftExpectation()),
                        request.discordIntent().authorizationIntent())
        );
        return authorization.authorize(currentActor, request.targetStaff(), authorizationRequest);
    }

    private static DiscordPunishment discordPunishment(
            CrossPlatformPunishmentRequest request,
            Actor currentActor,
            Instant issuedAt
    ) {
        return DiscordPunishment.pending(
                request.discordPunishmentId(), request.subjectId(), Optional.of(request.caseId()),
                request.discordUserId(), request.guildId(), currentActor,
                request.discordIntent(), issuedAt, request.operationKey()
        );
    }

    private static DiscordConsequenceIntent minecraftAuthorizationIntent(PunishmentExpectation expectation) {
        SanctionSpec supported = expectation.sanctions().stream()
                .filter(CrossPlatformPunishmentService::authorizationRepresentable)
                .findFirst()
                .orElse(new SanctionSpec(SanctionType.WARNING, SanctionLength.instant()));
        DiscordConsequenceType type = switch (supported.type()) {
            case WARNING -> DiscordConsequenceType.WARNING;
            case KICK -> DiscordConsequenceType.KICK;
            case MUTE -> DiscordConsequenceType.MUTE;
            case BAN, NETWORK_BAN, NETWORK_IDENTITY_BAN -> DiscordConsequenceType.BAN;
            default -> throw new IllegalStateException("authorization representative must be Discord-compatible");
        };
        return new DiscordConsequenceIntent(
                ModerationPlatform.MINECRAFT, type, supported.length(), false, false
        );
    }

    private static boolean authorizationRepresentable(SanctionSpec sanction) {
        return sanction.type() == SanctionType.WARNING
                || sanction.type() == SanctionType.KICK
                || sanction.type() == SanctionType.MUTE
                || sanction.type().isBan();
    }

    private static CrossPlatformPunishmentOutcome.Rejected invalidConsequence() {
        return new CrossPlatformPunishmentOutcome.Rejected(
                "MINECRAFT_CONSEQUENCE_MISMATCH",
                "The confirmed Minecraft consequence no longer matches the current policy result"
        );
    }
}
