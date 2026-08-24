package net.enthusia.staff.domain.auth;

import java.util.Optional;

/**
 * Single authoritative domain policy for staff moderation initiated through Discord.
 *
 * <p>This service deliberately accepts neither Discord roles nor command origin. Roles may control
 * command visibility and may be required later as an external enforcement precondition, but they
 * can never grant domain permission. Cross-platform requests carry their final explicit platform
 * selection and are checked against the existing Minecraft authorization policy as well.</p>
 */
public final class DiscordModerationAuthorizationService {
    private final DiscordOperationPolicy operations;
    private final DiscordMinecraftAuthorization minecraft;
    private final DiscordConsequencePolicy consequences;

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
        this.operations = new DiscordOperationPolicy();
        this.minecraft = new DiscordMinecraftAuthorization(minecraftAuthorization);
        this.consequences = new DiscordConsequencePolicy(limits);
    }

    public DiscordAuthorizationDecision authorize(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordAuthorizationRequest request
    ) {
        if (actor == null || targetStaff == null || request == null) {
            throw new IllegalArgumentException("actor, targetStaff and request must be present");
        }
        DiscordAuthorizationDecision targetDecision = operations.authorizeTarget(
                actor, targetStaff, request.operation());
        if (!targetDecision.permitted()) {
            return targetDecision;
        }
        if (!operations.permits(actor.rank(), request.operation())) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.UNAUTHORIZED_OPERATION);
        }
        DiscordAuthorizationDecision minecraftDecision = minecraft.authorize(actor, request);
        if (!minecraftDecision.permitted()) {
            return minecraftDecision;
        }
        DiscordAuthorizationDecision consequenceDecision = consequences.authorize(actor.rank(), request);
        if (!consequenceDecision.permitted()) {
            return consequenceDecision;
        }
        return DiscordAuthorizationDecision.allow(DiscordPreconditionPolicy.required(request));
    }

    public Optional<DiscordAuthorizationSnapshot> captureForConfirmation(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordAuthorizationRequest request
    ) {
        DiscordAuthorizationDecision decision = authorize(actor, targetStaff, request);
        return decision.permitted()
                ? Optional.of(snapshot(actor, targetStaff, request))
                : Optional.empty();
    }

    public DiscordAuthorizationDecision reauthorize(
            DiscordAuthorizationSnapshot snapshot,
            Actor currentActor,
            Optional<Actor> currentTargetStaff
    ) {
        if (snapshot == null || currentActor == null || currentTargetStaff == null) {
            throw new IllegalArgumentException("snapshot, currentActor and currentTargetStaff must be present");
        }
        if (!DiscordSnapshotPolicy.matches(snapshot, currentActor, currentTargetStaff)) {
            return DiscordAuthorizationDecision.deny(DiscordAuthorizationDenial.STALE_AUTHORIZATION);
        }
        return authorize(currentActor, currentTargetStaff, snapshot.request());
    }

    private static DiscordAuthorizationSnapshot snapshot(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordAuthorizationRequest request
    ) {
        return new DiscordAuthorizationSnapshot(
                actor.id(),
                actor.rank(),
                targetStaff.map(Actor::id),
                targetStaff.map(Actor::rank),
                request
        );
    }
}
