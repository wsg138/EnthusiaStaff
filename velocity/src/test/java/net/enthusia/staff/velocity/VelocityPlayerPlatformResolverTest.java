package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.player.PlayerPlatform;
import org.junit.jupiter.api.Test;

class VelocityPlayerPlatformResolverTest {
    private static final UUID PLAYER_ID = UUID.fromString("e4e1556c-8f75-4c35-8878-5ae71840ee73");

    @Test
    void availableFloodgateSeparatesBedrockAndJava() {
        VelocityPlayerPlatformResolver bedrock = resolver(
                IntegrationAvailability.AVAILABLE,
                true,
                IntegrationAvailability.AVAILABLE,
                new ArrayList<>()
        );
        VelocityPlayerPlatformResolver java = resolver(
                IntegrationAvailability.AVAILABLE,
                false,
                IntegrationAvailability.AVAILABLE,
                new ArrayList<>()
        );

        assertEquals(PlayerPlatform.BEDROCK, bedrock.resolve(PLAYER_ID));
        assertEquals(PlayerPlatform.JAVA, java.resolve(PLAYER_ID));
    }

    @Test
    void absentGeyserAndFloodgateProvesJava() {
        VelocityPlayerPlatformResolver resolver = resolver(
                IntegrationAvailability.NOT_INSTALLED,
                false,
                IntegrationAvailability.NOT_INSTALLED,
                new ArrayList<>()
        );

        assertEquals(PlayerPlatform.JAVA, resolver.resolve(PLAYER_ID));
    }

    @Test
    void geyserWithoutUsableFloodgateRemainsUnknown() {
        VelocityPlayerPlatformResolver resolver = resolver(
                IntegrationAvailability.INCOMPATIBLE,
                false,
                IntegrationAvailability.AVAILABLE,
                new ArrayList<>()
        );

        assertEquals(PlayerPlatform.UNKNOWN, resolver.resolve(PLAYER_ID));
    }

    @Test
    void queryFailuresRemainUnknownAndWarnOnlyOnce() {
        List<String> warnings = new ArrayList<>();
        VelocityPlayerPlatformResolver.FloodgateProbe probe = new VelocityPlayerPlatformResolver.FloodgateProbe() {
            @Override
            public IntegrationAvailability availability() {
                return IntegrationAvailability.AVAILABLE;
            }

            @Override
            public boolean isFloodgatePlayer(UUID playerId) {
                throw new IllegalStateException("provider unavailable");
            }
        };
        VelocityPlayerPlatformResolver resolver = new VelocityPlayerPlatformResolver(
                probe,
                IntegrationAvailability.AVAILABLE,
                warnings::add
        );

        assertEquals(PlayerPlatform.UNKNOWN, resolver.resolve(PLAYER_ID));
        assertEquals(PlayerPlatform.UNKNOWN, resolver.resolve(PLAYER_ID));
        assertEquals(1, warnings.size());
    }

    private static VelocityPlayerPlatformResolver resolver(
            IntegrationAvailability availability,
            boolean bedrock,
            IntegrationAvailability geyser,
            List<String> warnings
    ) {
        return new VelocityPlayerPlatformResolver(
                new VelocityPlayerPlatformResolver.FloodgateProbe() {
                    @Override
                    public IntegrationAvailability availability() {
                        return availability;
                    }

                    @Override
                    public boolean isFloodgatePlayer(UUID playerId) {
                        return bedrock;
                    }
                },
                geyser,
                warnings::add
        );
    }
}
