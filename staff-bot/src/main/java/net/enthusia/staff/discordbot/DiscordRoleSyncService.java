package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.persistence.DiscordRoleSyncPersistenceRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;

/** Computes deterministic D13 desired roles and persists bounded reconciliation diagnostics. */
final class DiscordRoleSyncService {
    private static final int MAX_LINKED_ACCOUNTS = 32;
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final String RESOURCE_TYPE = "DISCORD_ROLE_SYNC";
    private static final String KEY_PREFIX = "role-sync:";

    interface StateStore {
        List<DiscordUserId> discordUsersAfter(Optional<DiscordUserId> cursor, int limit);

        Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId);

        Optional<ReconciliationState> reconciliation(String key);

        ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now);
    }

    record Evaluation(DiscordUserId userId, Set<String> desiredRoleIds) {
        Evaluation {
            if (userId == null || desiredRoleIds == null) {
                throw new IllegalArgumentException("role-sync evaluation fields must be present");
            }
            desiredRoleIds = Set.copyOf(desiredRoleIds);
        }
    }

    private final StateStore store;
    private final MinecraftRoleEligibilityClient eligibility;
    private final DiscordRoleSyncConfiguration configuration;
    private final Clock clock;

    DiscordRoleSyncService(
            DiscordRoleSyncPersistenceRuntime persistence,
            MinecraftRoleEligibilityClient eligibility,
            DiscordRoleSyncConfiguration configuration,
            Clock clock
    ) {
        this(new RuntimeStateStore(persistence), eligibility, configuration, clock);
    }

    DiscordRoleSyncService(
            StateStore store,
            MinecraftRoleEligibilityClient eligibility,
            DiscordRoleSyncConfiguration configuration,
            Clock clock
    ) {
        if (store == null || eligibility == null || configuration == null || clock == null) {
            throw new IllegalArgumentException("role-sync service dependencies must be present");
        }
        this.store = store;
        this.eligibility = eligibility;
        this.configuration = configuration;
        this.clock = clock;
    }

    List<DiscordUserId> nextUsers(Optional<DiscordUserId> cursor) {
        return store.discordUsersAfter(cursor, configuration.batchSize());
    }

    boolean due(DiscordUserId userId) {
        Optional<ReconciliationState> state = store.reconciliation(key(userId));
        return state.flatMap(ReconciliationState::nextAttemptAt)
                .map(next -> !next.isAfter(clock.instant()))
                .orElse(true);
    }

    Evaluation evaluate(DiscordUserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Discord user ID must be present");
        }
        Set<UUID> accounts = store.subjectForDiscord(userId)
                .map(value -> value.subject().minecraftAccountIds())
                .orElse(Set.of());
        if (accounts.size() > MAX_LINKED_ACCOUNTS) {
            throw new IllegalStateException("role-sync subject exceeds the bounded linked-account limit");
        }
        Set<String> groups = new LinkedHashSet<>();
        accounts.stream().sorted(Comparator.comparing(UUID::toString))
                .forEach(playerId -> groups.addAll(eligibility.groups(playerId)));
        return new Evaluation(userId, configuration.desiredRoles(groups));
    }

    void recordSuccess(Evaluation evaluation, Set<String> observedRoleIds, String state) {
        saveRoles(evaluation.userId(), evaluation.desiredRoleIds(), observedRoleIds, state, Optional.empty(), false);
    }

    void recordRetry(
            DiscordUserId userId,
            Set<String> desiredRoleIds,
            Set<String> observedRoleIds,
            String errorCode
    ) {
        saveRoles(userId, desiredRoleIds, observedRoleIds, "RETRY", Optional.of(normalizeError(errorCode)), true);
    }

    void recordEvaluationRetry(DiscordUserId userId, String errorCode) {
        Instant now = clock.instant();
        Optional<ReconciliationState> current = store.reconciliation(key(userId));
        int attempts = nextAttemptCount(current);
        ReconciliationState proposed = new ReconciliationState(
                key(userId),
                RESOURCE_TYPE,
                userId.value(),
                current.map(ReconciliationState::desiredStateJson).orElseGet(() -> rolesJson(Set.of())),
                current.flatMap(ReconciliationState::observedStateJson),
                "RETRY",
                attempts,
                Optional.of(now.plus(retryDelay(attempts))),
                Optional.of(normalizeError(errorCode)),
                current.map(ReconciliationState::revision).orElse(0L)
        );
        persistWithConflictRetry(proposed, current, now);
    }

    DiscordRoleSyncConfiguration configuration() {
        return configuration;
    }

    private void saveRoles(
            DiscordUserId userId,
            Set<String> desired,
            Set<String> observed,
            String state,
            Optional<String> error,
            boolean retry
    ) {
        Instant now = clock.instant();
        Optional<ReconciliationState> current = store.reconciliation(key(userId));
        int attempts = retry ? nextAttemptCount(current) : 0;
        Optional<Instant> next = retry ? Optional.of(now.plus(retryDelay(attempts))) : Optional.empty();
        ReconciliationState proposed = new ReconciliationState(
                key(userId), RESOURCE_TYPE, userId.value(), rolesJson(desired), Optional.of(rolesJson(observed)),
                state, attempts, next, error, current.map(ReconciliationState::revision).orElse(0L)
        );
        persistWithConflictRetry(proposed, current, now);
    }

    private int nextAttemptCount(Optional<ReconciliationState> current) {
        return Math.addExact(current.map(ReconciliationState::attemptCount).orElse(0), 1);
    }

    private void persistWithConflictRetry(
            ReconciliationState proposed,
            Optional<ReconciliationState> current,
            Instant now
    ) {
        long expected = current.map(ReconciliationState::revision).orElse(-1L);
        try {
            store.save(proposed, expected, now);
        } catch (ModerationPersistenceException exception) {
            Optional<ReconciliationState> latest = store.reconciliation(proposed.reconciliationKey());
            long latestRevision = latest.map(ReconciliationState::revision).orElse(-1L);
            if (latestRevision == expected) {
                throw exception;
            }
            store.save(proposed, latestRevision, now);
        }
    }

    private Duration retryDelay(int attempt) {
        int exponent = Math.min(Math.max(attempt - 1, 0), 6);
        long seconds = Math.multiplyExact(configuration.interval().toSeconds(), 1L << exponent);
        return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_DELAY.toSeconds()));
    }

    private static String rolesJson(Set<String> roleIds) {
        if (roleIds == null) {
            throw new IllegalArgumentException("role IDs must be present");
        }
        String joined = roleIds.stream().sorted()
                .map(role -> "\"" + role + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"roles\":[" + joined + "]}";
    }

    private static String key(DiscordUserId userId) {
        return KEY_PREFIX + userId.value();
    }

    private static String normalizeError(String errorCode) {
        if (errorCode == null || !errorCode.matches("[a-z0-9_]{1,96}")) {
            return "role_sync_failure";
        }
        return errorCode;
    }

    private static final class RuntimeStateStore implements StateStore {
        private final DiscordRoleSyncPersistenceRuntime persistence;

        private RuntimeStateStore(DiscordRoleSyncPersistenceRuntime persistence) {
            if (persistence == null) {
                throw new IllegalArgumentException("role-sync persistence must be present");
            }
            this.persistence = persistence;
        }

        @Override
        public List<DiscordUserId> discordUsersAfter(Optional<DiscordUserId> cursor, int limit) {
            return persistence.discordUsersAfter(cursor, limit);
        }

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
            return persistence.subjectForDiscord(userId);
        }

        @Override
        public Optional<ReconciliationState> reconciliation(String key) {
            return persistence.reconciliation(key);
        }

        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            return persistence.saveReconciliation(state, expectedRevision, now);
        }
    }
}
