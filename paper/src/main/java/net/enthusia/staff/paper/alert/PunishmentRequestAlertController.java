package net.enthusia.staff.paper.alert;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

public final class PunishmentRequestAlertController implements AutoCloseable {
    private final String owner;
    private final LifecycleFactory factory;
    private final Consumer<Status> statusSink;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    private PunishmentRequestAlertWorkerSettings desired;
    private Storage storage;
    private PunishmentRequestAlertManagedLifecycle current;
    private Status status;
    private boolean closed;

    public PunishmentRequestAlertController(
            String owner,
            PunishmentRequestAlertWorkerSettings initialSettings,
            LifecycleFactory factory,
            Consumer<Status> statusSink
    ) {
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("alert lease owner must be present and at most 128 characters");
        }
        this.owner = owner;
        this.desired = Objects.requireNonNull(initialSettings, "initialSettings");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.statusSink = Objects.requireNonNull(statusSink, "statusSink");
        this.status = initialSettings.enabled()
                ? Status.waitingForStorage()
                : Status.disabled();
        publish(status);
    }

    public ApplyResult attachStorage(Storage candidate) {
        synchronized (this) {
            Objects.requireNonNull(candidate, "candidate");
            if (closed || shuttingDown.get()) {
                return ApplyResult.shuttingDown(status);
            }
            if (storage != null && storage != candidate) {
                return ApplyResult.failed(
                        Outcome.UNAVAILABLE,
                        status,
                        "Punishment-request alert storage is already attached"
                );
            }
            storage = candidate;
            if (!desired.enabled()) {
                return update(ApplyResult.noChanges(setStatus(Status.disabled())));
            }
            if (current != null && current.active()) {
                return update(ApplyResult.noChanges(setStatus(Status.active())));
            }
            return startWithoutPrevious(desired, Outcome.ENABLED, "Durable punishment-request alerts enabled");
        }
    }

    public PreparedChange prepare(PunishmentRequestAlertWorkerSettings candidate) {
        synchronized (this) {
            return prepareLocked(candidate);
        }
    }

    private PreparedChange prepareLocked(PunishmentRequestAlertWorkerSettings candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed || shuttingDown.get()) {
            return new PreparedChange(
                    this, candidate, desired, current, storage, null,
                    new IllegalStateException("alert controller is shutting down")
            );
        }
        PunishmentRequestAlertManagedLifecycle prepared = null;
        RuntimeException failure = null;
        if (requiresLifecycle(candidate)) {
            try {
                prepared = factory.create(owner, candidate, storage);
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        return new PreparedChange(this, candidate, desired, current, storage, prepared, failure);
    }

    private boolean requiresLifecycle(PunishmentRequestAlertWorkerSettings candidate) {
        return candidate.enabled()
                && storage != null
                && (current == null || !current.active() || !candidate.equals(desired));
    }

    public ApplyResult apply(PunishmentRequestAlertWorkerSettings candidate) {
        synchronized (this) {
            return applyLocked(candidate);
        }
    }

    private ApplyResult applyLocked(PunishmentRequestAlertWorkerSettings candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed || shuttingDown.get()) {
            return ApplyResult.shuttingDown(status);
        }
        boolean settingsChanged = !candidate.equals(desired);
        if (!candidate.enabled()) {
            return disable(candidate, settingsChanged);
        }
        if (storage == null) {
            return waitForStorage(candidate, settingsChanged);
        }
        if (hasUnchangedActiveLifecycle(settingsChanged)) {
            return update(ApplyResult.noChanges(setStatus(Status.active())));
        }
        if (current == null) {
            desired = candidate;
            return startWithoutPrevious(candidate, Outcome.ENABLED, "Durable punishment-request alerts enabled");
        }
        return replace(candidate);
    }

    private boolean hasUnchangedActiveLifecycle(boolean settingsChanged) {
        return !settingsChanged && current != null && current.active();
    }

    private ApplyResult disable(
            PunishmentRequestAlertWorkerSettings candidate,
            boolean settingsChanged
    ) {
        desired = candidate;
        boolean hadLifecycle = current != null;
        closeCurrent();
        Status next = setStatus(Status.disabled());
        return update(hadLifecycle || settingsChanged
                ? ApplyResult.changed(Outcome.DISABLED, next, "Durable punishment-request alerts disabled")
                : ApplyResult.noChanges(next));
    }

    private ApplyResult waitForStorage(
            PunishmentRequestAlertWorkerSettings candidate,
            boolean settingsChanged
    ) {
        desired = candidate;
        Status next = setStatus(Status.waitingForStorage());
        return update(settingsChanged
                ? ApplyResult.changed(
                        Outcome.WAITING_FOR_STORAGE,
                        next,
                        "Configuration accepted; alert startup is waiting for storage readiness"
                )
                : ApplyResult.noChanges(next));
    }

    public void detachStorage() {
        synchronized (this) {
            closeCurrent();
            storage = null;
            if (!closed) {
                setStatus(desired.enabled() ? Status.waitingForStorage() : Status.disabled());
                publish(status);
            }
        }
    }

    public PunishmentRequestAlertWorkerSettings currentSettings() {
        synchronized (this) {
            return desired;
        }
    }

    public Status currentStatus() {
        synchronized (this) {
            return status;
        }
    }

    public String owner() {
        return owner;
    }

    public boolean active() {
        synchronized (this) {
            return current != null && current.active() && !closed && !shuttingDown.get();
        }
    }

    public boolean shuttingDown() {
        synchronized (this) {
            return closed || shuttingDown.get();
        }
    }

    public ApplyResult forceUnavailable(
            PunishmentRequestAlertWorkerSettings desiredSettings,
            String issue
    ) {
        synchronized (this) {
            Objects.requireNonNull(desiredSettings, "desiredSettings");
            if (closed || shuttingDown.get()) {
                return ApplyResult.shuttingDown(status);
            }
            closeCurrent();
            desired = desiredSettings;
            return update(ApplyResult.failed(
                    Outcome.UNAVAILABLE,
                    setStatus(Status.unavailable(issue)),
                    issue
            ));
        }
    }

    @Override
    public void close() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            closeCurrent();
            storage = null;
            setStatus(Status.closed());
            publish(status);
        }
    }

    private ApplyResult commitPrepared(PreparedChange preparedChange) {
        synchronized (this) {
            return commitPreparedLocked(preparedChange);
        }
    }

    private ApplyResult commitPreparedLocked(PreparedChange preparedChange) {
        if (preparedChange.controller != this) {
            throw new IllegalArgumentException("prepared alert change belongs to another controller");
        }
        Optional<ApplyResult> rejection = preparedRejection(preparedChange);
        if (rejection.isPresent()) {
            return rejection.orElseThrow();
        }

        PunishmentRequestAlertManagedLifecycle candidate = preparedChange.takeLifecycle();
        if (candidate == null) {
            return applyLocked(preparedChange.candidateSettings);
        }
        return current == null
                ? startPreparedWithoutPrevious(preparedChange.candidateSettings, candidate)
                : replacePrepared(preparedChange.candidateSettings, candidate);
    }

    private Optional<ApplyResult> preparedRejection(PreparedChange preparedChange) {
        if (closed || shuttingDown.get()) {
            closeQuietly(preparedChange.takeLifecycle());
            return Optional.of(ApplyResult.shuttingDown(status));
        }
        if (preparedChange.failure != null) {
            return Optional.of(update(ApplyResult.failed(
                    Outcome.RESTORED,
                    setStatus(restoredStatus(
                            "Candidate construction failed; previous alert worker remains active"
                    )),
                    "Alert-worker candidate construction failed before runtime mutation",
                    preparedChange.failure
            )));
        }
        if (runtimeChanged(preparedChange)) {
            closeQuietly(preparedChange.takeLifecycle());
            return Optional.of(update(ApplyResult.failed(
                    Outcome.RESTORED,
                    setStatus(restoredStatus("Alert runtime changed before prepared commit")),
                    "Prepared alert change was discarded because runtime state changed"
            )));
        }
        return Optional.empty();
    }

    private boolean runtimeChanged(PreparedChange preparedChange) {
        return !Objects.equals(desired, preparedChange.previousSettings)
                || current != preparedChange.previousLifecycle
                || storage != preparedChange.preparedStorage;
    }

    private Status restoredStatus(String issue) {
        return current != null && current.active() ? Status.restored(issue) : status;
    }

    private ApplyResult replacePrepared(
            PunishmentRequestAlertWorkerSettings candidateSettings,
            PunishmentRequestAlertManagedLifecycle candidate
    ) {
        PunishmentRequestAlertWorkerSettings previousSettings = desired;
        PunishmentRequestAlertManagedLifecycle previous = current;
        current = null;
        desired = candidateSettings;
        closeQuietly(previous);
        if (closed || shuttingDown.get()) {
            closeQuietly(candidate);
            return update(ApplyResult.shuttingDown(setStatus(Status.closed())));
        }
        try {
            if (!candidate.start()) {
                throw new IllegalStateException("prepared replacement alert lifecycle did not start");
            }
            if (shuttingDown.get()) {
                closeQuietly(candidate);
                return update(ApplyResult.shuttingDown(setStatus(Status.closed())));
            }
            current = candidate;
            desired = candidateSettings;
            return update(ApplyResult.changed(
                    Outcome.REPLACED,
                    setStatus(Status.active()),
                    "Punishment-request alert worker settings replaced"
            ));
        } catch (RuntimeException replacementFailure) {
            closeQuietly(candidate);
            return restore(previousSettings, replacementFailure);
        }
    }

    private ApplyResult startPreparedWithoutPrevious(
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertManagedLifecycle candidate
    ) {
        try {
            if (!candidate.start()) {
                throw new IllegalStateException("prepared alert lifecycle did not start");
            }
            if (closed || shuttingDown.get()) {
                closeQuietly(candidate);
                return update(ApplyResult.shuttingDown(setStatus(Status.closed())));
            }
            current = candidate;
            desired = settings;
            return update(ApplyResult.changed(
                    Outcome.ENABLED, setStatus(Status.active()),
                    "Durable punishment-request alerts enabled"));
        } catch (RuntimeException exception) {
            closeQuietly(candidate);
            current = null;
            desired = settings;
            return update(ApplyResult.failed(
                    Outcome.UNAVAILABLE,
                    setStatus(Status.unavailable("Alert worker startup failed")),
                    "Punishment-request alerts could not be started",
                    exception
            ));
        }
    }

    private ApplyResult replace(PunishmentRequestAlertWorkerSettings candidateSettings) {
        PunishmentRequestAlertWorkerSettings previousSettings = desired;
        PunishmentRequestAlertManagedLifecycle previous = current;
        PunishmentRequestAlertManagedLifecycle candidate;
        try {
            candidate = factory.create(owner, candidateSettings, storage);
        } catch (RuntimeException exception) {
            return update(ApplyResult.failed(
                    Outcome.RESTORED,
                    setStatus(Status.restored("Replacement construction failed; previous alert worker remains active")),
                    "Alert-worker replacement failed before shutdown; previous lifecycle remains active",
                    exception
            ));
        }

        current = null;
        desired = candidateSettings;
        closeQuietly(previous);
        if (closed || shuttingDown.get()) {
            closeQuietly(candidate);
            return update(ApplyResult.shuttingDown(setStatus(Status.closed())));
        }
        try {
            if (!candidate.start()) {
                throw new IllegalStateException("replacement alert lifecycle did not start");
            }
            if (shuttingDown.get()) {
                closeQuietly(candidate);
                return update(ApplyResult.shuttingDown(setStatus(Status.closed())));
            }
            current = candidate;
            desired = candidateSettings;
            return update(ApplyResult.changed(
                    Outcome.REPLACED,
                    setStatus(Status.active()),
                    "Punishment-request alert worker settings replaced"
            ));
        } catch (RuntimeException replacementFailure) {
            closeQuietly(candidate);
            return restore(previousSettings, replacementFailure);
        }
    }

    private ApplyResult restore(
            PunishmentRequestAlertWorkerSettings previousSettings,
            RuntimeException replacementFailure
    ) {
        try {
            PunishmentRequestAlertManagedLifecycle restored = factory.create(owner, previousSettings, storage);
            if (!restored.start()) {
                closeQuietly(restored);
                throw new IllegalStateException("previous alert lifecycle did not restart");
            }
            current = restored;
            desired = previousSettings;
            return update(ApplyResult.failed(
                    Outcome.RESTORED,
                    setStatus(Status.restored("Replacement failed; previous alert worker was restored")),
                    "Alert-worker replacement failed; previous lifecycle restored",
                    replacementFailure
            ));
        } catch (RuntimeException restorationFailure) {
            replacementFailure.addSuppressed(restorationFailure);
            current = null;
            return update(ApplyResult.failed(
                    Outcome.UNAVAILABLE,
                    setStatus(Status.unavailable("Replacement and rollback both failed; alerts are unavailable")),
                    "Alert-worker replacement failed and alerts are now disabled",
                    replacementFailure
            ));
        }
    }

    private ApplyResult startWithoutPrevious(
            PunishmentRequestAlertWorkerSettings settings,
            Outcome outcome,
            String message
    ) {
        PunishmentRequestAlertManagedLifecycle candidate = null;
        try {
            candidate = factory.create(owner, settings, storage);
            if (!candidate.start()) {
                throw new IllegalStateException("alert lifecycle did not start");
            }
            if (closed || shuttingDown.get()) {
                closeQuietly(candidate);
                return update(ApplyResult.shuttingDown(setStatus(Status.closed())));
            }
            current = candidate;
            desired = settings;
            return update(ApplyResult.changed(outcome, setStatus(Status.active()), message));
        } catch (RuntimeException exception) {
            closeQuietly(candidate);
            current = null;
            desired = settings;
            return update(ApplyResult.failed(
                    Outcome.UNAVAILABLE,
                    setStatus(Status.unavailable("Alert worker startup failed")),
                    "Punishment-request alerts could not be started",
                    exception
            ));
        }
    }

    private void closeCurrent() {
        PunishmentRequestAlertManagedLifecycle closing = current;
        current = null;
        closeQuietly(closing);
    }

    private static void closeQuietly(PunishmentRequestAlertManagedLifecycle lifecycle) {
        if (lifecycle == null) {
            return;
        }
        try {
            lifecycle.close();
        } catch (RuntimeException ignored) {
            // Controller state must still advance; the caller records the replacement outcome.
        }
    }

    private Status setStatus(Status next) {
        status = Objects.requireNonNull(next, "next");
        return status;
    }

    private ApplyResult update(ApplyResult result) {
        publish(result.status());
        return result;
    }

    private void publish(Status next) {
        statusSink.accept(next);
    }

    public static final class PreparedChange implements AutoCloseable {
        private final PunishmentRequestAlertController controller;
        private final PunishmentRequestAlertWorkerSettings candidateSettings;
        private final PunishmentRequestAlertWorkerSettings previousSettings;
        private final PunishmentRequestAlertManagedLifecycle previousLifecycle;
        private final Storage preparedStorage;
        private final RuntimeException failure;
        private final AtomicBoolean completed = new AtomicBoolean();
        private PunishmentRequestAlertManagedLifecycle lifecycle;

        private PreparedChange(
                PunishmentRequestAlertController controller,
                PunishmentRequestAlertWorkerSettings candidateSettings,
                PunishmentRequestAlertWorkerSettings previousSettings,
                PunishmentRequestAlertManagedLifecycle previousLifecycle,
                Storage preparedStorage,
                PunishmentRequestAlertManagedLifecycle lifecycle,
                RuntimeException failure
        ) {
            this.controller = controller;
            this.candidateSettings = candidateSettings;
            this.previousSettings = previousSettings;
            this.previousLifecycle = previousLifecycle;
            this.preparedStorage = preparedStorage;
            this.lifecycle = lifecycle;
            this.failure = failure;
        }

        public RuntimeException preparationFailure() {
            return failure;
        }

        public ApplyResult commit() {
            if (!completed.compareAndSet(false, true)) {
                throw new IllegalStateException("prepared alert change was already completed");
            }
            return controller.commitPrepared(this);
        }

        @Override
        public void close() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            closeQuietly(takeLifecycle());
        }

        private PunishmentRequestAlertManagedLifecycle takeLifecycle() {
            PunishmentRequestAlertManagedLifecycle value = lifecycle;
            lifecycle = null;
            return value;
        }
    }

    @FunctionalInterface
    public interface LifecycleFactory {
        PunishmentRequestAlertManagedLifecycle create(
                String owner,
                PunishmentRequestAlertWorkerSettings settings,
                Storage storage
        );
    }

    public record Storage(
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players
    ) {
        public Storage {
            Objects.requireNonNull(alerts, "alerts");
            Objects.requireNonNull(requests, "requests");
            Objects.requireNonNull(players, "players");
        }
    }

    public enum State {
        DISABLED,
        ACTIVE,
        WAITING_FOR_STORAGE,
        RESTORED_AFTER_FAILURE,
        UNAVAILABLE_AFTER_FAILURE,
        CLOSED
    }

    public record Status(State state, String issue) {
        public Status {
            Objects.requireNonNull(state, "state");
            issue = issue == null ? "" : issue;
        }

        static Status disabled() {
            return new Status(State.DISABLED, "Durable punishment-request alerts are disabled by configuration");
        }

        static Status active() {
            return new Status(State.ACTIVE, "");
        }

        static Status waitingForStorage() {
            return new Status(State.WAITING_FOR_STORAGE,
                    "Durable punishment-request alerts are enabled and waiting for storage readiness");
        }

        static Status restored(String issue) {
            return new Status(State.RESTORED_AFTER_FAILURE, issue);
        }

        static Status unavailable(String issue) {
            return new Status(State.UNAVAILABLE_AFTER_FAILURE, issue);
        }

        static Status closed() {
            return new Status(State.CLOSED, "Alert controller is closed");
        }
    }

    public enum Outcome {
        NO_CHANGES,
        ENABLED,
        DISABLED,
        REPLACED,
        WAITING_FOR_STORAGE,
        RESTORED,
        UNAVAILABLE,
        SHUTTING_DOWN
    }

    public record ApplyResult(
            Outcome outcome,
            Status status,
            String message,
            RuntimeException failure
     ) {
        public ApplyResult {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(message, "message");
        }

        public boolean applied() {
            return switch (outcome) {
                case NO_CHANGES, ENABLED, DISABLED, REPLACED, WAITING_FOR_STORAGE -> true;
                case RESTORED, UNAVAILABLE, SHUTTING_DOWN -> false;
            };
        }

        static ApplyResult noChanges(Status status) {
            return new ApplyResult(Outcome.NO_CHANGES, status,
                    "Configuration is valid; no alert-worker changes were needed", null);
        }

        static ApplyResult changed(Outcome outcome, Status status, String message) {
            return new ApplyResult(outcome, status, message, null);
        }

        static ApplyResult failed(Outcome outcome, Status status, String message) {
            return new ApplyResult(outcome, status, message, null);
        }

        static ApplyResult failed(
                Outcome outcome,
                Status status,
                String message,
                RuntimeException failure
        ) {
            return new ApplyResult(outcome, status, message, failure);
        }

        static ApplyResult shuttingDown(Status status) {
            return new ApplyResult(Outcome.SHUTTING_DOWN, status,
                    "Configuration reload was rejected because shutdown has started", null);
        }
    }
}
