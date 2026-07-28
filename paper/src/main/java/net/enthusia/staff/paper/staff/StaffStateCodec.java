package net.enthusia.staff.paper.staff;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class StaffStateCodec {
    public static final int SCHEMA_VERSION = 1;
    private static final int MAGIC = 0x45535331;
    private static final int MAX_ITEM_BYTES = 6 * 1024 * 1024;
    private static final int MAX_EFFECTS = 128;

    public Captured capture(Player player, String serverId) {
        if (player == null || serverId == null || !serverId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("valid player and server ID are required");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(SCHEMA_VERSION);
                output.writeUTF(serverId);
                writeBytes(output, ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
                output.writeInt(player.getLevel());
                output.writeFloat(player.getExp());
                output.writeInt(player.getTotalExperience());
                output.writeDouble(player.getHealth());
                output.writeDouble(player.getAbsorptionAmount());
                output.writeInt(player.getFoodLevel());
                output.writeFloat(player.getSaturation());
                output.writeFloat(player.getExhaustion());
                Collection<PotionEffect> effects = player.getActivePotionEffects();
                if (effects.size() > MAX_EFFECTS) {
                    throw new IllegalStateException("player has too many active potion effects to snapshot safely");
                }
                output.writeInt(effects.size());
                for (PotionEffect effect : effects) {
                    output.writeUTF(effect.getType().getKey().asString());
                    output.writeInt(effect.getDuration());
                    output.writeInt(effect.getAmplifier());
                    output.writeBoolean(effect.isAmbient());
                    output.writeBoolean(effect.hasParticles());
                    output.writeBoolean(effect.hasIcon());
                }
                Location location = player.getLocation();
                output.writeUTF(location.getWorld().getKey().asString());
                output.writeDouble(location.getX());
                output.writeDouble(location.getY());
                output.writeDouble(location.getZ());
                output.writeFloat(location.getYaw());
                output.writeFloat(location.getPitch());
                output.writeUTF(player.getGameMode().name());
                output.writeBoolean(player.getAllowFlight());
                output.writeBoolean(player.isFlying());
                output.writeFloat(player.getFlySpeed());
                output.writeFloat(player.getWalkSpeed());
                output.writeBoolean(player.isInvulnerable());
                output.writeBoolean(player.isCollidable());
                output.writeBoolean(player.getCanPickupItems());
                output.writeInt(player.getFireTicks());
                output.writeInt(player.getRemainingAir());
                output.writeFloat(player.getFallDistance());
            }
            byte[] snapshot = bytes.toByteArray();
            return new Captured(SCHEMA_VERSION, snapshot, checksum(snapshot));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode staff state snapshot", exception);
        }
    }

    public boolean restore(Player player, byte[] snapshot) {
        Decoded decoded = decode(player, snapshot);
        if (!player.teleport(decoded.location())) {
            return false;
        }
        player.getInventory().setContents(decoded.inventory());
        player.setLevel(decoded.level());
        player.setExp(decoded.experienceProgress());
        player.setTotalExperience(decoded.totalExperience());
        player.setFoodLevel(decoded.food());
        player.setSaturation(decoded.saturation());
        player.setExhaustion(decoded.exhaustion());
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        decoded.effects().forEach(player::addPotionEffect);
        player.setGameMode(decoded.gameMode());
        player.setAllowFlight(decoded.allowFlight());
        player.setFlying(decoded.allowFlight() && decoded.flying());
        player.setFlySpeed(decoded.flySpeed());
        player.setWalkSpeed(decoded.walkSpeed());
        player.setInvulnerable(decoded.invulnerable());
        player.setCollidable(decoded.collidable());
        player.setCanPickupItems(decoded.canPickupItems());
        player.setFireTicks(decoded.fireTicks());
        player.setRemainingAir(decoded.remainingAir());
        player.setFallDistance(decoded.fallDistance());
        player.setAbsorptionAmount(decoded.absorption());
        player.setHealth(Math.min(decoded.health(), maximumHealth(player)));
        player.updateInventory();
        return true;
    }

    public String checksum(byte[] snapshot) {
        if (snapshot == null || snapshot.length == 0) {
            throw new IllegalArgumentException("snapshot must be present");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(snapshot));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Decoded decode(Player player, byte[] snapshot) {
        if (snapshot == null || snapshot.length == 0 || snapshot.length > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("staff snapshot has an invalid size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(snapshot))) {
            if (input.readInt() != MAGIC || input.readInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("staff snapshot schema is unsupported");
            }
            String serverId = input.readUTF();
            ItemStack[] inventory = ItemStack.deserializeItemsFromBytes(readBytes(input));
            int level = input.readInt();
            float experienceProgress = input.readFloat();
            int totalExperience = input.readInt();
            double health = input.readDouble();
            double absorption = input.readDouble();
            int food = input.readInt();
            float saturation = input.readFloat();
            float exhaustion = input.readFloat();
            int effectCount = input.readInt();
            if (effectCount < 0 || effectCount > MAX_EFFECTS) {
                throw new IllegalArgumentException("staff snapshot potion effect count is invalid");
            }
            List<PotionEffect> effects = new ArrayList<>(effectCount);
            for (int index = 0; index < effectCount; index++) {
                NamespacedKey key = NamespacedKey.fromString(input.readUTF());
                PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
                int duration = input.readInt();
                int amplifier = input.readInt();
                boolean ambient = input.readBoolean();
                boolean particles = input.readBoolean();
                boolean icon = input.readBoolean();
                if (type == null || duration < 0 || amplifier < 0) {
                    throw new IllegalArgumentException("staff snapshot contains an unavailable potion effect");
                }
                effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
            NamespacedKey worldKey = NamespacedKey.fromString(input.readUTF());
            World world = worldKey == null ? null : player.getServer().getWorld(worldKey);
            double x = input.readDouble();
            double y = input.readDouble();
            double z = input.readDouble();
            float yaw = input.readFloat();
            float pitch = input.readFloat();
            if (world == null) {
                throw new IllegalArgumentException("staff snapshot world is unavailable on this backend");
            }
            GameMode gameMode = GameMode.valueOf(input.readUTF());
            boolean allowFlight = input.readBoolean();
            boolean flying = input.readBoolean();
            float flySpeed = input.readFloat();
            float walkSpeed = input.readFloat();
            boolean invulnerable = input.readBoolean();
            boolean collidable = input.readBoolean();
            boolean canPickupItems = input.readBoolean();
            int fireTicks = input.readInt();
            int remainingAir = input.readInt();
            float fallDistance = input.readFloat();
            if (input.available() != 0 || inventory.length != player.getInventory().getContents().length
                    || level < 0 || experienceProgress < 0 || experienceProgress > 1 || totalExperience < 0
                    || health <= 0 || absorption < 0 || food < 0 || food > 20
                    || flySpeed < -1 || flySpeed > 1 || walkSpeed < -1 || walkSpeed > 1) {
                throw new IllegalArgumentException("staff snapshot values failed validation");
            }
            return new Decoded(
                    serverId, inventory, level, experienceProgress, totalExperience, health, absorption,
                    food, saturation, exhaustion, List.copyOf(effects), new Location(world, x, y, z, yaw, pitch),
                    gameMode, allowFlight, flying, flySpeed, walkSpeed, invulnerable, collidable,
                    canPickupItems, fireTicks, remainingAir, fallDistance
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Staff snapshot cannot be decoded safely", exception);
        }
    }

    private static void writeBytes(DataOutputStream output, byte[] bytes) throws IOException {
        if (bytes.length > MAX_ITEM_BYTES) {
            throw new IllegalStateException("staff inventory snapshot exceeds the safe size limit");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static double maximumHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            throw new IllegalStateException("player maximum-health attribute is unavailable");
        }
        return attribute.getValue();
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("staff inventory snapshot length is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IllegalArgumentException("staff inventory snapshot ended before its declared length");
        }
        return bytes;
    }

    public record Captured(int schemaVersion, byte[] snapshot, String checksum) {
        public Captured {
            snapshot = snapshot.clone();
        }

        @Override
        public byte[] snapshot() {
            return snapshot.clone();
        }
    }

    private record Decoded(
            String serverId,
            ItemStack[] inventory,
            int level,
            float experienceProgress,
            int totalExperience,
            double health,
            double absorption,
            int food,
            float saturation,
            float exhaustion,
            List<PotionEffect> effects,
            Location location,
            GameMode gameMode,
            boolean allowFlight,
            boolean flying,
            float flySpeed,
            float walkSpeed,
            boolean invulnerable,
            boolean collidable,
            boolean canPickupItems,
            int fireTicks,
            int remainingAir,
            float fallDistance
    ) {
    }
}
