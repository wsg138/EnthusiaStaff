package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaffDutyCommandGateWiringTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/staff/StaffDutyCommandGate.java"
    );

    @Test
    void restrictedCommandsAcceptExplicitUnrestrictedIdentityAuthority() throws IOException {
        String source = Files.readString(SOURCE).replace("\r\n", "\n");
        int start = source.indexOf("private boolean requiresOnDuty");
        int end = source.indexOf("private static void deny", start);
        String method = source.substring(start, end);

        assertTrue(method.contains("staffMode.authorityActiveOrUnrestricted(player.getUniqueId())"));
        assertFalse(method.contains("!staffMode.authorityActive(player.getUniqueId())"));
    }
}
