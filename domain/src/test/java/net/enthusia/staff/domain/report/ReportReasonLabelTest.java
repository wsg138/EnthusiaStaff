package net.enthusia.staff.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ReportReasonLabelTest {
    @Test void machineIdsBecomeReadableDisplayNames() {
        assertEquals("Combat Cheating", ReportReasonLabel.display("combat.cheating"));
        assertEquals("Chat Spam", ReportReasonLabel.display("chat_spam"));
        assertEquals("Griefing Other Bases", ReportReasonLabel.display("griefing-other.bases"));
        assertEquals("Unspecified reason", ReportReasonLabel.display(null));
    }
}
