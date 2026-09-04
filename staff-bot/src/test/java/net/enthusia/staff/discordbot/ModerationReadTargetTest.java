package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ModerationReadTargetTest {
    @Test
    void parsesDiscordUserTarget() {
        ModerationReadTarget target = ModerationReadTarget.parse("discord:1049827163345127424");

        assertEquals(1049827163345127424L, target.userId());
        assertEquals("discord:1049827163345127424", target.key());
        assertEquals(OptionalLong.empty(), target.channelId());
        assertEquals(OptionalLong.empty(), target.messageId());
    }

    @Test
    void parsesExactMessageTarget() {
        ModerationReadTarget target = ModerationReadTarget.parse(
                "message:1541286004298752091:1541300000000000001:1049827163345127424");

        assertEquals(1049827163345127424L, target.userId());
        assertEquals(1541286004298752091L, target.channelId().orElseThrow());
        assertEquals(1541300000000000001L, target.messageId().orElseThrow());
        assertTrue(target.key().startsWith("message:"));
    }

    @Test
    void rejectsLegacyAndMalformedTargets() {
        for (String value : new String[] {
                "sample-river-ash", "discord:0", "discord:-1", "message:1:2", "message:1:2:0", "bad|target"
        }) {
            assertThrows(IllegalArgumentException.class, () -> ModerationReadTarget.parse(value));
        }
    }
}
