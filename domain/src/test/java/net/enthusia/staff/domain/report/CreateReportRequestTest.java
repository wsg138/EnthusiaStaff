package net.enthusia.staff.domain.report;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.player.PlayerPlatform;
import org.junit.jupiter.api.Test;

final class CreateReportRequestTest {
    @Test
    void clientEvidenceMustBelongToTheTarget() {
        UUID reporter = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> new CreateReportRequest(
                new IdempotencyKey("report:test"),
                reporter,
                target,
                "chat.abuse",
                "Evidence description",
                "SMP",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                now,
                List.of(),
                List.of(),
                Optional.of(snapshot(UUID.randomUUID(), now))
        ));
    }

    private static ClientEvidenceSnapshot snapshot(UUID playerId, Instant capturedAt) {
        return new ClientEvidenceSnapshot(
                playerId,
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
