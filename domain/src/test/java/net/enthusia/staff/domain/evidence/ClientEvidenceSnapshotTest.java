package net.enthusia.staff.domain.evidence;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.player.PlayerPlatform;
import org.junit.jupiter.api.Test;

final class ClientEvidenceSnapshotTest {
    @Test
    void platformMustMatchTheFloodgateSignal() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                PlayerPlatform.JAVA,
                true,
                IntegrationAvailability.AVAILABLE,
                Optional.empty()
        ));
    }

    @Test
    void handshakeRequiresAnAvailableIntegration() {
        AutoClickerHandshakeEvidence handshake = new AutoClickerHandshakeEvidence(
                "1.0.0",
                "fabric",
                "1.21.11",
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertThrows(IllegalArgumentException.class, () -> snapshot(
                PlayerPlatform.JAVA,
                false,
                IntegrationAvailability.INCOMPATIBLE,
                Optional.of(handshake)
        ));
    }

    private static ClientEvidenceSnapshot snapshot(
            PlayerPlatform platform,
            boolean floodgatePlayer,
            IntegrationAvailability autoClicker,
            Optional<AutoClickerHandshakeEvidence> handshake
    ) {
        return new ClientEvidenceSnapshot(
                UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:01Z"),
                platform,
                Optional.of(774),
                Optional.of("1.21.11"),
                Optional.of("vanilla"),
                IntegrationAvailability.AVAILABLE,
                Optional.of("5.10.0"),
                IntegrationAvailability.AVAILABLE,
                floodgatePlayer,
                floodgatePlayer ? Optional.of("1.21.100") : Optional.empty(),
                floodgatePlayer ? Optional.of("WINDOWS_10") : Optional.empty(),
                IntegrationAvailability.AVAILABLE,
                autoClicker,
                handshake,
                IntegrationAvailability.UNAVAILABLE,
                Optional.empty()
        );
    }
}
