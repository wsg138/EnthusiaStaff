package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperIntegrationManagerRoseChatLifecycleTest {

    @Test
    void lifecycleMatcherAcceptsOnlyExactRoseChatPluginName() {
        assertTrue(PaperIntegrationManager.isRoseChat("RoseChat"));
        assertFalse(PaperIntegrationManager.isRoseChat("rosechat"));
        assertFalse(PaperIntegrationManager.isRoseChat("EnthusiaStaff"));
        assertFalse(PaperIntegrationManager.isRoseChat(null));
    }
}
