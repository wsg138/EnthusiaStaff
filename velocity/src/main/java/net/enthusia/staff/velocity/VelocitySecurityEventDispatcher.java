package net.enthusia.staff.velocity;

import com.velocitypowered.api.event.EventTask;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class VelocitySecurityEventDispatcher {
    private final Supplier<ExecutorService> executorSupplier;
    private final BooleanSupplier shuttingDown;

    VelocitySecurityEventDispatcher(
            Supplier<ExecutorService> executorSupplier,
            BooleanSupplier shuttingDown
    ) {
        this.executorSupplier = Objects.requireNonNull(executorSupplier, "executorSupplier");
        this.shuttingDown = Objects.requireNonNull(shuttingDown, "shuttingDown");
    }

    EventTask submit(Runnable operation, Runnable rejected) {
        ExecutorService executor = executorSupplier.get();
        if (executor == null || executor.isShutdown() || shuttingDown.getAsBoolean()) {
            return rejectedTask(rejected);
        }
        try {
            return EventTask.resumeWhenComplete(CompletableFuture.runAsync(operation, executor));
        } catch (RejectedExecutionException exception) {
            return rejectedTask(rejected);
        }
    }

    private static EventTask rejectedTask(Runnable rejected) {
        rejected.run();
        return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
    }
}
