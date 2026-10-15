package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffModerationConfigFileConsoleTest {
    @TempDir
    Path directory;

    @Test
    void fileBackedConsoleChannelMapsToRuntimeAllowlist() throws Exception {
        Path file = directory.resolve("moderation.properties");
        Files.writeString(file, """
                command-bridge.endpoints=smp=http://127.0.0.1:8772/v1/discord-command
                command-bridge.secret=synthetic-command-bridge-secret-long-enough
                command-bridge.channel-id=123456789012345678
                """);
        var values = StaffModerationConfigFile.read(file);
        var configuration = DiscordCommandBridgeConfiguration.fromEnvironment(values).orElseThrow();

        assertEquals(123456789012345678L, configuration.allowedChannelId());
        assertEquals("smp", configuration.endpoints().keySet().iterator().next());
    }

    @Test
    void missingChannelInFileBackedConfigFailsClosed() throws Exception {
        Path file = directory.resolve("missing-channel.properties");
        Files.writeString(file, """
                command-bridge.endpoints=smp=http://127.0.0.1:8772/v1/discord-command
                command-bridge.secret=synthetic-command-bridge-secret-long-enough
                """);
        var values = StaffModerationConfigFile.read(file);
        assertThrows(IllegalArgumentException.class,
                () -> DiscordCommandBridgeConfiguration.fromEnvironment(values));
    }
}
