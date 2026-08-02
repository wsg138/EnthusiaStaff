package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRequestIntegrationTest extends PunishmentRequestMariaDbSupport {
    @Test
    void pendingRequestSurvivesRestartAndDuplicateSubmissionReplays() throws SQLException {
        PunishmentApprovalRequest initial = request(
                "restart",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestResult.Submitted submitted = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    runtime.punishmentRequestStore().submit(initial)
            );
            assertFalse(submitted.replayed());
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            assertEquals(initial, store.find(initial.requestId()).orElseThrow());
            PunishmentRequestResult.Submitted replay = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    store.submit(initial)
            );
            assertTrue(replay.replayed());
            assertEquals(initial.requestId(), replay.request().requestId());
        }

        assertEquals(1, eventCount(initial.requestId(), "SUBMITTED"));
    }

    @Test
    void conflictingSubmissionsAreRejectedWithoutReplacingFrozenProposal() {
        UUID target = identifier("conflict-target");
        PunishmentApprovalRequest initial = request(
                "conflict-initial",
                "punishment-request-integration:conflict-shared",
                target,
                "test.conflict",
                DEVELOPER,
                StaffRank.MOD,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest idempotencyConflict = request(
                "conflict-idempotency",
                initial.submissionKey().value(),
                target,
                "test.conflict.changed",
                DEVELOPER,
                StaffRank.MOD,
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest duplicatePending = request(
                "conflict-duplicate",
                "punishment-request-integration:conflict-other",
                target,
                "test.conflict",
                DEVELOPER,
                StaffRank.MOD,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            assertInstanceOf(PunishmentRequestResult.Submitted.class, store.submit(initial));
            PunishmentRequestResult.Rejected keyConflict = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.submit(idempotencyConflict)
            );
            assertEquals("IDEMPOTENCY_CONFLICT", keyConflict.code());
            PunishmentRequestResult.Rejected duplicate = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.submit(duplicatePending)
            );
            assertEquals("DUPLICATE_PENDING", duplicate.code());
            assertEquals(initial, store.find(initial.requestId()).orElseThrow());
            assertFalse(store.find(idempotencyConflict.requestId()).isPresent());
            assertFalse(store.find(duplicatePending.requestId()).isPresent());
        }
    }

    @Test
    void staleFenceCannotApproveAndCurrentFenceCommitsAtomically() throws SQLException {
        PunishmentApprovalRequest pending = request(
                "approval",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        Instant approvalTime = NOW.plus(Duration.ofHours(2));
        CaseId caseId = new CaseId("A000000000000001");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(pending);
            PunishmentApprovalLease first = acquire(
                    store,
                    pending,
                    MOD,
                    approvalTime.minus(Duration.ofMinutes(3))
            );
            PunishmentApprovalLease current = acquire(store, pending, MOD, approvalTime.minusSeconds(1));
            assertTrue(current.fenceToken() > first.fenceToken());

            PunishmentRequestResult.Rejected stale = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(first, MOD, caseId, approvalTime)
            );
            assertEquals("STALE_LEASE", stale.code());
            assertEquals(0, countCases(caseId));

            PunishmentRequestResult.Approved approved = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(current, MOD, caseId, approvalTime)
            );
            assertFalse(approved.replayed());
            PunishmentRequestResult.Approved replay = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(current, MOD, new CaseId("A000000000000099"), approvalTime.plusSeconds(1))
            );
            assertTrue(replay.replayed());
            assertEquals(caseId, replay.caseId());
        }

        assertEquals(approvalTime, caseIssuedAt(caseId));
        assertEquals(approvalTime.plus(Duration.ofDays(7)), sanctionExpiration(caseId));
        assertEquals(0, leaseCount(pending.requestId()));
        assertEquals(1, eventCount(pending.requestId(), EVENT_APPROVED));
    }

    @Test
    void lostLeaseFailsClosedWithoutCreatingCase() throws SQLException {
        PunishmentApprovalRequest pending = request(
                "lease-loss",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000003");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(pending);
            PunishmentApprovalLease lease = acquire(store, pending, MOD, NOW);
            deleteLease(pending.requestId());
            PunishmentRequestResult.Rejected rejected = assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(lease, MOD, caseId, NOW.plusSeconds(10))
            );
            assertEquals("STALE_LEASE", rejected.code());
            assertEquals(PunishmentRequestStatus.PENDING, store.find(pending.requestId()).orElseThrow().status());
        }

        assertEquals(0, countCases(caseId));
        assertEquals(0, eventCount(pending.requestId(), EVENT_APPROVED));
    }

    @Test
    void denialAndExpirationRemainDurableWithoutCreatingCases() throws SQLException {
        PunishmentApprovalRequest denied = request(
                "denial",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest expiring = request(
                "expiration",
                thirtyDayBan(),
                NOW.plus(Duration.ofHours(1))
        );

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(denied);
            store.submit(expiring);
            PunishmentApprovalLease lease = acquire(store, denied, MOD, NOW);
            PunishmentRequestResult.Denied result = assertInstanceOf(
                    PunishmentRequestResult.Denied.class,
                    store.deny(lease, MOD, "Evidence did not support the requested result", NOW.plusSeconds(5))
            );
            assertFalse(result.replayed());
            PunishmentRequestResult.Denied replay = assertInstanceOf(
                    PunishmentRequestResult.Denied.class,
                    store.deny(lease, MOD, "Repeated denial", NOW.plusSeconds(6))
            );
            assertTrue(replay.replayed());
            assertEquals(1, store.expire(NOW.plus(Duration.ofHours(2))));
            assertEquals(PunishmentRequestStatus.DENIED, store.find(denied.requestId()).orElseThrow().status());
            assertEquals(PunishmentRequestStatus.EXPIRED, store.find(expiring.requestId()).orElseThrow().status());
        }

        assertEquals(0, countCasesForRequest(denied.requestId()));
        assertEquals(0, countCasesForRequest(expiring.requestId()));
        assertEquals(0, leaseCount(denied.requestId()));
        assertEquals(0, leaseCount(expiring.requestId()));
        assertEquals(1, eventCount(denied.requestId(), "DENIED"));
        assertEquals(1, eventCount(expiring.requestId(), "EXPIRED"));
    }

    @Test
    void independentExactPunishmentFulfillsOnlyMatchingPendingRequest() throws SQLException {
        UUID target = identifier("external-target");
        String reason = "test.external";
        PunishmentApprovalRequest matching = request(
                "external-match",
                target,
                reason,
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        PunishmentApprovalRequest differentDuration = request(
                "external-different",
                target,
                reason,
                thirtyDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000002");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore requests = runtime.punishmentRequestStore();
            requests.submit(matching);
            requests.submit(differentDuration);
            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    target,
                    reason,
                    sevenDayBan(),
                    NOW.plus(Duration.ofHours(3))
            ));
            PunishmentApprovalRequest fulfilled = requests.find(matching.requestId()).orElseThrow();
            assertEquals(PunishmentRequestStatus.FULFILLED_EXTERNALLY, fulfilled.status());
            assertEquals(caseId, fulfilled.resultingCaseId());
            assertEquals(
                    PunishmentRequestStatus.PENDING,
                    requests.find(differentDuration.requestId()).orElseThrow().status()
            );
        }

        assertEquals(1, eventCount(matching.requestId(), "FULFILLED_EXTERNALLY"));
        assertEquals(0, eventCount(differentDuration.requestId(), "FULFILLED_EXTERNALLY"));
    }

    @Test
    void serviceRejectsHelperDeveloperAndSelfApproval() {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture permanent = serviceFixture(
                    runtime,
                    "test.authority.service.permanent",
                    StaffRank.MOD,
                    SanctionLength.permanent()
            );
            PunishmentRequestResult.Submitted helperSubmission = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    permanent.requests().submit(
                            serviceRequest("helper-permanent", HELPER, "test.authority.service.permanent"),
                            OperationalMode.ACTIVE
                    )
            );
            assertEquals(CODE_FORBIDDEN, assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    permanent.requests().acquire(helperSubmission.request().requestId(), HELPER)
            ).code());
            assertEquals(CODE_FORBIDDEN, assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    permanent.requests().acquire(helperSubmission.request().requestId(), DEVELOPER)
            ).code());

            PunishmentRequestResult.Submitted developerSubmission = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    permanent.requests().submit(
                            serviceRequest("developer-proposal", DEVELOPER, "test.authority.service.permanent"),
                            OperationalMode.ACTIVE
                    )
            );
            Actor promotedRequester = new Actor(DEVELOPER.id(), "Promoted requester", StaffRank.MOD);
            assertEquals("SELF_APPROVAL_FORBIDDEN", assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    permanent.requests().acquire(developerSubmission.request().requestId(), promotedRequester)
            ).code());

            ServiceFixture temporary = serviceFixture(
                    runtime,
                    "test.authority.service.temporary",
                    StaffRank.MOD,
                    SanctionLength.temporary(Duration.ofHours(6))
            );
            assertEquals("APPROVAL_NOT_REQUIRED", assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    temporary.requests().submit(
                            serviceRequest("helper-temporary", HELPER, "test.authority.service.temporary"),
                            OperationalMode.ACTIVE
                    )
            ).code());
        }
    }

    @Test
    void storeRejectsHelperDeveloperAndSelfApprovalAtomically() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture fixture = serviceFixture(
                    runtime,
                    "test.authority.store",
                    StaffRank.MOD,
                    SanctionLength.permanent()
            );
            PunishmentApprovalRequest helperRequest = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("store-helper", HELPER, "test.authority.store"),
                            OperationalMode.ACTIVE
                    )
            ).request();
            PunishmentApprovalRequest developerRequest = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("store-developer", DEVELOPER, "test.authority.store"),
                            OperationalMode.ACTIVE
                    )
            ).request();
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            CaseId helperCase = new CaseId("A000000000000005");
            CaseId developerCase = new CaseId("A000000000000006");
            CaseId selfCase = new CaseId("A000000000000007");

            PunishmentApprovalLease helperLease = acquire(store, helperRequest, HELPER, NOW);
            assertEquals(CODE_FORBIDDEN, assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(helperLease, HELPER, helperCase, NOW.plusSeconds(1))
            ).code());
            PunishmentApprovalLease developerLease = acquire(store, developerRequest, DEVELOPER, NOW);
            assertEquals(CODE_FORBIDDEN, assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(developerLease, DEVELOPER, developerCase, NOW.plusSeconds(1))
            ).code());
            Actor promotedRequester = new Actor(DEVELOPER.id(), "Promoted requester", StaffRank.MOD);
            assertEquals("SELF_APPROVAL_FORBIDDEN", assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(developerLease, promotedRequester, selfCase, NOW.plusSeconds(2))
            ).code());
            assertEquals(List.of(0, 0, 0), List.of(
                    countCases(helperCase),
                    countCases(developerCase),
                    countCases(selfCase)
            ));
        }
    }

    @Test
    void reasonRankMinimumIsEnforcedByServiceAndStore() throws SQLException {
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture fixture = serviceFixture(
                    runtime,
                    "test.rank.admin",
                    StaffRank.ADMIN,
                    SanctionLength.permanent()
            );
            PunishmentApprovalRequest serviceRequest = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("admin-reason", DEVELOPER, "test.rank.admin"),
                            OperationalMode.ACTIVE
                    )
            ).request();
            assertEquals("APPROVER_RANK_REQUIRED", assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    fixture.requests().acquire(serviceRequest.requestId(), MOD)
            ).code());
            assertInstanceOf(
                    PunishmentRequestResult.Leased.class,
                    fixture.requests().acquire(serviceRequest.requestId(), ADMIN)
            );

            PunishmentApprovalRequest storedRequest = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("admin-reason-store", DEVELOPER, "test.rank.admin"),
                            OperationalMode.ACTIVE
                    )
            ).request();
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            PunishmentApprovalLease modLease = acquire(store, storedRequest, MOD, NOW);
            CaseId rejectedCaseId = new CaseId("A000000000000008");
            assertEquals("APPROVER_RANK_REQUIRED", assertInstanceOf(
                    PunishmentRequestResult.Rejected.class,
                    store.approve(modLease, MOD, rejectedCaseId, NOW.plusSeconds(1))
            ).code());
            assertEquals(0, countCases(rejectedCaseId));
        }
    }

    @Test
    void reviewedProposalRemainsFrozenAfterPolicyReplacement() throws SQLException {
        String reason = "test.frozen";
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            ServiceFixture fixture = serviceFixture(
                    runtime,
                    reason,
                    StaffRank.MOD,
                    SanctionLength.temporary(Duration.ofDays(7))
            );
            PunishmentApprovalRequest submitted = assertInstanceOf(
                    PunishmentRequestResult.Submitted.class,
                    fixture.requests().submit(
                            serviceRequest("frozen", DEVELOPER, reason),
                            OperationalMode.ACTIVE
                    )
            ).request();
            fixture.policies().replace(
                    "v2",
                    List.of(policy(reason, StaffRank.MOD, SanctionLength.temporary(Duration.ofDays(30))))
            );
            PunishmentApprovalLease lease = assertInstanceOf(
                    PunishmentRequestResult.Leased.class,
                    fixture.requests().acquire(submitted.requestId(), MOD)
            ).lease();
            PunishmentRequestResult.Approved approved = assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    fixture.requests().approve(lease, MOD)
            );
            assertEquals("v1", approved.request().proposal().configurationVersion());
            assertEquals(NOW.plus(Duration.ofDays(7)), sanctionExpiration(approved.caseId()));
        }
    }

    @Test
    void approvalAndRequestResolutionRollbackTogetherAndRetrySafely() throws SQLException {
        PunishmentApprovalRequest pending = request(
                "atomic-rollback",
                sevenDayBan(),
                NOW.plus(Duration.ofDays(7))
        );
        CaseId caseId = new CaseId("A000000000000004");

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            PunishmentRequestStore store = runtime.punishmentRequestStore();
            store.submit(pending);
            PunishmentApprovalLease lease = acquire(store, pending, MOD, NOW);
            installApprovalFailureTrigger();
            try {
                assertThrows(
                        ModerationPersistenceException.class,
                        () -> store.approve(lease, MOD, caseId, NOW.plusSeconds(10))
                );
            } finally {
                dropApprovalFailureTrigger();
            }
            assertEquals(0, countCases(caseId));
            assertEquals(PunishmentRequestStatus.PENDING, store.find(pending.requestId()).orElseThrow().status());
            assertEquals(0, eventCount(pending.requestId(), EVENT_APPROVED));
            assertEquals(1, leaseCount(pending.requestId()));
            assertFalse(assertInstanceOf(
                    PunishmentRequestResult.Approved.class,
                    store.approve(lease, MOD, caseId, NOW.plusSeconds(20))
            ).replayed());
        }

        assertEquals(1, countCases(caseId));
        assertEquals(1, eventCount(pending.requestId(), EVENT_APPROVED));
    }
}
