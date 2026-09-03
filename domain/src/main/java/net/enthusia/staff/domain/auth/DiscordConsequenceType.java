package net.enthusia.staff.domain.auth;

public enum DiscordConsequenceType {
    WARNING,
    MUTE,
    KICK,
    BAN,
    CHANNEL_RESTRICTION;

    public boolean requiresInstantLength() {
        return this == WARNING || this == KICK;
    }

    public boolean requiresDiscordPlatform() {
        return this == CHANNEL_RESTRICTION;
    }
}
