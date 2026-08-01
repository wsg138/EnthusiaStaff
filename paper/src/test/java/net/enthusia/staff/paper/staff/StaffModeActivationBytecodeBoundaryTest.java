package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.spi.ToolProvider;
import org.junit.jupiter.api.Test;

class StaffModeActivationBytecodeBoundaryTest {
    @Test
    void durableSessionIsPublishedOnlyAfterStaffStateApplicationSucceeds() {
        String disassembly = disassemble(StaffModeManager.class);

        String method = methodBody(disassembly, "private void activateDurableSession(");
        int apply = method.indexOf("applyStaffState");
        int publish = method.indexOf("java/util/Map.put");
        int success = method.indexOf("sendMessage");
        assertTrue(apply >= 0 && publish > apply,
                () -> "Active staff-session publication preceded state application:\n" + method);
        assertTrue(success > publish,
                () -> "Staff-mode success was reported before active publication:\n" + method);
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
