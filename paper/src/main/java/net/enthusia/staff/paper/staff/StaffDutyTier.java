package net.enthusia.staff.paper.staff;

import net.enthusia.staff.domain.auth.StaffRank;

/**
 * Collapses the six {@link StaffRank} values into the three on-duty enforcement tiers used by the
 * overnight permission model.
 *
 * <ul>
 *   <li>HELPER — tightly restricted trial rank: survival/spectator only, full lockdown.</li>
 *   <li>MOD — Helper base plus extras that are <em>logged instead of blocked</em> (pickup/drop,
 *   staff/empty inventory toggle, container use). Protected Survival or Spectator; never Creative.</li>
 *   <li>DEVELOPER — technical testing tier: unrestricted world/gameplay interaction with audit
 *   logging, without inheriting moderation approval authority.</li>
 *   <li>ADMIN — Admin/Founder operational tier: unrestricted, but every action is logged.</li>
 * </ul>
 *
 * <p>Developer remains a separate technical role. This operational tier controls only Staff Mode
 * interaction restrictions and auditing; it does not alter punishment, appeal, or approval policy.
 */
enum StaffDutyTier {
    HELPER,
    MOD,
    DEVELOPER,
    ADMIN;

    static StaffDutyTier of(StaffRank rank) {
        if (rank == null) {
            return null;
        }
        return switch (rank) {
            case HELPER -> HELPER;
            case MOD -> MOD;
            case DEVELOPER -> DEVELOPER;
            case ADMIN, FOUNDER -> ADMIN;
            case SYSTEM -> null;
        };
    }
}
