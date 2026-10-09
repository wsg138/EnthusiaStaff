package net.enthusia.staff.domain.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.enthusia.staff.domain.ports.PlayerDirectory;

/** Per-response player labels. Directory access belongs on a storage worker, never a region thread. */
public final class PlayerNames implements Function<UUID, String> {
    private static final int MAX_NAMES = 512;
    private final PlayerDirectory directory;
    private final Map<UUID, String> names = new HashMap<>();

    public PlayerNames(PlayerDirectory directory) {
        this.directory = directory;
    }

    @Override
    public String apply(UUID playerId) {
        java.util.Objects.requireNonNull(playerId, "playerId");
        if (names.containsKey(playerId)) {
            return names.get(playerId);
        }
        if (names.size() >= MAX_NAMES) {
            return unknown(playerId);
        }
        String name = directory == null ? unknown(playerId) : directory.find(playerId.toString())
                .flatMap(PlayerIdentity::currentUsername)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> unknown(playerId));
        names.put(playerId, name);
        return name;
    }

    public static String label(PlayerIdentity identity) {
        return identity.currentUsername().filter(value -> !value.isBlank())
                .orElseGet(() -> unknown(identity.playerId()));
    }

    public static String unknown(UUID playerId) {
        return "Unknown player (" + playerId + ")";
    }
}
