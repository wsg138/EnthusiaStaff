package net.enthusia.staff.paper.tester;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class CheatTesterSnapshotCodec {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_ITEM_BYTES = 1024 * 1024;
    private static final int MAX_SNAPSHOT_CHARS = 6 * 1024 * 1024;
    private static final int HOTBAR_SIZE = 9;

    private final JavaPlugin plugin;
    private final ObjectMapper json = new ObjectMapper();

    CheatTesterSnapshotCodec(JavaPlugin plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
    }

    String capture(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player must be present");
        }
        PlayerInventory inventory = player.getInventory();
        Location location = player.getLocation();
        Vector velocity = player.getVelocity();
        Snapshot snapshot = new Snapshot(
                SCHEMA_VERSION,
                location.getWorld().getUID().toString(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                velocity.getX(),
                velocity.getY(),
                velocity.getZ(),
                player.getFallDistance(),
                player.isInvulnerable(),
                inventory.getHeldItemSlot(),
                encodeItems(inventory.getStorageContents()),
                encodeItems(inventory.getArmorContents()),
                encodeItem(inventory.getItemInOffHand())
        );
        return encode(snapshot);
    }

    void restore(
            Player player,
            String encoded,
            boolean restoreMovement,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        requireRestoreArguments(player, encoded, success, failure);
        Snapshot snapshot = decode(encoded);
        try {
            applyBaseState(player, snapshot);
            if (restoreMovement) {
                restoreMovement(player, snapshot, success, failure);
            } else {
                success.run();
            }
        } catch (RuntimeException exception) {
            failure.accept(exception);
        }
    }

    private static void requireRestoreArguments(
            Player player,
            String encoded,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        if (player == null || encoded == null || success == null || failure == null) {
            throw new IllegalArgumentException("restore fields must be present");
        }
    }

    private void restoreMovement(
            Player player,
            Snapshot snapshot,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        World world = Bukkit.getWorld(UUID.fromString(snapshot.worldId()));
        if (world == null) {
            failure.accept(new IllegalStateException("snapshot world is unavailable"));
            return;
        }
        Location destination = new Location(
                world,
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                snapshot.yaw(),
                snapshot.pitch()
        );
        Vector velocity = new Vector(snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
        player.teleportAsync(destination).whenComplete(
                (teleported, error) -> handleTeleportResult(player, snapshot, velocity, teleported, error, success, failure)
        );
    }

    private void handleTeleportResult(
            Player player,
            Snapshot snapshot,
            Vector velocity,
            Boolean teleported,
            Throwable error,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        if (error != null) {
            failure.accept(error);
            return;
        }
        if (!Boolean.TRUE.equals(teleported)) {
            failure.accept(new IllegalStateException("tester state teleport restoration was refused"));
            return;
        }
        scheduleMovementRestore(player, snapshot, velocity, success, failure);
    }

    private void scheduleMovementRestore(
            Player player,
            Snapshot snapshot,
            Vector velocity,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        boolean scheduled = player.getScheduler().execute(
                plugin,
                () -> applyMovementState(player, snapshot, velocity, success, failure),
                () -> failure.accept(new IllegalStateException("target retired during state restoration")),
                1L
        );
        if (!scheduled) {
            failure.accept(new IllegalStateException("target state restoration could not be scheduled"));
        }
    }

    private static void applyMovementState(
            Player player,
            Snapshot snapshot,
            Vector velocity,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        try {
            player.setVelocity(velocity);
            player.setFallDistance(snapshot.fallDistance());
            player.setInvulnerable(snapshot.invulnerable());
            success.run();
        } catch (RuntimeException exception) {
            failure.accept(exception);
        }
    }

    private static void applyBaseState(Player player, Snapshot snapshot) {
        applyInventory(player, snapshot);
        player.setInvulnerable(snapshot.invulnerable());
        player.setFallDistance(snapshot.fallDistance());
    }

    private static void applyInventory(Player player, Snapshot snapshot) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = decodeItems(snapshot.storage(), inventory.getStorageContents().length);
        ItemStack[] armor = decodeItems(snapshot.armor(), inventory.getArmorContents().length);
        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(decodeItem(snapshot.offhand()));
        if (snapshot.heldSlot() < 0 || snapshot.heldSlot() >= HOTBAR_SIZE) {
            throw new IllegalArgumentException("tester snapshot held slot is invalid");
        }
        inventory.setHeldItemSlot(snapshot.heldSlot());
        player.updateInventory();
    }

    private String encode(Snapshot snapshot) {
        try {
            String encoded = json.writeValueAsString(snapshot);
            if (encoded.length() > MAX_SNAPSHOT_CHARS) {
                throw new IllegalArgumentException("tester snapshot exceeds the safety limit");
            }
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode cheat tester snapshot", exception);
        }
    }

    private Snapshot decode(String encoded) {
        if (encoded.length() > MAX_SNAPSHOT_CHARS) {
            throw new IllegalArgumentException("tester snapshot exceeds the safety limit");
        }
        try {
            Snapshot snapshot = json.readValue(encoded, Snapshot.class);
            validateSnapshot(snapshot);
            return snapshot;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to decode cheat tester snapshot", exception);
        }
    }

    private static void validateSnapshot(Snapshot snapshot) {
        if (snapshot.schemaVersion() != SCHEMA_VERSION || snapshot.worldId() == null
                || snapshot.storage() == null || snapshot.armor() == null) {
            throw new IllegalArgumentException("tester snapshot schema is unsupported");
        }
    }

    private static String[] encodeItems(ItemStack[] items) {
        String[] encoded = new String[items.length];
        for (int index = 0; index < items.length; index++) {
            encoded[index] = encodeItem(items[index]);
        }
        return encoded;
    }

    private static String encodeItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        byte[] bytes = item.serializeAsBytes();
        if (bytes.length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("tester snapshot item exceeds the safety limit");
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static ItemStack[] decodeItems(String[] encoded, int expectedLength) {
        if (encoded.length != expectedLength) {
            throw new IllegalArgumentException("tester snapshot slot count does not match runtime inventory");
        }
        ItemStack[] items = new ItemStack[encoded.length];
        for (int index = 0; index < encoded.length; index++) {
            items[index] = decodeItem(encoded[index]);
        }
        return items;
    }

    private static ItemStack decodeItem(String encoded) {
        if (encoded == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("tester snapshot contains invalid item encoding", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("tester snapshot item length is invalid");
        }
        return ItemStack.deserializeBytes(bytes);
    }

    private record Snapshot(
            int schemaVersion,
            String worldId,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            double velocityX,
            double velocityY,
            double velocityZ,
            float fallDistance,
            boolean invulnerable,
            int heldSlot,
            String[] storage,
            String[] armor,
            String offhand
    ) {
    }
}
