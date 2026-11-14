package net.enthusia.staff.paper.command;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Schedules live evidence reads on the target's owning entity thread. A
 * retired/rejected target never yields a snapshot; terminal callbacks are
 * single-use even when a scheduler invokes a callback before rejecting.
 */
final class ClientEvidenceOwnerCapture {
    @FunctionalInterface
    interface OwnerScheduler {
        boolean schedule(Runnable onOwner, Runnable retired);
    }

    private ClientEvidenceOwnerCapture() { }

    static <T> void capture(
            OwnerScheduler scheduler,
            BooleanSupplier targetOnline,
            Supplier<T> snapshot,
            Consumer<T> captured,
            Runnable unavailable,
            Consumer<RuntimeException> failed
    ) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(targetOnline, "targetOnline");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(unavailable, "unavailable");
        Objects.requireNonNull(failed, "failed");
        AtomicBoolean terminal = new AtomicBoolean();
        Runnable onOwner = () -> {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            try {
                if (!targetOnline.getAsBoolean()) {
                    unavailable.run();
                    return;
                }
                captured.accept(snapshot.get());
            } catch (RuntimeException exception) {
                failed.accept(exception);
            }
        };
        Runnable retired = () -> {
            if (terminal.compareAndSet(false, true)) {
                unavailable.run();
            }
        };
        try {
            if (!scheduler.schedule(onOwner, retired)) {
                retired.run();
            }
        } catch (RuntimeException exception) {
            if (terminal.compareAndSet(false, true)) {
                failed.accept(exception);
            }
        }
    }
}
