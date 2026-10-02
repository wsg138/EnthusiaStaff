package net.enthusia.staff.paper.inventory;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class LiveCursorEscrowMarker {
    private static final int MARKER_PARTS = 2;

    enum Role {
        SOURCE,
        RESULT
    }

    private final NamespacedKey key;

    LiveCursorEscrowMarker(JavaPlugin plugin) {
        key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "live_inventory_cursor_transfer");
    }

    ItemStack mark(ItemStack item, UUID operationId, Role role) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        ItemStack marked = item.clone();
        ItemMeta meta = marked.getItemMeta();
        meta.getPersistentDataContainer().set(
                key,
                PersistentDataType.STRING,
                encode(operationId, role)
        );
        marked.setItemMeta(meta);
        return marked;
    }

    ItemStack clear(ItemStack item, UUID operationId) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        Optional<Marker> marker = read(item);
        if (marker.isEmpty() || !marker.orElseThrow().operationId().equals(operationId)) {
            return item.clone();
        }
        ItemStack cleared = item.clone();
        ItemMeta meta = cleared.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        cleared.setItemMeta(meta);
        return cleared;
    }

    Optional<Marker> read(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        String encoded = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (encoded == null) {
            return Optional.empty();
        }
        String[] parts = encoded.split(":", MARKER_PARTS);
        if (parts.length != MARKER_PARTS) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Marker(
                    UUID.fromString(parts[1]),
                    Role.valueOf(parts[0].toUpperCase(Locale.ROOT))
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    boolean matches(ItemStack item, UUID operationId, Role role) {
        return read(item).filter(marker -> marker.operationId().equals(operationId) && marker.role() == role).isPresent();
    }

    ItemStack clearAny(ItemStack item) {
        if (read(item).isEmpty()) {
            return item == null ? null : item.clone();
        }
        ItemStack cleared = item.clone();
        ItemMeta meta = cleared.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        cleared.setItemMeta(meta);
        return cleared;
    }

    private static String encode(UUID operationId, Role role) {
        return Objects.requireNonNull(role, "role").name() + ':'
                + Objects.requireNonNull(operationId, "operationId");
    }

    record Marker(UUID operationId, Role role) {
        Marker {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(role, "role");
        }
    }
}
