package net.enthusia.staff.paper.tester;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.ports.FakeBaseAuditStore;
import net.enthusia.staff.domain.tester.FakeBaseAuditAction;
import net.enthusia.staff.domain.tester.FakeBaseAuditEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class FakeBaseAuditWriter {
    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final Supplier<FakeBaseAuditStore> auditStore;
    private final ExecutorService workers;

    FakeBaseAuditWriter(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<FakeBaseAuditStore> auditStore,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.serverId = CheatTesterRuntimeSupport.requireServerId(serverId);
        this.auditStore = java.util.Objects.requireNonNull(auditStore, "auditStore");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    FakeBaseAuditStore loadedStore() {
        try {
            return auditStore.get();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fake-base audit storage lookup failed", exception);
            return null;
        }
    }

    FakeBaseAuditEvent event(
            UUID operationId,
            UUID actorId,
            UUID targetId,
            FakeBaseAuditAction action,
            String outcome,
            String reason
    ) {
        return new FakeBaseAuditEvent(
                UUID.randomUUID(), operationId, serverId, actorId, targetId, action, outcome, reason, clock.instant());
    }

    void recordBestEffort(FakeBaseAuditEvent event) {
        FakeBaseAuditStore loaded = loadedStore();
        if (loaded == null || !submit(() -> recordBestEffort(loaded, event))) {
            plugin.getLogger().warning(
                    "Fake-base lifecycle audit could not be queued; safety cleanup remains authoritative");
        }
    }

    boolean submit(Runnable operation) {
        if (workers.isShutdown()) {
            return false;
        }
        try {
            workers.execute(operation);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    private void recordBestEffort(FakeBaseAuditStore store, FakeBaseAuditEvent event) {
        try {
            store.record(event);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Fake-base cleanup/lifecycle audit failed", exception);
        }
    }
}
