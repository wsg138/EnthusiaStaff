package net.enthusia.staff.domain.auth;

/**
 * External side-effect preconditions that must be checked after domain authorization.
 *
 * <p>They never grant authority. In particular, satisfying Discord role hierarchy cannot turn a
 * denied domain decision into an allowed one.</p>
 */
public enum DiscordEnforcementPrecondition {
    DISCORD_ROLE_HIERARCHY
}
