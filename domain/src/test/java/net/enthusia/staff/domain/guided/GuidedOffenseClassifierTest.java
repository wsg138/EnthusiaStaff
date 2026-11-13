package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GuidedOffenseClassifierTest {
    private static final String CHAT = "chat";
    private static final String FEW = "few";
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
                CHAT, "public-chat", "spam", null, FEW, null));
        assertEquals("spam.message-flooding", GuidedOffenseClassifier.resolve(
                CHAT, "public-chat", "spam", null, "large-scale", null));
        assertEquals("spam.private-message", GuidedOffenseClassifier.resolve(
                CHAT, "direct-message", "spam", null, FEW, null));
        assertEquals("hate.abbreviated-slur-targeted", GuidedOffenseClassifier.resolve(
                CHAT, "public-chat", "slur", "abbreviated", FEW, "targeted"));
        assertEquals("cheating.xray-esp", GuidedOffenseClassifier.resolve(
                "gameplay", "in-game", "cheating", "xray", "sustained", null));
        assertEquals("reputation.alt-manipulation", GuidedOffenseClassifier.resolve(
                "economy", "market", "reputation", "alt", FEW, null));
    }
}
