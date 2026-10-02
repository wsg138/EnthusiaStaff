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
    private static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private static final float HALF_ROTATION = 180.0F;
    private static final float FULL_ROTATION = 360.0F;

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
                output.writeBoolean(StaffInvulnerabilityFlag.read(player));
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
        player.getInventory().setContents(decoded.inventory().toArray(ItemStack[]::new));
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

    /** Verifies actual runtime values, rather than requiring identical serializer byte ordering. */
    public String verifiedRestorationChecksum(Player player, String serverId, byte[] snapshot, String expectedChecksum) {
        if (!checksum(snapshot).equals(expectedChecksum)) {
            throw new IllegalStateException("saved staff snapshot integrity check failed");
        }
        Decoded expected = decode(player, snapshot);
        Captured captured = capture(player, serverId);
        Decoded actual = decode(player, captured.snapshot());
        if (!expected.equals(actual)) {
            throw new IllegalStateException("restored staff state differs in " + differingFields(expected, actual));
        }
        return expectedChecksum;
    }

    static List<String> differingFields(Decoded expected, Decoded actual) {
        List<String> differences = new ArrayList<>();
        for (var field : Decoded.class.getRecordComponents()) {
            try {
                if (!java.util.Objects.equals(field.getAccessor().invoke(expected), field.getAccessor().invoke(actual))) {
                    differences.add(field.getName());
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("staff restoration comparison is unavailable", exception);
            }
        }
        return List.copyOf(differences);
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
        validateSnapshotSize(snapshot);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(snapshot))) {
            validateHeader(input);
            Decoded decoded = readDecoded(player, input);
            validateDecoded(player, input, decoded);
            return decoded;
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Staff snapshot cannot be decoded safely", exception);
        }
    }

    private static void validateSnapshotSize(byte[] snapshot) {
        if (snapshot == null || snapshot.length == 0 || snapshot.length > MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("staff snapshot has an invalid size");
        }
    }

    private static void validateHeader(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC || input.readInt() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("staff snapshot schema is unsupported");
        }
    }

    private Decoded readDecoded(Player player, DataInputStream input) throws IOException {
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
        java.util.Set<PotionEffect> effects = java.util.Set.copyOf(readEffects(input));
        Location location = readLocation(player, input);
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
        return new Decoded(serverId, java.util.Arrays.asList(inventory), level, experienceProgress,
                totalExperience, health, absorption, food, saturation, exhaustion, effects, location,
                gameMode, allowFlight, flying, flySpeed, walkSpeed, invulnerable, collidable,
                canPickupItems, fireTicks, remainingAir, fallDistance);
    }

    private static List<PotionEffect> readEffects(DataInputStream input) throws IOException {
        int effectCount = input.readInt();
        if (effectCount < 0 || effectCount > MAX_EFFECTS) {
            throw new IllegalArgumentException("staff snapshot potion effect count is invalid");
        }
        List<PotionEffect> effects = new ArrayList<>(effectCount);
        for (int index = 0; index < effectCount; index++) {
            effects.add(readEffect(input));
        }
        return effects;
    }

    private static PotionEffect readEffect(DataInputStream input) throws IOException {
        NamespacedKey key = NamespacedKey.fromString(input.readUTF());
        PotionEffectType type = key == null ? null : Registry.MOB_EFFECT.get(key);
        int duration = input.readInt();
        int amplifier = input.readInt();
        boolean ambient = input.readBoolean();
        boolean particles = input.readBoolean();
        boolean icon = input.readBoolean();
        if (type == null) {
            throw new IllegalArgumentException("staff snapshot contains an unavailable potion effect");
        }
        validatePotionEffectValues(duration, amplifier);
        return new PotionEffect(type, duration, amplifier, ambient, particles, icon);
    }

    private static Location readLocation(Player player, DataInputStream input) throws IOException {
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
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static void validateDecoded(Player player, DataInputStream input, Decoded decoded) throws IOException {
        if (input.available() != 0) {
            throw new IllegalArgumentException("staff snapshot contains trailing data");
        }
        validateInventoryAndExperience(player, decoded);
        validateHealthAndFood(decoded);
        validateMovement(decoded);
    }

    private static void validateInventoryAndExperience(Player player, Decoded decoded) {
        if (decoded.inventory().size() != player.getInventory().getContents().length
                || decoded.level() < 0 || decoded.experienceProgress() < 0
                || decoded.experienceProgress() > 1 || decoded.totalExperience() < 0) {
            throw new IllegalArgumentException("staff snapshot values failed validation");
        }
    }

    private static void validateHealthAndFood(Decoded decoded) {
        if (decoded.health() <= 0 || decoded.absorption() < 0 || decoded.food() < 0 || decoded.food() > 20) {
            throw new IllegalArgumentException("staff snapshot values failed validation");
        }
    }

    private static void validateMovement(Decoded decoded) {
        if (decoded.flySpeed() < -1 || decoded.flySpeed() > 1
                || decoded.walkSpeed() < -1 || decoded.walkSpeed() > 1) {
            throw new IllegalArgumentException("staff snapshot values failed validation");
        }
    }

    static void validatePotionEffectValues(int duration, int amplifier) {
        if ((duration < 0 && duration != PotionEffect.INFINITE_DURATION) || amplifier < 0) {
            throw new IllegalArgumentException("staff snapshot contains invalid potion effect values");
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

    record Decoded(
            String serverId,
            List<ItemStack> inventory,
            int level,
            float experienceProgress,
            int totalExperience,
            double health,
            double absorption,
            int food,
            float saturation,
            float exhaustion,
            java.util.Set<PotionEffect> effects,
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
        Decoded {
            inventory = java.util.Collections.unmodifiableList(new ArrayList<>(inventory));
            effects = java.util.Set.copyOf(effects);
            location = location.clone();
            // Full rotations represent the same orientation after Bukkit teleport normalization.
            float yaw = location.getYaw() % FULL_ROTATION;
            if (yaw >= HALF_ROTATION) yaw -= FULL_ROTATION;
            if (yaw < -HALF_ROTATION) yaw += FULL_ROTATION;
            location.setYaw(yaw == 0 ? 0 : yaw);
        }
    }
}
