package net.enthusia.staff.domain.evidence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.player.PlayerPlatform;

public record ClientEvidenceSnapshot(
        UUID playerId,
        Instant capturedAt,
        PlayerPlatform platform,
        Optional<Integer> protocolVersion,
        Optional<String> minecraftVersion,
        Optional<String> reportedBrand,
        IntegrationAvailability viaVersion,
        Optional<String> viaVersionPluginVersion,
        IntegrationAvailability floodgate,
        boolean floodgatePlayer,
        Optional<String> bedrockVersion,
        Optional<String> bedrockDevice,
        IntegrationAvailability geyser,
        IntegrationAvailability autoClicker,
        Optional<AutoClickerHandshakeEvidence> autoClickerHandshake,
        IntegrationAvailability polar,
        Optional<String> polarMetadata
) {
    public ClientEvidenceSnapshot {
        if (playerId == null || capturedAt == null || platform == null || protocolVersion == null
                || minecraftVersion == null || reportedBrand == null || viaVersion == null
                || viaVersionPluginVersion == null || floodgate == null || bedrockVersion == null
                || bedrockDevice == null || geyser == null || autoClicker == null
                || autoClickerHandshake == null || polar == null || polarMetadata == null) {
            throw new IllegalArgumentException("client evidence fields must be present");
        }
        protocolVersion.ifPresent(protocol -> {
            if (protocol < 0) {
                throw new IllegalArgumentException("protocol version cannot be negative");
            }
        });
        validateText(minecraftVersion, 64, "minecraftVersion");
        validateText(reportedBrand, 255, "reportedBrand");
        validateText(viaVersionPluginVersion, 64, "viaVersionPluginVersion");
        validateText(bedrockVersion, 64, "bedrockVersion");
        validateText(bedrockDevice, 64, "bedrockDevice");
        validateText(polarMetadata, 1_000, "polarMetadata");
        if (floodgatePlayer != (platform == PlayerPlatform.BEDROCK)) {
            throw new IllegalArgumentException("Bedrock platform must match the Floodgate player signal");
        }
        if (autoClickerHandshake.isPresent() && autoClicker != IntegrationAvailability.AVAILABLE) {
            throw new IllegalArgumentException("AutoClicker evidence requires an available integration");
        }
        if (autoClickerHandshake.stream().anyMatch(
                evidence -> evidence.receivedAt().isAfter(capturedAt))) {
            throw new IllegalArgumentException("AutoClicker evidence cannot be newer than its snapshot");
        }
    }

    private static void validateText(Optional<String> value, int maximumLength, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(text -> {
            if (text.isBlank() || text.length() > maximumLength) {
                throw new IllegalArgumentException(name + " is invalid");
            }
        });
    }
}
