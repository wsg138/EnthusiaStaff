package net.enthusia.staff.paper.sanction;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Fail-closed reauthorization for queued sanction edits and overturns. */
public final class SanctionChangeWorkerAuthorization {
    private SanctionChangeWorkerAuthorization() {
    }

    /** Schedules all Bukkit permission reads on the player's owning entity thread. */
    static CompletableFuture<Actor> requestPlayerSnapshot(
            Player player, Plugin plugin, AuthorizationPolicy authorization,
            SanctionChangeAction action
    ) {
        CompletableFuture<Actor> checked = new CompletableFuture<>();
        try {
            boolean scheduled = player.getScheduler().execute(plugin, () -> {
                try {
                    Actor fresh = player.isOnline()
                            ? PaperActorResolver.resolve(player).orElse(null) : null;
                    if (fresh == null
                            || !fresh.id().equals(player.getUniqueId())
                            || !authorization.permits(fresh, action.requiredModerationAction())
                            || !player.hasPermission(SanctionChangeAccess.permissionFor(action))) {
                        checked.complete(null);
                    } else {
                        checked.complete(fresh);
                    }
                } catch (RuntimeException denied) {
                    checked.complete(null);
                }
            }, () -> checked.complete(null), 1L);
            if (!scheduled) {
                checked.complete(null);
            }
        } catch (RuntimeException unavailable) {
            checked.complete(null);
        }
        return checked;
    }

    /**
     * Call ONLY from bounded storage worker threads, never the player's entity
     * thread. Return null when rank, Staff Mode, permission or scheduler changed.
     */
    public static Actor awaitCurrentActor(
            CommandSender sender, Actor captured, Plugin plugin,
            AuthorizationPolicy authorization, SanctionChangeAction action
    ) {
        Objects.requireNonNull(action);
        if (captured == null || authorization == null) {
            return null;
        }
        if (!(sender instanceof Player player)) {
            return authorization.permits(captured, action.requiredModerationAction())
                    ? captured : null;
        }
        try {
            Actor fresh = requestPlayerSnapshot(player, plugin, authorization, action)
                    .get(5, TimeUnit.SECONDS);
            if (fresh == null || !fresh.id().equals(captured.id())
                    || fresh.rank() != captured.rank()) {
                return null;
            }
            return fresh;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException | RuntimeException unavailable) {
            return null;
        }
    }
}
