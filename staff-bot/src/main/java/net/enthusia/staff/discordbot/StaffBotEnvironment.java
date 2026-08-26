package net.enthusia.staff.discordbot;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * Fixed Discord application identities for the two explicitly separated runtime environments.
 */
public enum StaffBotEnvironment {
    STAGING(
            "staging",
            1541279616881397772L,
            1410303324745371709L,
            1541286004298752091L),
    PRODUCTION(
            "production",
            1541279426233376818L,
            1410303324745371709L,
            null);

    private final String label;
    private final long applicationId;
    private final long guildId;
    private final Long testChannelId;

    StaffBotEnvironment(String label, long applicationId, long guildId, Long testChannelId) {
        this.label = label;
        this.applicationId = applicationId;
        this.guildId = guildId;
        this.testChannelId = testChannelId;
    }

    public String label() {
        return label;
    }

    public long applicationId() {
        return applicationId;
    }

    public long guildId() {
        return guildId;
    }

    public OptionalLong testChannelId() {
        return testChannelId == null ? OptionalLong.empty() : OptionalLong.of(testChannelId);
    }

    public static StaffBotEnvironment parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("staff bot environment is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (StaffBotEnvironment environment : values()) {
            if (environment.label.equals(normalized)) {
                return environment;
            }
        }
        throw new IllegalArgumentException("staff bot environment must be staging or production");
    }
}
