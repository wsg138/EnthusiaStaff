package net.enthusia.staff.domain.player;

import java.util.Objects;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;

/**
 * Converts runtime provider observations into the only platform values that may be persisted.
 */
public final class PlayerPlatformDetection {
    private PlayerPlatformDetection() {
    }

    public static PlayerPlatform resolve(
            IntegrationAvailability floodgate,
            boolean floodgatePlayer,
            IntegrationAvailability geyser
    ) {
        Objects.requireNonNull(floodgate, "floodgate");
        Objects.requireNonNull(geyser, "geyser");
        if (floodgate == IntegrationAvailability.AVAILABLE) {
            return floodgatePlayer ? PlayerPlatform.BEDROCK : PlayerPlatform.JAVA;
        }
        if (floodgatePlayer) {
            return PlayerPlatform.UNKNOWN;
        }
        return floodgate == IntegrationAvailability.NOT_INSTALLED
                && geyser == IntegrationAvailability.NOT_INSTALLED
                ? PlayerPlatform.JAVA
                : PlayerPlatform.UNKNOWN;
    }
}
