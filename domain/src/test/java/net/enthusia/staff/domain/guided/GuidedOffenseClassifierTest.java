package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuidedOffenseClassifierTest {
    @Test
    void internalMappingsExistInConfiguredPolicy() throws Exception {
        Path policy = Path.of("../paper/src/main/resources/reason-policies.yml");
        if (!Files.exists(policy)) {
            policy = Path.of("paper/src/main/resources/reason-policies.yml");
        }
        String yaml = Files.readString(policy);
        for (String id : GuidedOffenseClassifier.mappedReasonIds()) {
            assertTrue(yaml.contains("- id: " + id + "\n")
                            || yaml.contains("- id: " + id + "\r\n"),
                    "Missing configured policy reason: " + id);
        }
    }

    @Test
    void similarConductProducesDifferentGroundedReasonIds() {
        assertEquals("spam.low-level", GuidedOffenseClassifier.resolve(
                "chat", "public-chat", "spam", null, "few", null));
        assertEquals("spam.message-flooding", GuidedOffenseClassifier.resolve(
                "chat", "public-chat", "spam", null, "large-scale", null));
        assertEquals("spam.private-message", GuidedOffenseClassifier.resolve(
                "chat", "direct-message", "spam", null, "few", null));
        assertEquals("hate.abbreviated-slur-targeted", GuidedOffenseClassifier.resolve(
                "chat", "public-chat", "slur", "abbreviated", "few", "targeted"));
        assertEquals("cheating.xray-esp", GuidedOffenseClassifier.resolve(
                "gameplay", "in-game", "cheating", "xray", "sustained", null));
        assertEquals("reputation.alt-manipulation", GuidedOffenseClassifier.resolve(
                "economy", "market", "reputation", "alt", "few", null));
    }
}
