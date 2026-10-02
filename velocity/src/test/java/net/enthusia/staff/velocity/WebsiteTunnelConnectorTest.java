package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebsiteTunnelConnectorTest {
    @Test
    void connectorIsOptionalAndIncompleteInstallationsFailClosed(@TempDir Path directory) throws IOException {
        assertTrue(WebsiteTunnelConnector.startIfInstalled(directory, () -> {}).isEmpty());
        Files.createDirectories(directory.resolve("website-tunnel"));
        Files.writeString(directory.resolve("website-tunnel/connector-token"), "x".repeat(200));
        assertThrows(IOException.class, () -> WebsiteTunnelConnector.startIfInstalled(directory, () -> {}));
    }

    @Test
    void commandUsesAnExactBinaryAndTokenFilenameWithoutCredentialValues(@TempDir Path directory) {
        var command = WebsiteTunnelConnector.command();
        assertEquals("./cloudflared", command.getFirst());
        assertEquals("--token-file", command.get(command.size() - 2));
        assertEquals("connector-token", command.getLast());
    }
}
