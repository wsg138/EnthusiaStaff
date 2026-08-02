package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;

class StorageBootstrapBytecodeBoundaryTest {
    @Test
    void asynchronousStoragePhaseContainsNoDirectBukkitOrPlayerRecoveryInvocation() {
        String disassembly = disassemble(EnthusiaStaffPaperPlugin.class);

        String method = methodBody(disassembly,
                "private net.enthusia.staff.paper.EnthusiaStaffPaperPlugin$StorageBootstrapContext openStoragePhase();");
        assertTrue(method.contains("MariaDb.initialize"), method);
        for (String forbidden : List.of(
                "getServer",
                "getOnlinePlayers",
                "recoverOnlinePlayers",
                "org/bukkit",
                "org/bukkit/entity/Player",
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

    private static String disassemble(Class<?> type) {
        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow();
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        int exit = javap.run(
                writer,
                writer,
                "-classpath", System.getProperty("java.class.path"),
                "-c", "-p",
                type.getName()
        );
        writer.flush();
        assertEquals(0, exit, output.toString());
        return output.toString();
    }
}
