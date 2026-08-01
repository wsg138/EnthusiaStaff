package net.enthusia.staff.paper;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class PaperStorageBootstrapCoordinator<S> {
    @FunctionalInterface
    interface Scheduler {
        void execute(Runnable task);
    }

    private final BooleanSupplier stopping;
    private final Scheduler asynchronous;
    private final Scheduler synchronous;
    private final Supplier<S> openStorage;
    private final Predicate<S> publishStorage;
    private final Consumer<S> synchronousCompletion;
    private final Consumer<S> asynchronousFollowUp;
    private final Consumer<S> discardPublished;
    private final Consumer<S> closeUnpublished;
    private final Consumer<RuntimeException> failureHandler;
    private final AtomicBoolean started = new AtomicBoolean();

    PaperStorageBootstrapCoordinator(
            BooleanSupplier stopping,
            Scheduler asynchronous,
            Scheduler synchronous,
            Supplier<S> openStorage,
            Predicate<S> publishStorage,
            Consumer<S> synchronousCompletion,
            Consumer<S> asynchronousFollowUp,
            Consumer<S> discardPublished,
            Consumer<S> closeUnpublished,
            Consumer<RuntimeException> failureHandler
    ) {
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.asynchronous = Objects.requireNonNull(asynchronous, "asynchronous");
        this.synchronous = Objects.requireNonNull(synchronous, "synchronous");
        this.openStorage = Objects.requireNonNull(openStorage, "openStorage");
        this.publishStorage = Objects.requireNonNull(publishStorage, "publishStorage");
        this.synchronousCompletion = Objects.requireNonNull(synchronousCompletion, "synchronousCompletion");
        this.asynchronousFollowUp = Objects.requireNonNull(asynchronousFollowUp, "asynchronousFollowUp");
        this.discardPublished = Objects.requireNonNull(discardPublished, "discardPublished");
        this.closeUnpublished = Objects.requireNonNull(closeUnpublished, "closeUnpublished");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("storage bootstrap has already started");
        }
        if (stopping.getAsBoolean()) {
            return;
        }
        try {
            asynchronous.execute(this::openAsynchronously);
        } catch (RuntimeException exception) {
            reportFailure(exception);
        }
    }

    private void openAsynchronously() {
        if (stopping.getAsBoolean()) {
            return;
        }
        S opened = null;
        boolean published = false;
        try {
            opened = Objects.requireNonNull(openStorage.get(), "opened storage");
            if (stopping.getAsBoolean()) {
                closeUnpublished.accept(opened);
                return;
            }
            if (!publishStorage.test(opened)) {
                closeUnpublished.accept(opened);
                return;
            }
            published = true;
            if (stopping.getAsBoolean()) {
                discardPublished.accept(opened);
                return;
            }
            S storage = opened;
            synchronous.execute(() -> completeSynchronously(storage));
        } catch (RuntimeException exception) {
            discardAfterFailure(opened, published, exception);
            reportFailure(exception);
        }
    }

    private void completeSynchronously(S storage) {
        if (stopping.getAsBoolean()) {
            discardPublished.accept(storage);
            return;
        }
        try {
            synchronousCompletion.accept(storage);
        } catch (RuntimeException exception) {
            discardWithSuppression(storage, exception);
            reportFailure(exception);
            return;
        }
        if (stopping.getAsBoolean()) {
            discardPublished.accept(storage);
            return;
        }
        try {
            asynchronous.execute(() -> followUpAsynchronously(storage));
        } catch (RuntimeException exception) {
            discardWithSuppression(storage, exception);
            reportFailure(exception);
        }
    }

    private void followUpAsynchronously(S storage) {
        if (stopping.getAsBoolean()) {
            return;
        }
        try {
            asynchronousFollowUp.accept(storage);
        } catch (RuntimeException exception) {
            reportFailure(exception);
        }
    }

    private void discardAfterFailure(S opened, boolean published, RuntimeException failure) {
        if (opened == null) {
            return;
        }
        try {
            if (published) {
                discardPublished.accept(opened);
            } else {
                closeUnpublished.accept(opened);
            }
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void discardWithSuppression(S storage, RuntimeException failure) {
        try {
            discardPublished.accept(storage);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void reportFailure(RuntimeException failure) {
        if (!stopping.getAsBoolean()) {
            failureHandler.accept(failure);
        }
    }
}
