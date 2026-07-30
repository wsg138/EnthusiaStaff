from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


vanish = Path("paper/src/main/java/net/enthusia/staff/paper/visibility/VanishManager.java")
replace_once(
    vanish,
    '''    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        recordViewerRank(player);
        applySpectatorPolicy(player, event.getNewGameMode(), true);
        refreshTarget(player);
    }
''',
    '''    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        recordViewerRank(player);
        applySpectatorPolicy(player, event.getNewGameMode(), true);
        refreshTarget(player);
        player.getScheduler().execute(plugin, () -> refreshTarget(player), null, 1L);
    }
''',
)
replace_once(
    vanish,
    '''    private void applyTabListing(Player viewer, Player target, boolean canSee) {
        UUID targetId = target.getUniqueId();
        if (!canSee || hiddenSpectators.contains(targetId)) {
            viewer.unlistPlayer(target);
            return;
        }
        StaffRank targetRank = onlineStaffRanks.get(targetId);
        if (targetRank == null) {
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR && !spectatorTabPackets.available()) {
            viewer.unlistPlayer(target);
            return;
        }
        viewer.listPlayer(target);
    }
''',
    '''    private void applyTabListing(Player viewer, Player target, boolean canSee) {
        if (!shouldList(target, canSee)) {
            unlistSafely(viewer, target);
            return;
        }
        try {
            viewer.listPlayer(target);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Player tab listing raced with visibility removal", exception);
            unlistSafely(viewer, target);
        }
    }

    private boolean shouldList(Player target, boolean canSee) {
        UUID targetId = target.getUniqueId();
        if (!canSee || hiddenSpectators.contains(targetId)) {
            return false;
        }
        StaffRank targetRank = onlineStaffRanks.get(targetId);
        return targetRank != null
                && (target.getGameMode() != GameMode.SPECTATOR || spectatorTabPackets.available());
    }

    private void unlistSafely(Player viewer, Player target) {
        try {
            viewer.unlistPlayer(target);
        } catch (IllegalStateException exception) {
            plugin.getLogger().log(Level.FINE, "Player tab removal raced with disconnect", exception);
        }
    }
''',
)

policy = Path("paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicy.java")
replace_once(
    policy,
    '''package net.enthusia.staff.paper.staff;

import net.enthusia.staff.domain.auth.StaffRank;
''',
    '''package net.enthusia.staff.paper.staff;

import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
''',
)
replace_once(
    policy,
    '''    static boolean usesCreativeMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    static boolean hasAdvancedStaffTools(StaffRank rank) {
''',
    '''    static boolean usesCreativeMode(StaffRank rank) {
        return rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
    }

    static GameMode requiredGameMode(StaffRank rank) {
        return usesCreativeMode(rank) ? GameMode.CREATIVE : GameMode.SPECTATOR;
    }

    static boolean hasAdvancedStaffTools(StaffRank rank) {
''',
)

manager = Path("paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java")
replace_once(
    manager,
    '''import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
''',
    '''import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
''',
)
replace_once(
    manager,
    '''    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
''',
    '''    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!active(playerId) || transitions.contains(playerId)) {
            return;
        }
        StaffRank rank = ranks.get(playerId);
        if (rank == null || event.getNewGameMode() != StaffModeAccessPolicy.requiredGameMode(rank)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your staff rank cannot use that game mode while staff mode is active."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
''',
)
replace_once(
    manager,
    '''        boolean creative = StaffModeAccessPolicy.usesCreativeMode(rank);
        player.setGameMode(creative ? GameMode.CREATIVE : GameMode.SPECTATOR);
''',
    '''        player.setGameMode(StaffModeAccessPolicy.requiredGameMode(rank));
''',
)

test = Path("paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicyTest.java")
replace_once(
    test,
    '''import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;
''',
    '''import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
''',
)
replace_once(
    test,
    '''        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.HELPER));
''',
    '''        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.HELPER));
        assertFalse(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.HELPER));
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.HELPER) == GameMode.SPECTATOR);
''',
)
replace_once(
    test,
    '''        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
''',
    '''        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.hasAdvancedStaffTools(StaffRank.DEVELOPER));
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.DEVELOPER) == GameMode.SPECTATOR);
''',
)
replace_once(
    test,
    '''        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.FOUNDER));
''',
    '''        assertFalse(StaffModeAccessPolicy.usesCreativeMode(StaffRank.MOD));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.ADMIN));
        assertTrue(StaffModeAccessPolicy.usesCreativeMode(StaffRank.FOUNDER));
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.MOD) == GameMode.SPECTATOR);
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.ADMIN) == GameMode.CREATIVE);
        assertTrue(StaffModeAccessPolicy.requiredGameMode(StaffRank.FOUNDER) == GameMode.CREATIVE);
''',
)

plugin = Path("paper/src/main/resources/plugin.yml")
replace_once(
    plugin,
    '''  vanish:
    description: Toggle durable rank-aware vanish
    usage: /vanish
''',
    '''  vanish:
    description: Toggle durable rank-aware vanish or spectator tab presentation
    usage: /vanish | /vanish tab <show|hide>
''',
)
