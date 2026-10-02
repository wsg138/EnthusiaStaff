package net.enthusia.staff.discordbot;

/** Pure hierarchy/ownership policy for D13-managed Discord role mutations. */
final class DiscordRoleMutationPolicy {
    private DiscordRoleMutationPolicy() {
    }

    static boolean canMutate(boolean publicRole, boolean integrationManaged, boolean canInteract) {
        return !publicRole && !integrationManaged && canInteract;
    }
}
