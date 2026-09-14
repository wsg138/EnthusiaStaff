package net.enthusia.staff.discordbot;

import java.util.Set;
import java.util.UUID;

/** Current Minecraft group facts used only to project managed Discord presentation roles. */
interface MinecraftRoleEligibilityClient {
    Set<String> groups(UUID playerId);
}
