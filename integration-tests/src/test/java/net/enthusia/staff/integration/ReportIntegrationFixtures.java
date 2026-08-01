package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.report.ReportSubmissionResult;

final class ReportIntegrationFixtures {
    static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final String REASON_ID = "chat.abuse";
    private static final String SERVER_ID = "paper-report-test";

    private ReportIntegrationFixtures() {
    }

    static CreateReportRequest request(
            UUID reporterId,
            UUID targetId,
            String idempotencyKey,
            Instant createdAt,
            String evidenceLabel
    ) {
        return new CreateReportRequest(
                new IdempotencyKey(idempotencyKey),
                reporterId,
                targetId,
                REASON_ID,
                "Report description " + evidenceLabel,
                SERVER_ID,
                Optional.of("minecraft:overworld"),
                Optional.of("1,64,1"),
                Optional.of("2,64,2"),
                createdAt,
                List.of(new CreateReportRequest.ChatContextMessage(
                        reporterId,
                        "Reporter",
                        "Public evidence " + evidenceLabel,
                        createdAt
                )),
                List.of(new CreateReportRequest.PrivateMessageContextMessage(
                        reporterId,
                        "Reporter",
                        targetId,
                        "Target",
                        "Private evidence " + evidenceLabel,
                        createdAt
                )),
                Optional.empty()
        );
    }

    static CreateReportRequest requestWithEvidence(
            UUID reporterId,
            UUID targetId,
            String idempotencyKey,
            Instant createdAt,
            String evidenceLabel
    ) {
        CreateReportRequest base = request(reporterId, targetId, idempotencyKey, createdAt, evidenceLabel);
        return new CreateReportRequest(
                base.idempotencyKey(),
                base.reporterId(),
                base.targetId(),
                base.reasonId(),
                base.description(),
                base.serverId(),
                base.worldId(),
                base.reporterCoordinates(),
                base.targetCoordinates(),
                base.createdAt(),
                base.publicChatContext(),
                base.privateMessageContext(),
                Optional.of(clientEvidence(targetId, createdAt))
        );
    }

    static ReportStateChangeRequest change(UUID reportId, UUID actorId, String key) {
        return stateChange(reportId, actorId, ReportAction.CLAIM, 0L, key);
    }

    static ReportStateChangeRequest stateChange(
            UUID reportId,
            UUID actorId,
            ReportAction action,
            long revision,
            String key
    ) {
        return new ReportStateChangeRequest(
                reportId,
                actorId,
                action,
                revision,
                "Investigating report",
                new IdempotencyKey(key),
                NOW.plusSeconds(revision + 1)
        );
    }

    static ReportSubmissionResult.Accepted accepted(ReportSubmissionResult result) {
        return assertInstanceOf(ReportSubmissionResult.Accepted.class, result);
    }

    static ReportStateChangeResult.Applied apply(
            ReportStore store,
            ReportStateChangeRequest request
    ) {
        return assertInstanceOf(ReportStateChangeResult.Applied.class, store.changeState(request));
    }

    static ReportStateChangeResult.Rejected reject(
            ReportStore store,
            ReportStateChangeRequest request
    ) {
        return assertInstanceOf(ReportStateChangeResult.Rejected.class, store.changeState(request));
    }

    static void assertQueueContains(
            ReportStore store,
            ReportQueue queue,
            UUID actorId,
            UUID reportId
    ) {
        assertTrue(store.list(queue, actorId, 100).stream()
                .anyMatch(report -> report.reportId().equals(reportId)));
    }

    static void assertQueueExcludes(
            ReportStore store,
            ReportQueue queue,
            UUID actorId,
            UUID reportId
    ) {
        assertTrue(store.list(queue, actorId, 100).stream()
                .noneMatch(report -> report.reportId().equals(reportId)));
    }

    private static ClientEvidenceSnapshot clientEvidence(UUID targetId, Instant capturedAt) {
        return new ClientEvidenceSnapshot(
                targetId,
                capturedAt,
                PlayerPlatform.JAVA,
                Optional.of(774),
                Optional.of("1.21.11"),
                Optional.of("vanilla"),
                IntegrationAvailability.AVAILABLE,
                Optional.of("5.10.0"),
                IntegrationAvailability.AVAILABLE,
                false,
                Optional.empty(),
                Optional.empty(),
                IntegrationAvailability.NOT_INSTALLED,
                IntegrationAvailability.NOT_INSTALLED,
                Optional.empty(),
                IntegrationAvailability.NOT_INSTALLED,
                Optional.empty()
        );
    }
}
