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
 *   <li>ADMIN — Admin/Founder: no restrictions, but every action is logged.</li>
 * </ul>
 *
 * <p><strong>Flagged for owner review:</strong> {@link StaffRank#DEVELOPER} is mapped to the MOD
 * tier per the overnight owner directive. ENTHUSIASTAFF-GOALS.md §17 states Developer "is never
 * treated as Mod or higher"; if the owner confirms that, change the DEVELOPER branch below to a
 * dedicated restricted tier.
 */
enum StaffDutyTier {
    HELPER,
    MOD,
    ADMIN;

    static StaffDutyTier of(StaffRank rank) {
        if (rank == null) {
            return null;
        }
        return switch (rank) {
            case HELPER -> HELPER;
            case MOD, DEVELOPER -> MOD;
            case ADMIN, FOUNDER -> ADMIN;
            case SYSTEM -> null;
        };
    }
}
