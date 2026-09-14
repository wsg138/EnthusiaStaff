package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordIdentityRef;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.MinecraftIdentityRef;
import net.enthusia.staff.domain.moderation.ModerationIdentity;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;

class DiscordRoleSyncServiceTest {
    private static final DiscordUserId DISCORD = new DiscordUserId("123456789012345678");
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-14T13:00:00Z");
    private static final String HELPER_ROLE = "1001";
    private static final String MOD_ROLE = "1002";

    @Test
    void unionsEligibilityAcrossEveryCurrentLinkedMinecraftAccount() {
        FakeStore store = new FakeStore();
        store.subjects.put(DISCORD, subject(Optional.empty(), FIRST, SECOND));
        Map<UUID, Set<String>> groups = Map.of(FIRST, Set.of("helper"), SECOND, Set.of("mod", "vip"));
        DiscordRoleSyncService service = service(store, player -> groups.getOrDefault(player, Set.of()), NOW);

        DiscordRoleSyncService.Evaluation result = service.evaluate(DISCORD);

        assertEquals(Set.of(HELPER_ROLE, MOD_ROLE), result.desiredRoleIds());
    }

    @Test
    void mainAccountSelectionDoesNotLimitRoleUnion() {
        FakeStore store = new FakeStore();
        Map<UUID, Set<String>> groups = Map.of(FIRST, Set.of("helper"), SECOND, Set.of("mod"));
        DiscordRoleSyncService service = service(store, player -> groups.getOrDefault(player, Set.of()), NOW);

        store.subjects.put(DISCORD, subject(Optional.of(main(FIRST)), FIRST, SECOND));
        Set<String> firstMain = service.evaluate(DISCORD).desiredRoleIds();
        store.subjects.put(DISCORD, subject(Optional.of(main(SECOND)), FIRST, SECOND));
        Set<String> secondMain = service.evaluate(DISCORD).desiredRoleIds();

        assertEquals(Set.of(HELPER_ROLE, MOD_ROLE), firstMain);
        assertEquals(firstMain, secondMain);
    }

    @Test
    void unlinkingAllMinecraftAccountsProjectsNoManagedRoles() {
        FakeStore store = new FakeStore();
        store.subjects.put(DISCORD, discordOnlySubject());
        DiscordRoleSyncService service = service(store, ignored -> Set.of("mod"), NOW);

        assertTrue(service.evaluate(DISCORD).desiredRoleIds().isEmpty());
    }

    @Test
    void retryBackoffSurvivesServiceRestart() {
        FakeStore store = new FakeStore();
        DiscordRoleSyncService first = service(store, ignored -> Set.of(), NOW);
        first.recordRetry(DISCORD, Set.of(HELPER_ROLE), Set.of(), "role_add_failed");

        assertFalse(first.due(DISCORD));
        DiscordRoleSyncService restarted = service(store, ignored -> Set.of(), NOW.plusSeconds(61));
        assertTrue(restarted.due(DISCORD));
        assertEquals(1, store.state().attemptCount());
        assertEquals(Optional.of(NOW.plusSeconds(60)), store.state().nextAttemptAt());
    }

    @Test
    void eligibilityFailurePreservesLastKnownDesiredAndObservedSnapshots() {
        FakeStore store = new FakeStore();
        DiscordRoleSyncService service = service(store, ignored -> Set.of(), NOW);
        service.recordSuccess(
                new DiscordRoleSyncService.Evaluation(DISCORD, Set.of(MOD_ROLE)),
                Set.of(HELPER_ROLE),
                "SHADOW_DRIFT"
        );

        service.recordEvaluationRetry(DISCORD, "eligibility_unavailable");

        ReconciliationState state = store.state();
        assertEquals("{\"roles\":[\"1002\"]}", state.desiredStateJson());
        assertEquals(Optional.of("{\"roles\":[\"1001\"]}"), state.observedStateJson());
        assertEquals("RETRY", state.state());
        assertEquals(1, state.attemptCount());
    }

    @Test
    void revisionConflictIsRereadAndRetriedOnce() {
        ConflictStore store = new ConflictStore();
        DiscordRoleSyncService service = service(store, ignored -> Set.of(), NOW);

        service.recordRetry(DISCORD, Set.of(HELPER_ROLE), Set.of(), "role_add_failed");

        assertEquals(2, store.saveAttempts);
        assertEquals(1L, store.state().revision());
    }

    @Test
    void persistenceFailureWithoutRevisionChangePropagates() {
        SameRevisionFailureStore store = new SameRevisionFailureStore();
        DiscordRoleSyncService service = service(store, ignored -> Set.of(), NOW);

        assertThrows(ModerationPersistenceException.class,
                () -> service.recordRetry(DISCORD, Set.of(), Set.of(), "role_sync_failure"));
    }

    private static DiscordRoleSyncService service(
            DiscordRoleSyncService.StateStore store,
            MinecraftRoleEligibilityClient eligibility,
            Instant now
    ) {
        return new DiscordRoleSyncService(
                store,
                eligibility,
                new DiscordRoleSyncConfiguration(
                        DiscordRoleSyncConfiguration.Mode.SHADOW,
                        Map.of("helper", HELPER_ROLE, "mod", MOD_ROLE),
                        Set.of("9000"),
                        Duration.ofSeconds(60),
                        25
                ),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private static MainMinecraftAccount main(UUID playerId) {
        return new MainMinecraftAccount(playerId, MainAccountSelectionSource.AUTOMATIC);
    }

    private static VersionedSubject subject(Optional<MainMinecraftAccount> main, UUID... players) {
        List<ModerationIdentity> identities = new ArrayList<>();
        identities.add(new DiscordIdentityRef(DISCORD));
        for (UUID player : players) {
            identities.add(new MinecraftIdentityRef(player));
        }
        return new VersionedSubject(
                new ModerationSubject(new ModerationSubjectId(UUID.randomUUID()), Set.copyOf(identities), main),
                0
        );
    }

    private static VersionedSubject discordOnlySubject() {
        return new VersionedSubject(
                new ModerationSubject(
                        new ModerationSubjectId(UUID.randomUUID()),
                        Set.of(new DiscordIdentityRef(DISCORD)),
                        Optional.empty()
                ),
                0
        );
    }

    private static class FakeStore implements DiscordRoleSyncService.StateStore {
        final Map<DiscordUserId, VersionedSubject> subjects = new HashMap<>();
        final Map<String, ReconciliationState> reconciliations = new HashMap<>();
        int saveAttempts;

        @Override
        public List<DiscordUserId> discordUsersAfter(Optional<DiscordUserId> cursor, int limit) {
            return List.of();
        }

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
            return Optional.ofNullable(subjects.get(userId));
        }

        @Override
        public Optional<ReconciliationState> reconciliation(String key) {
            return Optional.ofNullable(reconciliations.get(key));
        }

        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            saveAttempts++;
            long revision = expectedRevision < 0 ? 0 : expectedRevision + 1;
            ReconciliationState saved = withRevision(state, revision);
            reconciliations.put(state.reconciliationKey(), saved);
            return saved;
        }

        ReconciliationState state() {
            return reconciliations.values().stream().findFirst().orElseThrow();
        }
    }

    private static final class ConflictStore extends FakeStore {
        private boolean conflicted;

        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            if (!conflicted) {
                conflicted = true;
                saveAttempts++;
                reconciliations.put(state.reconciliationKey(), withRevision(state, 0));
                throw new ModerationPersistenceException("simulated revision race");
            }
            return super.save(state, expectedRevision, now);
        }
    }

    private static final class SameRevisionFailureStore extends FakeStore {
        @Override
        public ReconciliationState save(ReconciliationState state, long expectedRevision, Instant now) {
            saveAttempts++;
            throw new ModerationPersistenceException("simulated database failure");
        }
    }

    private static ReconciliationState withRevision(ReconciliationState state, long revision) {
        return new ReconciliationState(
                state.reconciliationKey(),
                state.resourceType(),
                state.resourceId(),
                state.desiredStateJson(),
                state.observedStateJson(),
                state.state(),
                state.attemptCount(),
                state.nextAttemptAt(),
                state.lastErrorCode(),
                revision
        );
    }
}
