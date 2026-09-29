package net.enthusia.staff.paper;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

final class PaperRuntimeLifecycle<S, T, C> {
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final Object lock = new Object();
    private final AtomicReference<Optional<S>> storage = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<T>> task = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<C>> channel = new AtomicReference<>(Optional.empty());

    boolean stopping() {
        return stopping.get();
    }

    boolean publishStorage(S value) {
        synchronized (lock) {
            if (stopping.get() || storage.get().isPresent()) {
                return false;
            }
            storage.set(Optional.of(value));
            return true;
        }
    }

    Optional<S> storage() {
        return storage.get();
    }

    <R> R storageValue(Function<S, R> selector) {
        return storage.get().map(selector).orElse(null);
    }

    Optional<S> removeStorage() {
        synchronized (lock) {
            return storage.getAndSet(Optional.empty());
        }
    }

    Optional<S> removeStorageIf(Predicate<S> predicate) {
        synchronized (lock) {
            Optional<S> current = storage.get();
            if (current.filter(predicate).isEmpty()) {
                return Optional.empty();
            }
            storage.set(Optional.empty());
            return current;
        }
    }

    boolean publishTask(T value) {
        synchronized (lock) {
            if (stopping.get() || task.get().isPresent()) {
                return false;
            }
            task.set(Optional.of(value));
            return true;
        }
    }

    Optional<T> removeTask() {
        return task.getAndSet(Optional.empty());
    }

    boolean publishChannel(C value) {
        synchronized (lock) {
            if (stopping.get() || channel.get().isPresent()) {
                return false;
            }
            channel.set(Optional.of(value));
            return true;
        }
    }

    Optional<C> removeChannel() {
        return channel.getAndSet(Optional.empty());
    }

    void beginShutdown(Runnable transition) {
        stopping.set(true);
        synchronized (lock) {
            transition.run();
        }
    }

    boolean runIfRunning(Runnable action) {
        synchronized (lock) {
            if (stopping.get()) {
                return false;
            }
            action.run();
            return true;
        }
    }
}
