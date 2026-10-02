package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import net.enthusia.staff.common.CaseId;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class StaffWarningPresentationTest {
    @Test
    void prominentMessageKeepsTheFullLiteralReasonAndCaseReference() {
        String reason = "Low-level spam <red>literal evidence</red>";
        String message = PlainTextComponentSerializer.plainText().serialize(
                StaffWarningPresentation.chat(reason, new CaseId("HASWGPFC87E06QEB")));
        assertTrue(message.contains("You have received a warning for: " + reason));
        assertTrue(message.contains("Case: HASWGPFC87E06QEB"));
        assertTrue(message.startsWith("\n"));
    }
}
