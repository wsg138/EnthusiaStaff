package net.enthusia.staff.domain.discord;

/** Previous member permission override retained so restriction removal can restore exact state. */
public record DiscordPermissionSnapshot(boolean existed, long allowedRaw, long deniedRaw) {
    public static DiscordPermissionSnapshot absent() {
        return new DiscordPermissionSnapshot(false, 0L, 0L);
    }
}
