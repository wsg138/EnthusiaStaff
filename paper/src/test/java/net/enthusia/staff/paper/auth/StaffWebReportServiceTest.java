package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportEvidencePurgeResult;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.report.ReportSummary;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import org.junit.jupiter.api.Test;

final class StaffWebReportServiceTest {
    private static final UUID ACTOR = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TARGET = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID REPORT = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant NOW = Instant.parse("2026-10-10T18:00:00Z");

    @Test
    void disabledModeNeverConsultsLiveSessionOrReportStorage() {
        var service = service(OperationalMode.SHADOW_MIGRATION, StaffRank.ADMIN,
                null, null);
        assertThrows(SecurityException.class,
                () -> service.execute("claim", request("Claimed for investigation")));
    }

    @Test
    void currentStaffHierarchyAndDutyAreEnforcedBeforeReportMutation() {
        var store = new MemoryStore();
        var noDuty = service(OperationalMode.ACTIVE, StaffRank.MOD, store, session(false));
        assertThrows(SecurityException.class,
                () -> noDuty.execute("claim", request("Claimed for investigation")));
        var restricted = service(OperationalMode.ACTIVE, StaffRank.MOD,
                store, session());
        // A moderator cannot resolve a report against an Administrator.
        assertThrows(SecurityException.class,
                () -> new StaffWebReportService(
                        new StaffWebReportService.Dependencies(Clock.systemUTC(),
                                () -> OperationalMode.ACTIVE, () -> store, StaffWebReportServiceTest::session),
                        id -> new Actor(id, "Moderator", StaffRank.MOD),
                        target -> Optional.of(StaffRank.ADMIN)).execute("claim", request("Review")));
        assertEquals(0, store.applied);
    }

    @Test
    void reportClaimCommitsWithAuthoritativeRevisionAndStableIdempotencyKey() {
        MemoryStore store = new MemoryStore();
        StaffWebReportService service = service(OperationalMode.ACTIVE,
                StaffRank.ADMIN, store, session());
        var decision = service.execute("claim", request(""));
        assertEquals("CLAIMED", decision.state());
        assertEquals(4, decision.revision());
        assertEquals(1, store.applied);
        assertEquals(ReportAction.CLAIM, store.last.get().action());
        assertEquals(3L, store.last.get().expectedRevision());
        assertTrue(store.last.get().idempotencyKey().value().startsWith("discord-report:"));
    }

    @Test
    void missingResolutionNoteOrMalformedOperationIdCannotChangeReport() {
        MemoryStore store = new MemoryStore();
        var service = service(OperationalMode.ACTIVE, StaffRank.ADMIN, store, session());
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("close", request("")));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("close",
                        new StaffWebReportService.Request(ACTOR, REPORT, 3, "Resolved", "bad")));
        assertThrows(IllegalArgumentException.class,
                () -> service.execute("arbitrary", request("Resolved")));
        assertEquals(0, store.applied);
    }

    private static StaffWebReportService.Request request(String note) {
        return new StaffWebReportService.Request(ACTOR, REPORT, 3, note, "123456789012345678");
    }

    private static StaffWebReportService service(
            OperationalMode mode, StaffRank rank, ReportStore reports, StaffSessionStore sessions) {
        return new StaffWebReportService(
                new StaffWebReportService.Dependencies(Clock.systemUTC(),
                        () -> mode, () -> reports, () -> sessions),
                id -> new Actor(id, "Staff", rank),
                ignored -> Optional.empty());
    }

    private static StaffSessionStore session() {
        return session(true);
    }

    private static StaffSessionStore session(boolean onDuty) {
        return new StaffSessionStore() {
            @Override
            public StaffSessionSnapshot begin(UUID staffId, String serverId,
                    int schemaVersion, String checksum, byte[] snapshot, Instant now) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Optional<StaffSessionSnapshot> active(UUID staffId) {
                if (!onDuty) {
                    return Optional.empty();
                }
                return Optional.of(new StaffSessionSnapshot(UUID.randomUUID(), staffId, "SMP",
                        StaffSessionState.ACTIVE, false, 1, "0".repeat(64), new byte[] {1}, NOW, 1));
            }
            @Override
            public Optional<StaffSessionSnapshot> beginExit(UUID staffId, Instant now) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean completeExit(UUID sessionId, String checksum, Instant now) {
                throw new UnsupportedOperationException();
            }
            @Override
            public void recoveryRequired(UUID sessionId, String reason, Instant now) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean setVanish(UUID staffId, boolean vanished, Instant now) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class MemoryStore implements ReportStore {
        private final AtomicReference<ReportStateChangeRequest> last = new AtomicReference<>();
        private int applied;

        @Override
        public ReportSubmissionResult submit(CreateReportRequest request) {
            throw new UnsupportedOperationException();
        }
        @Override
        public List<ReportSummary> list(ReportQueue queue, UUID actorId, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override
        public List<ReportSummary> listActiveForTarget(UUID targetId, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Optional<ReportDetails> details(UUID id) {
            var summary = new ReportSummary(REPORT, ACTOR, TARGET, "spam.low-level",
                    ReportState.OPEN, Optional.empty(), "SMP", NOW, NOW, 3);
            return Optional.of(new ReportDetails(summary, "Spam", Optional.empty(),
                    Optional.empty(), Optional.empty(), List.of(), List.of(), List.of()));
        }
        @Override
        public ReportStateChangeResult changeState(ReportStateChangeRequest request) {
            applied++;
            last.set(request);
            return new ReportStateChangeResult.Applied(ReportState.CLAIMED, 4, false);
        }
        @Override
        public ReportEvidencePurgeResult purgeExpiredEvidence(Instant now, int batchLimit) {
            throw new UnsupportedOperationException();
        }
    }
}
