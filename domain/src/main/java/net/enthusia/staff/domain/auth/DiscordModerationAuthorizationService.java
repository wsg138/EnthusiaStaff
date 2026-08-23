package net.enthusia.staff.domain.auth;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;

/**
 * Single authoritative domain policy for staff moderation initiated through Discord.
 *
 * <p>This service deliberately accepts neither Discord roles nor command origin. Roles may control
 * command visibility and may be required later as an external enforcement precondition, but they
 * can never grant domain permission. Cross-platform requests carry their final explicit platform
 * selection and are checked against the existing Minecraft authorization policy as well.</p>
 */
public final class DiscordModerationAuthorizationService {
    private final AuthorizationPolicy minecraftAuthorization;
    private final DiscordAuthorizationLimits limits;

    public DiscordModerationAuthorizationService(DiscordAuthorizationLimits limits) {
        this(new DefaultAuthorizationPolicy(), limits);
    }

    public DiscordModerationAuthorizationService(
            AuthorizationPolicy minecraftAuthorization,
            DiscordAuthorizationLimits limits
    ) {
        if (minecraftAuthorization == null || limits == null) {
            throw new IllegalArgumentException("authorization policy and limits must be present");
        }
        this.minecraftAuthorization = minecraftAuthorization;
        this.limits = limits;
    }

    public DiscordAuthorizationDecision authorize(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordAuthorizationRequest request
    ) {
        if (actor == null || targetStaff == null || request == null) {
            throw new IllegalArgumentException("actor, targetStaff and request must be present");
        }

        DiscordAuthorizationDecision targetDecision = authorizeTarget(actor, targetStaff, request.operation());
        if (!targetDecision.permitted()) {
            return targetDecision;
        }
        if (!permitsDiscordOperation(actor.rank(), request.operation())) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.UNAUTHORIZED_OPERATION);
        }

        DiscordAuthorizationDecision minecraftDecision = authorizeMinecraft(actor, request);
        if (!minecraftDecision.permitted()) {
            return minecraftDecision;
        }

        if (request.operation() == DiscordModerationOperation.ISSUE_SANCTION) {
            for (DiscordConsequenceIntent consequence : request.consequences()) {
                if (consequence.platform() != ModerationPlatform.DISCORD) {
                    continue;
                }
                DiscordAuthorizationDecision consequenceDecision = authorizeDiscordConsequence(actor.rank(), consequence);
                if (!consequenceDecision.permitted()) {
                    return consequenceDecision;
                }
            }
        }

        return DiscordAuthorizationDecision.allow(requiredPreconditions(request));
    }

    public Optional<DiscordAuthorizationSnapshot> captureForConfirmation(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordAuthorizationRequest request
    ) {
        DiscordAuthorizationDecision decision = authorize(actor, targetStaff, request);
        if (!decision.permitted()) {
            return Optional.empty();
        }
        return Optional.of(new DiscordAuthorizationSnapshot(
                actor.id(),
                actor.rank(),
                targetStaff.map(Actor::id),
                targetStaff.map(Actor::rank),
                request
        ));
    }

    public DiscordAuthorizationDecision reauthorize(
            DiscordAuthorizationSnapshot snapshot,
            Actor currentActor,
            Optional<Actor> currentTargetStaff
    ) {
        if (snapshot == null || currentActor == null || currentTargetStaff == null) {
            throw new IllegalArgumentException("snapshot, currentActor and currentTargetStaff must be present");
        }
        if (!snapshot.actorId().equals(currentActor.id()) || snapshot.actorRank() != currentActor.rank()) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.STALE_AUTHORIZATION);
        }
        if (!snapshot.targetStaffId().equals(currentTargetStaff.map(Actor::id))
                || !snapshot.targetStaffRank().equals(currentTargetStaff.map(Actor::rank))) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.STALE_AUTHORIZATION);
        }
        return authorize(currentActor, currentTargetStaff, snapshot.request());
    }

    private DiscordAuthorizationDecision authorizeTarget(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordModerationOperation operation
    ) {
        if (!operation.isMutation() || targetStaff.isEmpty()) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        Actor target = targetStaff.orElseThrow();
        if (actor.id().equals(target.id())) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.SELF_TARGET);
        }
        if (discordAuthorityLevel(actor.rank()) <= discordAuthorityLevel(target.rank())) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.TARGET_STAFF_PROTECTED);
        }
        return DiscordAuthorizationDecision.allow(Set.of());
    }

    private DiscordAuthorizationDecision authorizeMinecraft(Actor actor, DiscordAuthorizationRequest request) {
        if (!request.platforms().contains(ModerationPlatform.MINECRAFT) || !request.operation().isMutation()) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        if (actor.rank() == StaffRank.HELPER) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.HELPER_CROSS_PLATFORM_FORBIDDEN);
        }

        ModerationAction required = minecraftActionFor(request.operation());
        if (required != null && !minecraftAuthorization.permits(actor, required)) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
        }

        if (request.operation() == DiscordModerationOperation.ISSUE_SANCTION) {
            for (DiscordConsequenceIntent consequence : request.consequences()) {
                if (consequence.platform() != ModerationPlatform.MINECRAFT) {
                    continue;
                }
                if (consequence.customDuration()
                        && !minecraftAuthorization.permits(actor, ModerationAction.USE_CUSTOM_DURATION)) {
                    return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
                }
                if (consequence.customConsequence()
                        && !minecraftAuthorization.permits(actor, ModerationAction.USE_CUSTOM_COMBINATION)) {
                    return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.MINECRAFT_AUTHORIZATION_DENIED);
                }
            }
        }
        return DiscordAuthorizationDecision.allow(Set.of());
    }

    private DiscordAuthorizationDecision authorizeDiscordConsequence(
            StaffRank rank,
            DiscordConsequenceIntent consequence
    ) {
        if (rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER) {
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        if (rank == StaffRank.HELPER) {
            if (consequence.customDuration()) {
                return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.CUSTOM_DURATION_NOT_PERMITTED);
            }
            if (consequence.customConsequence()) {
                return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.CUSTOM_CONSEQUENCE_REQUIRES_ADMIN);
            }
            if (consequence.type() == DiscordConsequenceType.WARNING) {
                return DiscordAuthorizationDecision.allow(Set.of());
            }
            if (consequence.type() != DiscordConsequenceType.MUTE) {
                return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
            }
            if (consequence.length().isPermanent()) {
                return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
            }
            return within(consequence.length(), limits.helperMaxMute());
        }
        if (rank == StaffRank.MOD || rank == StaffRank.DEVELOPER) {
            if (consequence.customConsequence()) {
                return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.CUSTOM_CONSEQUENCE_REQUIRES_ADMIN);
            }
            if (consequence.length().isPermanent()) {
                return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.PERMANENT_ACTION_REQUIRES_ADMIN);
            }
            if (consequence.length().kind() == SanctionLength.Kind.TEMPORARY) {
                return within(consequence.length(), limits.moderatorMaximum(consequence.type()));
            }
            return DiscordAuthorizationDecision.allow(Set.of());
        }
        return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.UNAUTHORIZED_CONSEQUENCE);
    }

    private static DiscordAuthorizationDecision within(SanctionLength length, Duration maximum) {
        Duration duration = length.temporary().orElseThrow();
        if (duration.compareTo(maximum) > 0) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.DURATION_EXCEEDS_LIMIT);
        }
        return DiscordAuthorizationDecision.allow(Set.of());
    }

    private static boolean permitsDiscordOperation(StaffRank rank, DiscordModerationOperation operation) {
        return switch (rank) {
            case HELPER -> operation == DiscordModerationOperation.VIEW_LINKED_ACCOUNTS
                    || operation == DiscordModerationOperation.VIEW_HISTORY
                    || operation == DiscordModerationOperation.VIEW_NOTES
                    || operation == DiscordModerationOperation.VIEW_EVIDENCE
                    || operation == DiscordModerationOperation.ISSUE_SANCTION;
            case MOD, DEVELOPER -> operation == DiscordModerationOperation.VIEW_LINKED_ACCOUNTS
                    || operation == DiscordModerationOperation.VIEW_HISTORY
                    || operation == DiscordModerationOperation.VIEW_NOTES
                    || operation == DiscordModerationOperation.VIEW_EVIDENCE
                    || operation == DiscordModerationOperation.ISSUE_SANCTION
                    || operation == DiscordModerationOperation.END_SANCTION
                    || operation == DiscordModerationOperation.REVOKE_SANCTION
                    || operation == DiscordModerationOperation.APPROVE_SANCTION_REQUEST
                    || operation == DiscordModerationOperation.REQUEST_OVERTURN;
            case ADMIN, FOUNDER -> true;
            case SYSTEM -> false;
        };
    }

    private static ModerationAction minecraftActionFor(DiscordModerationOperation operation) {
        return switch (operation) {
            case ISSUE_SANCTION -> ModerationAction.ISSUE_POLICY_SANCTION;
            case END_SANCTION -> ModerationAction.END_SANCTION;
            case REVOKE_SANCTION -> ModerationAction.REVOKE_SANCTION;
            case APPROVE_SANCTION_REQUEST -> ModerationAction.APPROVE_POLICY_SANCTION;
            case REQUEST_OVERTURN -> ModerationAction.REQUEST_FULL_OVERTURN;
            case APPROVE_OVERTURN -> ModerationAction.APPROVE_OVERTURN;
            case FULL_OVERTURN -> ModerationAction.FULL_OVERTURN;
            case VIEW_LINKED_ACCOUNTS, VIEW_HISTORY, VIEW_NOTES, VIEW_EVIDENCE -> null;
        };
    }

    private static int discordAuthorityLevel(StaffRank rank) {
        return switch (rank) {
            case HELPER -> 10;
            case MOD, DEVELOPER -> 20;
            case ADMIN -> 30;
            case FOUNDER -> 40;
            case SYSTEM -> 50;
        };
    }

    private static Set<DiscordEnforcementPrecondition> requiredPreconditions(DiscordAuthorizationRequest request) {
        EnumSet<DiscordEnforcementPrecondition> required = EnumSet.noneOf(DiscordEnforcementPrecondition.class);
        if (request.operation().isMutation() && request.platforms().contains(ModerationPlatform.MINECRAFT)) {
            required.add(DiscordEnforcementPrecondition.MINECRAFT_PUNISHMENT_POLICY_REVALIDATION);
        }
        if (requiresDiscordRoleHierarchy(request)) {
            required.add(DiscordEnforcementPrecondition.DISCORD_ROLE_HIERARCHY);
        }
        return required;
    }

    private static boolean requiresDiscordRoleHierarchy(DiscordAuthorizationRequest request) {
        if (!request.platforms().contains(ModerationPlatform.DISCORD) || !request.operation().isMutation()) {
            return false;
        }
        return switch (request.operation()) {
            case ISSUE_SANCTION -> request.consequences().stream()
                    .filter(intent -> intent.platform() == ModerationPlatform.DISCORD)
                    .anyMatch(intent -> intent.type() != DiscordConsequenceType.WARNING);
            case REQUEST_OVERTURN -> false;
            case END_SANCTION, REVOKE_SANCTION, APPROVE_SANCTION_REQUEST, APPROVE_OVERTURN, FULL_OVERTURN -> true;
            case VIEW_LINKED_ACCOUNTS, VIEW_HISTORY, VIEW_NOTES, VIEW_EVIDENCE -> false;
        };
    }
}
