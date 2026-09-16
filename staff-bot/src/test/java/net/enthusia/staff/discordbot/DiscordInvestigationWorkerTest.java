package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;
import org.junit.jupiter.api.Test;

class DiscordInvestigationWorkerTest {
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final ModerationSubjectId SUBJECT = new ModerationSubjectId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final UUID PUNISHMENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void restartSafeDiscoveryRetriesChannelsIndependentlyAndRealertsOnNewPlayerRevision() {
        MutableClock clock = new MutableClock(NOW);
        FakeStore store = new FakeStore(candidate(7));
        AtomicInteger discordCalls = new AtomicInteger();
        AtomicInteger minecraftCalls = new AtomicInteger();
        DiscordInvestigationAlertSink discord = alert -> discordCalls.getAndIncrement() == 0
                ? DiscordInvestigationAlertSink.Delivery.retry("TEMPORARY")
                : DiscordInvestigationAlertSink.Delivery.success();
        DiscordInvestigationAlertSink minecraft = alert -> {
            minecraftCalls.incrementAndGet();
            return DiscordInvestigationAlertSink.Delivery.success();
        };
        DiscordInvestigationWorker worker = new DiscordInvestigationWorker(
                store, discord, minecraft, clock, Duration.ofSeconds(5), Duration.ofMinutes(1));

        worker.runCycle();
        EvasionAlert first = store.onlyAlert();
        assertEquals(EvasionAlert.DeliveryState.RETRY, first.discordDelivery());
        assertEquals(EvasionAlert.DeliveryState.DELIVERED, first.minecraftDelivery());
        assertEquals(context(7), first.context());
        assertEquals(1, discordCalls.get());
        assertEquals(1, minecraftCalls.get());

        worker.runCycle();
        assertEquals(1, store.alertCount());
        assertEquals(1, discordCalls.get());
        assertEquals(1, minecraftCalls.get());

        clock.advance(Duration.ofSeconds(5));
        worker.runCycle();
        first = store.onlyAlert();
        assertEquals(EvasionAlert.DeliveryState.DELIVERED, first.discordDelivery());
        assertEquals(EvasionAlert.DeliveryState.DELIVERED, first.minecraftDelivery());
        assertEquals(2, discordCalls.get());
        assertEquals(1, minecraftCalls.get());

        store.candidate = candidate(8);
        clock.advance(Duration.ofSeconds(1));
        worker.runCycle();
        assertEquals(2, store.alertCount());
        assertEquals(3, discordCalls.get());
        assertEquals(2, minecraftCalls.get());
        assertTrue(store.alerts.values().stream().allMatch(alert ->
                alert.discordDelivery() == EvasionAlert.DeliveryState.DELIVERED
                        && alert.minecraftDelivery() == EvasionAlert.DeliveryState.DELIVERED));
    }

    private static DiscordInvestigationStore.EvasionCandidate candidate(long revision) {
        return new DiscordInvestigationStore.EvasionCandidate(context(revision));
    }

    private static EvasionAlert.Context context(long revision) {
        return new EvasionAlert.Context(
                SUBJECT, PUNISHMENT, new DiscordUserId("223456789012345680"), DiscordConsequenceType.BAN,
                "Active ban", DiscordPunishmentState.APPLIED, Optional.empty(), PLAYER, Optional.of("LinkedAlt"),
                "survival", revision, EvasionAlert.TriggerType.LINKED_MINECRAFT_ONLINE, NOW.minusSeconds(5)
        );
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeStore implements DiscordInvestigationStore {
        private final Map<String, EvasionAlert> alerts = new LinkedHashMap<>();
        private EvasionCandidate candidate;

        private FakeStore(EvasionCandidate candidate) {
            this.candidate = candidate;
        }

        int alertCount() {
            return alerts.size();
        }

        EvasionAlert onlyAlert() {
            return alerts.values().iterator().next();
        }

        @Override
        public EvasionAlert createEvasionAlert(EvasionAlertDraft draft) {
            EvasionAlert existing = alerts.get(draft.operationKey());
            if (existing != null) {
                return replay(existing);
            }
            EvasionAlert created = new EvasionAlert(
                    draft.alertId(), draft.operationKey(), draft.context(),
                    EvasionAlert.State.OPEN, EvasionAlert.DeliveryState.PENDING, EvasionAlert.DeliveryState.PENDING,
                    0, 0, Optional.empty(), Optional.empty(), Optional.of(draft.now()), Optional.of(draft.now()),
                    draft.now(), draft.now(), 0, false);
            alerts.put(draft.operationKey(), created);
            return created;
        }

        @Override
        public List<EvasionAlert> pendingEvasionAlerts(Instant now, int limit) {
            return alerts.values().stream()
                    .filter(alert -> due(alert.discordDelivery(), alert.discordNextAttemptAt(), now)
                            || due(alert.minecraftDelivery(), alert.minecraftNextAttemptAt(), now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public EvasionAlert updateEvasionDelivery(EvasionDeliveryUpdate update) {
            EvasionAlert current = alerts.values().stream()
                    .filter(alert -> alert.alertId().equals(update.alertId())).findFirst().orElseThrow();
            if (current.revision() != update.expectedRevision()) {
                throw new IllegalStateException("stale alert revision");
            }
            EvasionAlert next = updated(current, update);
            alerts.put(current.operationKey(), next);
            return next;
        }

        private static EvasionAlert updated(EvasionAlert current, EvasionDeliveryUpdate update) {
            return switch (update.channel()) {
                case DISCORD -> updateDiscord(current, update);
                case MINECRAFT -> updateMinecraft(current, update);
            };
        }

        private static EvasionAlert updateDiscord(EvasionAlert current, EvasionDeliveryUpdate update) {
            return new EvasionAlert(
                    current.alertId(), current.operationKey(), current.context(), current.state(),
                    deliveryState(update), current.minecraftDelivery(), current.discordAttempts() + 1,
                    current.minecraftAttempts(), update.errorCode(), current.minecraftErrorCode(), update.nextAttemptAt(),
                    current.minecraftNextAttemptAt(), current.createdAt(), current.updatedAt().plusMillis(1),
                    current.revision() + 1, false
            );
        }

        private static EvasionAlert updateMinecraft(EvasionAlert current, EvasionDeliveryUpdate update) {
            return new EvasionAlert(
                    current.alertId(), current.operationKey(), current.context(), current.state(),
                    current.discordDelivery(), deliveryState(update), current.discordAttempts(),
                    current.minecraftAttempts() + 1, current.discordErrorCode(), update.errorCode(),
                    current.discordNextAttemptAt(), update.nextAttemptAt(), current.createdAt(),
                    current.updatedAt().plusMillis(1), current.revision() + 1, false
            );
        }

        private static EvasionAlert.DeliveryState deliveryState(EvasionDeliveryUpdate update) {
            return update.delivered() ? EvasionAlert.DeliveryState.DELIVERED : EvasionAlert.DeliveryState.RETRY;
        }

        private static EvasionAlert replay(EvasionAlert source) {
            return new EvasionAlert(
                    source.alertId(), source.operationKey(), source.context(), source.state(),
                    source.discordDelivery(), source.minecraftDelivery(), source.discordAttempts(),
                    source.minecraftAttempts(), source.discordErrorCode(), source.minecraftErrorCode(),
                    source.discordNextAttemptAt(), source.minecraftNextAttemptAt(), source.createdAt(), source.updatedAt(),
                    source.revision(), true
            );
        }

        private static boolean due(
                EvasionAlert.DeliveryState state,
                Optional<Instant> nextAttempt,
                Instant now
        ) {
            return state != EvasionAlert.DeliveryState.DELIVERED
                    && nextAttempt.isPresent() && !nextAttempt.orElseThrow().isAfter(now);
        }

        @Override
        public List<EvasionCandidate> evasionCandidates(int limit) {
            return List.of(candidate);
        }

        @Override
        public List<PunishmentObservation> punishmentObservations(int limit) {
            return List.of();
        }

        @Override
        public int closeInactiveCases(Instant inactivityCutoff, Instant now, int limit) {
            return 0;
        }

        @Override
        public int purgeEligibleEvidence(Instant now, int limit) {
            return 0;
        }

        @Override
        public Optional<EvasionAlert> findEvasionAlert(UUID alertId) {
            return alerts.values().stream().filter(alert -> alert.alertId().equals(alertId)).findFirst();
        }

        @Override
        public EvasionAlert resolveEvasionAlert(UUID alertId, long expectedRevision, Instant now) {
            throw unsupported();
        }

        @Override
        public InvestigationCase ensurePunishmentCase(PunishmentCaseDraft draft) {
            throw unsupported();
        }

        @Override
        public InvestigationCase createInvestigationCase(InvestigationCaseDraft draft) {
            throw unsupported();
        }

        @Override
        public Optional<InvestigationCase> findCase(CaseId caseId) {
            throw unsupported();
        }

        @Override
        public InvestigationCase touchCase(CaseActivity activity) {
            throw unsupported();
        }

        @Override
        public InvestigationNote createNote(NoteDraft draft) {
            throw unsupported();
        }

        @Override
        public InvestigationNote editNote(NoteEdit edit) {
            throw unsupported();
        }

        @Override
        public Optional<InvestigationNote> findNote(UUID noteId) {
            throw unsupported();
        }

        @Override
        public List<InvestigationNote.Version> noteHistory(UUID noteId, int limit) {
            throw unsupported();
        }

        @Override
        public InvestigationEvidence.Stored captureEvidence(InvestigationEvidence.Capture capture) {
            throw unsupported();
        }

        @Override
        public InvestigationEvidence.Stored recordEvidenceEdit(InvestigationEvidence.Edit edit) {
            throw unsupported();
        }

        @Override
        public int captureMoreContext(InvestigationEvidence.ContextBatch batch) {
            throw unsupported();
        }

        @Override
        public Optional<InvestigationEvidence.Stored> findEvidenceByMessage(
                String guildId,
                String channelId,
                String messageId
        ) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by this worker test");
        }
    }
}
