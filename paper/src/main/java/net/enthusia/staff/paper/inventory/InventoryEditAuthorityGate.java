package net.enthusia.staff.paper.inventory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Revalidates inventory-edit authority on the staff member's owning Paper scheduler. */
final class InventoryEditAuthorityGate {
    static final String EDIT_PERMISSION = "enthusiastaff.inventory.edit";
    private static final Duration AUTHORITY_TIMEOUT = Duration.ofSeconds(2);

    private final JavaPlugin plugin;

    InventoryEditAuthorityGate(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    boolean current(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return current(new PaperAuthorityQuery(plugin, viewer), AUTHORITY_TIMEOUT);
    }

    static boolean current(AuthorityQuery query, Duration timeout) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        try {
            query.execute(
                    () -> decision.complete(query.online() && query.hasEditPermission()),
                    () -> decision.complete(false)
            );
        } catch (RuntimeException exception) {
            return false;
        }
        return await(decision, timeout);
    }

    private static boolean await(CompletableFuture<Boolean> decision, Duration timeout) {
        try {
            return decision.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            return false;
        }
    }

    interface AuthorityQuery {
        void execute(Runnable query, Runnable retired);

        boolean online();

        boolean hasEditPermission();
    }

    private record PaperAuthorityQuery(JavaPlugin plugin, Player viewer) implements AuthorityQuery {
        private PaperAuthorityQuery {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(viewer, "viewer");
        }

        @Override
        public void execute(Runnable query, Runnable retired) {
            viewer.getScheduler().execute(plugin, query, retired, 1L);
        }

        @Override
        public boolean online() {
            return viewer.isOnline();
        }

        @Override
        public boolean hasEditPermission() {
            return viewer.hasPermission(EDIT_PERMISSION);
        }
    }
}
