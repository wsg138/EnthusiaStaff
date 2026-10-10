package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PunishmentSanctionRoutesTest {
    private static final String WARN_ONE = "00000000-0000-0000-0000-000000000101";
    private static final String WARN_TWO = "00000000-0000-0000-0000-000000000102";

    @Test
    void unpunishAndRemoveSelectOneExactWarningNotLatestPlayerPunishment() {
        assertArrayEquals(
                new String[]{"sanction", "revoke", WARN_ONE, "Incorrect warning"},
                PunishmentSanctionRoutes.rewrite("unpunish", new String[]{WARN_ONE, "Incorrect warning"})
        );
        assertArrayEquals(
                new String[]{"sanction", "revoke", WARN_TWO, "Wrong", "context"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"remove", WARN_TWO, "Wrong", "context"})
        );
    }

    @Test
    void reduceChangeAndOtherCommandsDelegateToExistingSanctionLifecycle() {
        assertArrayEquals(
                new String[]{"sanction", "reduce", WARN_ONE, "2d", "Review"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"reduce", WARN_ONE, "2d", "Review"})
        );
        assertArrayEquals(
                new String[]{"sanction", "reduce", WARN_ONE, "4h", "Shorter"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "4h", "Shorter"})
        );
        assertArrayEquals(
                new String[]{"sanction", "end", WARN_ONE, "No longer needed"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "end", "No longer needed"})
        );
        assertArrayEquals(
                new String[]{"sanction", "revoke", WARN_ONE, "Misapplied"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "remove", "Misapplied"})
        );
        assertArrayEquals(
                new String[]{"sanction", "overturn", WARN_ONE, "Appeal", "accepted"},
                PunishmentSanctionRoutes.rewrite("punish",
                        new String[]{"change", WARN_ONE, "overturn", "Appeal", "accepted"})
        );
    }

    @Test
    void ambiguousPlayerCaseAndMissingSanctionIdsCannotChooseLatestAutomatically() {
        assertNull(PunishmentSanctionRoutes.rewrite("unpunish", new String[]{"TestPlayer", "oops"}));
        assertNull(PunishmentSanctionRoutes.rewrite("punish", new String[]{"remove", "CASE001", "oops"}));
        assertNull(PunishmentSanctionRoutes.rewrite("punish", new String[]{"remove", WARN_ONE}));
        assertNull(PunishmentSanctionRoutes.rewrite("punish", new String[]{"change", WARN_ONE}));
        assertNull(PunishmentSanctionRoutes.rewrite("unpunish", new String[0]));
        assertFalse(PunishmentSanctionRoutes.handles("warn", new String[]{"remove", WARN_ONE}));
        assertFalse(PunishmentSanctionRoutes.handles("punish", new String[]{"PlayerName"}));
        assertTrue(PunishmentSanctionRoutes.usage().contains("/history <player>"));
    }

    @Test
    void NewRoutesAreRegisteredAndAuthorityGated() throws IOException {
        Path base = Path.of("src/main/java/net/enthusia/staff/paper");
        String registrar = Files.readString(base.resolve("PaperCommandRegistrar.java"))
                .replace("\r\n", "\n");
        String command = Files.readString(base.resolve("command/PunishmentCommand.java"))
                .replace("\r\n", "\n");
        String gate = Files.readString(base.resolve("staff/StaffDutyCommandGate.java"))
                .replace("\r\n", "\n");
        assertTrue(registrar.contains("List.of(\"punish\", \"unpunish\""));
        assertTrue(registrar.contains("command.configureSanctionLifecycle(sanctionLifecycle)"));
        assertTrue(command.indexOf("PunishmentSanctionRoutes.handles(route, args)")
                < command.indexOf("requireDraftPermission(sender, actor)"));
        assertTrue(command.contains("sanctionLifecycle.execute(sender, label, routed)"));
        assertTrue(gate.contains("\"punish\", \"unpunish\""));
    }
}
