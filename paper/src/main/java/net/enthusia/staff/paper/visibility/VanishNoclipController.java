package net.enthusia.staff.paper.visibility;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class VanishNoclipController {
    private final VanishClientGameModeAdapter clientModes;
    private final Map<UUID, Boolean> baselineNoPhysics = new ConcurrentHashMap<>();

    VanishNoclipController(VanishClientGameModeAdapter clientModes) {
        this.clientModes = java.util.Objects.requireNonNull(clientModes, "clientModes");
    }

    public static VanishNoclipController install(JavaPlugin plugin) {
        return new VanishNoclipController(VanishClientGameModeAdapterFactory.install(plugin.getLogger()));
    }

    public boolean supportsClientPresentation() { return clientModes.available(); }
    public String unavailableReason() { return clientModes.unavailableReason(); }

    boolean canEnable(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR || clientModes.available();
    }

    boolean reconcile(Player player, boolean fullVanish) {
        if (fullVanish && !canEnable(player)) {
            return false;
        }
        return fullVanish ? enable(player) : disable(player);
    }

    boolean maintain(Player player) {
        UUID playerId = player.getUniqueId();
        if (!baselineNoPhysics.containsKey(playerId)) {
            return false;
        }
        player.setNoPhysics(true);
        return true;
    }

    void gameModeChanged(UUID playerId, GameMode gameMode, boolean fullVanish) {
        if (fullVanish && baselineNoPhysics.containsKey(playerId)) {
            baselineNoPhysics.put(playerId, gameMode == GameMode.SPECTATOR);
        }
    }

    void retire(UUID playerId) { baselineNoPhysics.remove(playerId); }

    private boolean enable(Player player) {
        UUID playerId = player.getUniqueId();
        baselineNoPhysics.putIfAbsent(playerId, player.hasNoPhysics());
        player.setNoPhysics(true);
        if (player.getGameMode() == GameMode.SPECTATOR || clientModes.present(player, GameMode.SPECTATOR)) {
            return true;
        }
        restoreServerPhysics(player, baselineNoPhysics.remove(playerId));
        return false;
    }

    private boolean disable(Player player) {
        Boolean baseline = baselineNoPhysics.remove(player.getUniqueId());
        if (baseline == null) return true;
        boolean presented = player.getGameMode() == GameMode.SPECTATOR
                || clientModes.present(player, player.getGameMode());
        restoreServerPhysics(player, baseline);
        player.updateInventory();
        return presented;
    }

    private static void restoreServerPhysics(Player player, Boolean baseline) {
        player.setNoPhysics(player.getGameMode() == GameMode.SPECTATOR || Boolean.TRUE.equals(baseline));
    }
}
