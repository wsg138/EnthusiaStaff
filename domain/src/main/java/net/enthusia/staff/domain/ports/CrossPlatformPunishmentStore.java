package net.enthusia.staff.domain.ports;

import net.enthusia.staff.domain.application.CrossPlatformPunishmentPlan;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentResult;

/** Atomic durable boundary for D08 Minecraft plus Discord punishment intent creation. */
public interface CrossPlatformPunishmentStore {
    CrossPlatformPunishmentResult create(CrossPlatformPunishmentPlan plan);
}
