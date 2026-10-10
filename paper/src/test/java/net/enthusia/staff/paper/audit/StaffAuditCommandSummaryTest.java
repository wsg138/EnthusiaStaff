package net.enthusia.staff.paper.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class StaffAuditCommandSummaryTest {
    @Test
    void stripsPrivateMessageArgumentsAndSecretsFromStaffAudit() {
        String message = StaffAuditCommandSummary.summarize("/msg Alice secret private password");
        assertEquals("/msg (arguments withheld)", message);
        assertFalse(message.contains("Alice"));
        assertFalse(StaffAuditCommandSummary.summarize("/login confidential-token").contains("confidential"));
    }

    @Test
    void retainsNormalizedCommandIdentifierOnly() {
        assertEquals("/punish (arguments withheld)", StaffAuditCommandSummary.summarize("/punish Player ban"));
        assertEquals("/unknown (arguments withheld)", StaffAuditCommandSummary.summarize("bad"));
        assertEquals("/unknown (arguments withheld)", StaffAuditCommandSummary.summarize(null));
        assertEquals("/unknown (arguments withheld)", StaffAuditCommandSummary.summarize("/login:secret"));
        assertEquals("/unknown (arguments withheld)", StaffAuditCommandSummary.summarize("/tokenSuperSecret ABC"));
        assertEquals("/punish (arguments withheld)", StaffAuditCommandSummary.summarize("/PUNISH target reason"));
    }
}
