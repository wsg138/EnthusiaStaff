package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageBootstrapBytecodeBoundaryTest {
    @Test
    void asynchronousStoragePhaseContainsNoDirectBukkitOrPlayerRecoveryInvocation()
            throws IOException, InterruptedException {
        Path javap = Path.of(System.getProperty("java.home"), "bin", executable("javap"));
        Process process = new ProcessBuilder(
                javap.toString(),
                "-classpath", System.getProperty("java.class.path"),
                "-c", "-p",
                EnthusiaStaffPaperPlugin.class.getName()
        ).redirectErrorStream(true).start();
        String disassembly = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), disassembly);

        String method = methodBody(disassembly,
                "private net.enthusia.staff.paper.EnthusiaStaffPaperPlugin$StorageBootstrapContext openStoragePhase();");
        assertTrue(method.contains("MariaDb.initialize"), method);
        for (String forbidden : List.of(
                "getServer",
                "org/bukkit",
                "captureStartupPlayer",
                "attachPunishmentRequestAlerts",
                "publishBootstrapPromotion"
        )) {
            assertFalse(method.contains(forbidden),
                    () -> "Worker storage phase directly crosses the Bukkit recovery boundary: " + forbidden
                            + System.lineSeparator() + method);
        }
    }

    private static String methodBody(String disassembly, String signature) {
        int start = disassembly.indexOf(signature);
        assertTrue(start >= 0, () -> "Missing javap method signature:\n" + disassembly);
        int next = disassembly.indexOf("\n  private ", start + signature.length());
        if (next < 0) {
            next = disassembly.length();
        }
        return disassembly.substring(start, next);
    }

    private static String executable(String name) {
        return System.getProperty("os.name", "").toLowerCase().contains("win")
                ? name + ".exe"
                : name;
    }
}
