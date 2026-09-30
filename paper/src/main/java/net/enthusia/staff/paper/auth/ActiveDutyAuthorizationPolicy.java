package net.enthusia.staff.paper.auth;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;

/**
 * Adds the local Minecraft Staff Mode requirement to an existing rank/action policy.
 *
 * <p>This wrapper is for player-originated Paper mutation paths. It deliberately does not replace
 * the shared authorization policy used by Discord, automated enforcement, or other system actors.
 * Permission/LuckPerms state is therefore not sufficient: a player actor must still have a usable
 * active Staff Mode session when the mutation is authorized.</p>
 */
public final class ActiveDutyAuthorizationPolicy implements AuthorizationPolicy {
    private static final UUID CONSOLE_ACTOR_ID = new UUID(0L, 0L);

    private final AuthorizationPolicy delegate;
    private final Predicate<UUID> activeDuty;

    public ActiveDutyAuthorizationPolicy(AuthorizationPolicy delegate, Predicate<UUID> activeDuty) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.activeDuty = Objects.requireNonNull(activeDuty, "activeDuty");
    }

    @Override
    public boolean permits(Actor actor, ModerationAction action) {
        if (actor == null || action == null || !delegate.permits(actor, action)) {
            return false;
        }
        if (actor.rank() == StaffRank.SYSTEM || CONSOLE_ACTOR_ID.equals(actor.id())) {
            return true;
        }
        return activeDuty.test(actor.id());
    }
}
