package net.enthusia.staff.paper.tester;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class CheatTesterProbeEngine {
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 0.0001D;
    private static final double MIN_AIM_VECTOR_LENGTH_SQUARED = 0.000001D;
    private static final double FALLING_VELOCITY_THRESHOLD = -0.05D;
    private static final float PREVIOUS_FALL_THRESHOLD = 2.0F;
    private static final float FALL_RESET_THRESHOLD = 0.25F;

    private final JavaPlugin plugin;
    private final CheatTesterSettings settings;
    private final FakeEntityAdapter fakeEntities;
    private final CheatTesterFakeEntityState fakeEntityState;
    private final Predicate<CheatTesterSession> sampleActive;
    private final BiConsumer<CheatTesterSession, String> retirement;
    private final Map<CheatTesterType, ProbeStarter> starters;

    CheatTesterProbeEngine(
            JavaPlugin plugin,
            CheatTesterSettings settings,
            FakeEntityAdapter fakeEntities,
            CheatTesterFakeEntityState fakeEntityState,
            Predicate<CheatTesterSession> sampleActive,
            BiConsumer<CheatTesterSession, String> retirement
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.fakeEntities = Objects.requireNonNull(fakeEntities, "fakeEntities");
        this.fakeEntityState = Objects.requireNonNull(fakeEntityState, "fakeEntityState");
        this.sampleActive = Objects.requireNonNull(sampleActive, "sampleActive");
        this.retirement = Objects.requireNonNull(retirement, "retirement");
        this.starters = createStarters();
    }

    CheatTesterSession.PreparedProbe prepare(Player target, CheatTesterType type) {
        if (target.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            target.closeInventory();
        }
        return switch (type) {
            case TOTEM_REFILL -> prepareTotem(target.getInventory());
            case AUTO_ARMOR -> prepareArmor(target.getInventory());
            case VELOCITY, NO_FALL, FAKE_ENTITY -> CheatTesterSession.PreparedProbe.NONE;
        };
    }

    void begin(Player target, CheatTesterSession session) {
        ProbeStarter starter = Objects.requireNonNull(starters.get(session.type), "cheat tester probe starter");
        starter.start(target, session);
    }

    void hideFake(Player target, CheatTesterSession session) {
        FakeEntityAdapter.Handle handle = session.fakeHandle;
        if (handle == null) {
            return;
        }
        fakeEntityState.remove(session);
        fakeEntities.destroy(target, handle);
        scheduleHideForStaff(session);
    }

    void scheduleHideForStaff(CheatTesterSession session) {
        FakeEntityAdapter.Handle handle = session.fakeHandle;
        if (handle == null) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player staff = plugin.getServer().getPlayer(session.staffId);
            if (staff != null) {
                staff.getScheduler().execute(plugin, () -> fakeEntities.destroy(staff, handle), null, 1L);
            }
        });
    }

    private Map<CheatTesterType, ProbeStarter> createStarters() {
        EnumMap<CheatTesterType, ProbeStarter> configured = new EnumMap<>(CheatTesterType.class);
        configured.put(CheatTesterType.TOTEM_REFILL, this::beginTotem);
        configured.put(CheatTesterType.AUTO_ARMOR, this::beginArmor);
        configured.put(CheatTesterType.VELOCITY, (target, ignored) -> beginVelocity(target));
        configured.put(CheatTesterType.NO_FALL, this::beginNoFall);
        configured.put(CheatTesterType.FAKE_ENTITY, this::beginFake);
        return Map.copyOf(configured);
    }

    private static CheatTesterSession.PreparedProbe prepareTotem(PlayerInventory inventory) {
        int totemSlot = firstMaterial(inventory.getStorageContents(), Material.TOTEM_OF_UNDYING);
        if (totemSlot < 0) {
            throw new IllegalStateException("Target needs a totem in normal inventory for a no-injection refill probe");
        }
        return new CheatTesterSession.PreparedProbe(totemSlot, -1, -1);
    }

    private static CheatTesterSession.PreparedProbe prepareArmor(PlayerInventory inventory) {
        int armorSlot = firstNonEmpty(inventory.getArmorContents());
        int storageSlot = firstEmpty(inventory.getStorageContents());
        if (armorSlot < 0 || storageSlot < 0) {
            throw new IllegalStateException(
                    "Target needs equipped armor and one empty inventory slot for an exact-restore armor probe"
            );
        }
        return new CheatTesterSession.PreparedProbe(-1, armorSlot, storageSlot);
    }

    private void beginTotem(Player target, CheatTesterSession session) {
        PlayerInventory inventory = target.getInventory();
        int source = session.probe.sourceSlot();
        ItemStack[] storage = inventory.getStorageContents();
        if (!validTotemSource(storage, source)) {
            throw new IllegalStateException("The prepared totem source changed before probe start");
        }
        inventory.setItemInOffHand(new ItemStack(Material.AIR));
        target.updateInventory();
    }

    private static boolean validTotemSource(ItemStack[] storage, int source) {
        return source >= 0 && source < storage.length && storage[source] != null
                && storage[source].getType() == Material.TOTEM_OF_UNDYING;
    }

    private void beginArmor(Player target, CheatTesterSession session) {
        PlayerInventory inventory = target.getInventory();
        ItemStack[] armor = inventory.getArmorContents();
        ItemStack[] storage = inventory.getStorageContents();
        int armorSlot = session.probe.armorSlot();
        int storageSlot = session.probe.storageSlot();
        validateArmorIndices(armor, storage, armorSlot, storageSlot);
        validateArmorItems(armor, storage, armorSlot, storageSlot);
        storage[storageSlot] = armor[armorSlot].clone();
        armor[armorSlot] = new ItemStack(Material.AIR);
        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        target.updateInventory();
    }

    private static void validateArmorIndices(ItemStack[] armor, ItemStack[] storage, int armorSlot, int storageSlot) {
        if (armorSlot < 0 || armorSlot >= armor.length || storageSlot < 0 || storageSlot >= storage.length) {
            throw new IllegalStateException("The prepared armor slots changed before probe start");
        }
    }

    private static void validateArmorItems(ItemStack[] armor, ItemStack[] storage, int armorSlot, int storageSlot) {
        if (armor[armorSlot] == null || armor[armorSlot].isEmpty()) {
            throw new IllegalStateException("The prepared armor item changed before probe start");
        }
        if (storage[storageSlot] != null && !storage[storageSlot].isEmpty()) {
            throw new IllegalStateException("The prepared armor destination changed before probe start");
        }
    }

    private void beginVelocity(Player target) {
        Vector horizontal = target.getLocation().getDirection().setY(0.0D);
        if (horizontal.lengthSquared() < MIN_DIRECTION_LENGTH_SQUARED) {
            horizontal = new Vector(1.0D, 0.0D, 0.0D);
        } else {
            horizontal.normalize();
        }
        target.setVelocity(horizontal.multiply(settings.velocityHorizontal()).setY(settings.velocityVertical()));
    }

    private void beginNoFall(Player target, CheatTesterSession session) {
        target.setFallDistance(0.0F);
        target.setVelocity(new Vector(0.0D, settings.noFallVertical(), 0.0D));
        session.sampleTask = target.getScheduler().runAtFixedRate(
                plugin,
                ignored -> sampleNoFall(target, session),
                () -> retirement.accept(session, "Target retired during no-fall sampling"),
                1L,
                1L
        );
    }

    private void sampleNoFall(Player target, CheatTesterSession session) {
        if (!sampleActive.test(session)) {
            cancel(session.sampleTask);
            return;
        }
        float current = target.getFallDistance();
        session.maxFallDistance = Math.max(session.maxFallDistance, current);
        if (fallResetObserved(target, session, current)) {
            session.airborneFallResets.incrementAndGet();
        }
        session.previousFallDistance = current;
    }

    private static boolean fallResetObserved(Player target, CheatTesterSession session, float current) {
        return target.getVelocity().getY() < FALLING_VELOCITY_THRESHOLD
                && session.previousFallDistance > PREVIOUS_FALL_THRESHOLD
                && current < FALL_RESET_THRESHOLD;
    }

    private void beginFake(Player target, CheatTesterSession session) {
        if (!fakeEntities.available() || session.fakeHandle == null) {
            throw new IllegalStateException("ProtocolLib fake-entity support became unavailable");
        }
        Location location = fakeLocation(target);
        session.fakeLocation = CheatTesterSession.StartPoint.capture(location);
        fakeEntityState.track(session.fakeHandle, session.targetId);
        fakeEntities.show(target, session.fakeHandle, location);
        scheduleShowForStaff(session, location);
        session.sampleTask = target.getScheduler().runAtFixedRate(
                plugin,
                ignored -> sampleFakeAim(target, session),
                () -> retirement.accept(session, "Target retired during fake-entity aim sampling"),
                1L,
                1L
        );
    }

    private void sampleFakeAim(Player target, CheatTesterSession session) {
        if (!sampleActive.test(session)) {
            cancel(session.sampleTask);
            return;
        }
        CheatTesterSession.StartPoint fake = session.fakeLocation;
        if (fake == null || !target.getWorld().getUID().equals(fake.worldId())) {
            return;
        }
        Location eye = target.getEyeLocation();
        Vector toFake = new Vector(fake.x() - eye.getX(), fake.y() + 1.0D - eye.getY(), fake.z() - eye.getZ());
        if (toFake.lengthSquared() < MIN_AIM_VECTOR_LENGTH_SQUARED) {
            session.minimumAimAngleDegrees = 0.0D;
            return;
        }
        updateMinimumAimAngle(session, eye.getDirection(), toFake);
    }

    private static void updateMinimumAimAngle(CheatTesterSession session, Vector look, Vector toFake) {
        if (look.lengthSquared() < MIN_AIM_VECTOR_LENGTH_SQUARED) {
            return;
        }
        double dot = Math.max(-1.0D, Math.min(1.0D, look.normalize().dot(toFake.normalize())));
        session.minimumAimAngleDegrees = Math.min(session.minimumAimAngleDegrees, Math.toDegrees(Math.acos(dot)));
    }

    private void scheduleShowForStaff(CheatTesterSession session, Location location) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player staff = plugin.getServer().getPlayer(session.staffId);
            if (staff == null || !staff.isOnline()) {
                return;
            }
            Location copy = location.clone();
            staff.getScheduler().execute(plugin, () -> showFakeToStaff(staff, session, copy), null, 1L);
        });
    }

    private void showFakeToStaff(Player staff, CheatTesterSession session, Location location) {
        if (!sampleActive.test(session) || session.fakeHandle == null) {
            return;
        }
        if (fakeEntities.available() && staff.getWorld().equals(location.getWorld())) {
            fakeEntities.show(staff, session.fakeHandle, location);
        }
    }

    private Location fakeLocation(Player target) {
        Location base = target.getLocation();
        Vector direction = base.getDirection().setY(0.0D);
        if (direction.lengthSquared() < MIN_DIRECTION_LENGTH_SQUARED) {
            direction = new Vector(0.0D, 0.0D, 1.0D);
        } else {
            direction.normalize();
        }
        return base.clone().add(direction.multiply(settings.fakeEntityDistance()));
    }

    private static int firstMaterial(ItemStack[] items, Material material) {
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (item != null && !item.isEmpty() && item.getType() == material) {
                return index;
            }
        }
        return -1;
    }

    private static int firstNonEmpty(ItemStack[] items) {
        for (int index = 0; index < items.length; index++) {
            if (items[index] != null && !items[index].isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static int firstEmpty(ItemStack[] items) {
        for (int index = 0; index < items.length; index++) {
            if (items[index] == null || items[index].isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static void cancel(ScheduledTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (RuntimeException ignored) {
                // The durable journal remains authoritative.
            }
        }
    }

    @FunctionalInterface
    private interface ProbeStarter {
        void start(Player target, CheatTesterSession session);
    }
}
