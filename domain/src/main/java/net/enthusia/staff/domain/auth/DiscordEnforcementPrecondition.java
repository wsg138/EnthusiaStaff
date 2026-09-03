package net.enthusia.staff.domain.auth;

/**
 * External side-effect preconditions that must be checked after domain authorization.
 *
 * <p>They never grant authority. In particular, satisfying Discord role hierarchy cannot turn a
 * denied domain decision into an allowed one, and an allowed Discord decision cannot replace the
 * authoritative Minecraft punishment-policy validation.</p>
 */
public enum DiscordEnforcementPrecondition {
    DISCORD_ROLE_HIERARCHY,
    MINECRAFT_PUNISHMENT_POLICY_REVALIDATION
}
