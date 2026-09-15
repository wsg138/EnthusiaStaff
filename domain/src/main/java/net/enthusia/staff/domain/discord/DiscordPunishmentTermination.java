package net.enthusia.staff.domain.discord;

/** Requested terminal reason for a reversible Discord punishment. */
public enum DiscordPunishmentTermination {
    NONE,
    END,
    REVOKE,
    OVERTURN,
    EXPIRE
}
