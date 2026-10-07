package net.enthusia.staff.paper.punishment.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import org.junit.jupiter.api.Test;

class PolicyV2QuestionInputTest {
    @Test
    void parsesEverySupportedQuestionKindAgainstTheW1Definition() {
        IncidentAttributeDefinition yesNo = IncidentAttributeDefinition.booleanValue("targeted", true);
        IncidentAttributeDefinition number = IncidentAttributeDefinition.integerValue("count", true, 1, 5);
        IncidentAttributeDefinition choice = IncidentAttributeDefinition.enumValue(
                "severity", true, Set.of("low", "high-severity")
        );
        IncidentAttributeDefinition text = IncidentAttributeDefinition.textValue("context", false, 20);

        assertEquals(new IncidentAttributeValue.BooleanValue(true), PolicyV2QuestionInput.parse(yesNo, "yes"));
        assertEquals(new IncidentAttributeValue.BooleanValue(false), PolicyV2QuestionInput.parse(yesNo, "NO"));
        assertEquals(new IncidentAttributeValue.IntegerValue(4), PolicyV2QuestionInput.parse(number, "4"));
        assertEquals(
                new IncidentAttributeValue.EnumValue("high-severity"),
                PolicyV2QuestionInput.parse(choice, "High Severity")
        );
        assertEquals(
                new IncidentAttributeValue.TextValue("short context"),
                PolicyV2QuestionInput.parse(text, "short context")
        );
    }

    @Test
    void rejectsMalformedOrOutOfRangeAnswers() {
        IncidentAttributeDefinition yesNo = IncidentAttributeDefinition.booleanValue("targeted", true);
        IncidentAttributeDefinition number = IncidentAttributeDefinition.integerValue("count", true, 1, 5);
        IncidentAttributeDefinition choice = IncidentAttributeDefinition.enumValue(
                "severity", true, Set.of("low", "high")
        );

        assertThrows(IllegalArgumentException.class, () -> PolicyV2QuestionInput.parse(yesNo, "maybe"));
        assertThrows(IllegalArgumentException.class, () -> PolicyV2QuestionInput.parse(number, "6"));
        assertThrows(IllegalArgumentException.class, () -> PolicyV2QuestionInput.parse(number, "not-a-number"));
        assertThrows(IllegalArgumentException.class, () -> PolicyV2QuestionInput.parse(choice, "critical"));
    }
}
