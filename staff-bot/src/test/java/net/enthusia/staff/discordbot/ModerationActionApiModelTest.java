package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import org.junit.jupiter.api.Test;

class ModerationActionApiModelTest {
    @Test
    void durationClassificationAndNotificationComeFromTheServer() {
        var preset = input("MUTE", "1h", Optional.empty()).toIntent();
        assertFalse(preset.customDuration());
        assertTrue(preset.notifyTarget());
        assertEquals(0, preset.messageDeleteSeconds());
        assertTrue(input("MUTE", "2h", Optional.empty()).toIntent().customDuration());
    }

    @Test
    void rejectsUnsupportedActionsAndConflictingRestriction() {
        assertThrows(IllegalArgumentException.class, () -> input("MINECRAFT_BAN", "1d", Optional.empty()).toIntent());
        assertThrows(IllegalArgumentException.class, () -> input("WARNING", "1d", Optional.empty()).toIntent());
        assertThrows(IllegalArgumentException.class, () -> input("CHANNEL_RESTRICTION", "1d", Optional.empty()).toIntent());
        var scope = new DiscordRestrictionTarget(DiscordRestrictionTarget.Kind.CHANNEL,"123",DiscordRestrictionTarget.Mode.READ_ONLY);
        assertThrows(IllegalArgumentException.class, () -> input("MUTE", "1h", Optional.of(scope)).toIntent());
    }

    @Test
    void rejectsClientAuthorityFieldsAndInvalidSessionBinding() {
        String forged = "{\"actorId\":\"1\",\"guildId\":\"2\",\"targetKey\":\"discord:3\",\"sessionBinding\":\"" + "a".repeat(64) + "\",\"approved\":true}";
        assertThrows(Exception.class, () -> ModerationReadApiServer.jsonMapper().readValue(
                forged.getBytes(StandardCharsets.UTF_8), ModerationActionApiService.Request.class));
        assertThrows(IllegalArgumentException.class, () -> new ModerationActionApiService.Request(
                "1", "2", "discord:3", "bad", Optional.empty(), Optional.empty()));
    }

    private static ModerationActionApiService.IntentInput input(String type, String duration,
            Optional<DiscordRestrictionTarget> restriction) {
        return new ModerationActionApiService.IntentInput(type,duration,"Test reason","Test explanation",restriction);
    }
}
