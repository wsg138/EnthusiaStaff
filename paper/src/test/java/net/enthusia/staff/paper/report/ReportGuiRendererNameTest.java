package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportGuiRendererNameTest {
    @Test
    void rendersCachedNameOrExplicitUnresolvedIdentity() {
        UUID id = UUID.fromString("12345678-0000-0000-0000-000000000001");
        assertEquals("Moderator", ReportGuiRenderer.humanName(id, "Moderator"));
        assertEquals("Unknown (12345678)", ReportGuiRenderer.humanName(id, null));
        assertEquals("Unknown (12345678)", ReportGuiRenderer.humanName(id, " "));
    }
}
