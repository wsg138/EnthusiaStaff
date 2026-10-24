package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ReportPlayerNamesTest {
    @Test void currentPlayerNamesAreBoundedAndSafeForDiscord() {
        assertEquals("Steve_123", ReportPlayerNames.display("Steve_123"));
        assertEquals("*BedrockUser", ReportPlayerNames.display("*BedrockUser"));
        assertEquals("Unknown player", ReportPlayerNames.display(null));
        assertEquals("Unknown player", ReportPlayerNames.display("@everyone"));
        assertEquals("Unknown player", ReportPlayerNames.display("bad`name"));
        assertEquals("Unknown player", ReportPlayerNames.display("X".repeat(50)));
    }
}
