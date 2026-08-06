package net.enthusia.staff.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.junit.jupiter.api.Test;

class PlayerPlatformDetectionTest {
    @Test
    void availableFloodgateIdentifiesBedrock() {
        assertEquals(
                PlayerPlatform.BEDROCK,
                PlayerPlatformDetection.resolve(
                        IntegrationAvailability.AVAILABLE,
                        true,
                        IntegrationAvailability.AVAILABLE
                )
        );
    }

    @Test
    void availableFloodgateProvesJavaWhenPlayerIsNotFloodgateManaged() {
        assertEquals(
                PlayerPlatform.JAVA,
                PlayerPlatformDetection.resolve(
                        IntegrationAvailability.AVAILABLE,
                        false,
                        IntegrationAvailability.AVAILABLE
                )
        );
    }

    @Test
    void missingGeyserProvesJavaWithoutFloodgate() {
        assertEquals(
                PlayerPlatform.JAVA,
                PlayerPlatformDetection.resolve(
                        IntegrationAvailability.NOT_INSTALLED,
                        false,
                        IntegrationAvailability.NOT_INSTALLED
                )
        );
    }

    @Test
    void installedGeyserWithUnavailableFloodgateRemainsUnknown() {
        assertEquals(
                PlayerPlatform.UNKNOWN,
                PlayerPlatformDetection.resolve(
                        IntegrationAvailability.INCOMPATIBLE,
                        false,
                        IntegrationAvailability.AVAILABLE
                )
        );
    }

    @Test
    void brokenFloodgateWithoutLocalGeyserRemainsUnknown() {
        assertEquals(
                PlayerPlatform.UNKNOWN,
                PlayerPlatformDetection.resolve(
                        IntegrationAvailability.UNAVAILABLE,
                        false,
                        IntegrationAvailability.NOT_INSTALLED
                )
        );
    }

    @Test
    void inconsistentBedrockFlagWithoutAvailableFloodgateRemainsUnknown() {
        assertEquals(
                PlayerPlatform.UNKNOWN,
                PlayerPlatformDetection.resolve(
                        IntegrationAvailability.UNAVAILABLE,
                        true,
                        IntegrationAvailability.NOT_INSTALLED
                )
        );
    }
}
