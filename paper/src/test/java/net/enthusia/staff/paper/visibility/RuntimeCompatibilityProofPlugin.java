package net.enthusia.staff.paper.visibility;

import io.papermc.paper.ServerBuildInfo;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class RuntimeCompatibilityProofPlugin extends JavaPlugin implements Listener {
    private static final Path EVIDENCE = Path.of("runtime-proof-evidence.txt");
    private final AtomicInteger joins = new AtomicInteger();
    private Plugin staffPlugin;
    private ClassLoader staffLoader;
    private Class<?> adapterClass;
    private Object reflectionAccess;
    private Object bypassAdapter;
    private Object bypassController;
    private Object badController;
    private Object vanishManager;
    private Object visibility;
    private Object rankAdmin;
    private Field noclipField;
    private Object originalNoclip;
    private Method reconcileManager;
    private Method setVanished;
    private Method reconcileController;
    private Method retireController;
    private boolean managerInjected;

    @Override
    public void onEnable() {
        try {
            try {
                Files.deleteIfExists(EVIDENCE);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not reset proof evidence", exception);
            }
            staffPlugin = requireStaffPlugin();
            staffLoader = staffPlugin.getClass().getClassLoader();
            inspectRuntime();
            prepareFrozenSeam();
            prepareManager();
            getServer().getPluginManager().registerEvents(this, this);
            evidence("HARNESS_READY=true");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            evidence("HARNESS_READY=false error=" + compact(exception));
            getLogger().severe("Runtime proof harness initialization failed: " + compact(exception));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void prepareRank(PlayerJoinEvent event) {
        event.getPlayer().addAttachment(this, "enthusiastaff.rank.admin", true);
        event.getPlayer().addAttachment(this, "enthusiastaff.vanish", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        int sequence = joins.incrementAndGet();
        Player player = event.getPlayer();
        evidence("JOIN|sequence=" + sequence + "|uuid=" + player.getUniqueId()
                + "|noPhysics=" + player.hasNoPhysics() + "|serverMode=" + player.getGameMode());
        if (sequence == 1) {
            later(20L, () -> firstSession(player));
        } else if (sequence == 2) {
            later(10L, () -> secondSession(player));
        }
    }

    private Plugin requireStaffPlugin() {
        Plugin plugin = getServer().getPluginManager().getPlugin("EnthusiaStaff");
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("Frozen EnthusiaStaff plugin is not enabled");
        }
        return plugin;
    }

    private void inspectRuntime() throws ReflectiveOperationException {
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        evidence("RUNTIME|brandId=" + info.brandId()
                + "|brandName=" + info.brandName()
                + "|minecraft=" + info.minecraftVersionId()
                + "|build=" + optionalInt(info.buildNumber())
                + "|gitCommit=" + info.gitCommit().orElse("missing")
                + "|buildTime=" + info.buildTime()
                + "|simple=" + info.asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE));
        inspectAbi("org.bukkit.craftbukkit.entity.CraftPlayer");
        inspectAbi("net.minecraft.server.level.ServerPlayer");
        inspectAbi("net.minecraft.server.network.ServerGamePacketListenerImpl");
        inspectAbi("net.minecraft.network.protocol.Packet");
        inspectAbi("net.minecraft.network.protocol.game.ClientboundGameEventPacket");
        Class<?> craftPlayer = load("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = load("net.minecraft.server.level.ServerPlayer");
        Class<?> listener = load("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> packet = load("net.minecraft.network.protocol.Packet");
        Class<?> gameEvent = load("net.minecraft.network.protocol.game.ClientboundGameEventPacket");
        Method getHandle = craftPlayer.getMethod("getHandle");
        Field connection = serverPlayer.getField("connection");
        Field change = gameEvent.getField("CHANGE_GAME_MODE");
        Constructor<?> constructor = gameEvent.getConstructor(change.getType(), float.class);
        Method send = listener.getMethod("send", packet);
        evidence("ABI_SIGNATURE|CraftPlayer#getHandle=" + getHandle.toGenericString());
        evidence("ABI_SIGNATURE|ServerPlayer#connection=" + connection.toGenericString());
        evidence("ABI_SIGNATURE|ClientboundGameEventPacket#CHANGE_GAME_MODE=" + change.toGenericString());
        evidence("ABI_SIGNATURE|ClientboundGameEventPacket#ctor=" + constructor.toGenericString());
        evidence("ABI_SIGNATURE|ServerGamePacketListenerImpl#send=" + send.toGenericString());
    }

    private void inspectAbi(String className) throws ReflectiveOperationException {
        Class<?> type = load(className);
        evidence("ABI_CLASS|" + className + "|sha256=" + classSha256(type));
    }

    private void prepareFrozenSeam() throws ReflectiveOperationException {
        adapterClass = staffLoader.loadClass(
                "net.enthusia.staff.paper.visibility.Paper26VanishClientGameModeAdapter");
        Method supports = adapterClass.getDeclaredMethod(
                "supportsRuntime", boolean.class, String.class, OptionalInt.class);
        supports.setAccessible(true);
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        Object supported = supports.invoke(null, true, info.minecraftVersionId(), info.buildNumber());
        evidence("PRODUCT_SUPPORTS=" + supported);

        Method install = adapterClass.getDeclaredMethod("install", Logger.class);
        install.setAccessible(true);
        Object productAdapter = install.invoke(null, getLogger());
        evidence("PRODUCT_INSTALL|class=" + productAdapter.getClass().getName()
                + "|available=" + invokeBoolean(productAdapter, "available")
                + "|reason=" + invokeString(productAdapter, "unavailableReason"));

        Method accessMethod = adapterClass.getDeclaredMethod("reflectionAccess");
        accessMethod.setAccessible(true);
        reflectionAccess = accessMethod.invoke(null);
        evidence("REFLECTION_ACCESS=true|class=" + reflectionAccess.getClass().getName());

        Constructor<?> adapterConstructor = adapterClass.getDeclaredConstructor(
                Logger.class, reflectionAccess.getClass());
        adapterConstructor.setAccessible(true);
        bypassAdapter = adapterConstructor.newInstance(getLogger(), reflectionAccess);
        bypassController = newController(bypassAdapter);

        Object badAccess = badReflectionAccess(reflectionAccess);
        Object badAdapter = adapterConstructor.newInstance(getLogger(), badAccess);
        badController = newController(badAdapter);
        evidence("BYPASS_ADAPTER_AVAILABLE=" + invokeBoolean(bypassAdapter, "available"));
    }

    private Object badReflectionAccess(Object access) throws ReflectiveOperationException {
        Class<?> type = access.getClass();
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] parts = new Object[5];
        String[] names = {"getHandle", "connection", "gameEventPacket", "changeGameMode"};
        for (int index = 0; index < names.length; index++) {
            Method accessor = type.getDeclaredMethod(names[index]);
            accessor.setAccessible(true);
            parts[index] = accessor.invoke(access);
        }
        parts[4] = Object.class.getMethod("toString");
        return constructor.newInstance(parts);
    }

    private Object newController(Object adapter) throws ReflectiveOperationException {
        Class<?> controller = staffLoader.loadClass(
                "net.enthusia.staff.paper.visibility.VanishNoclipController");
        Constructor<?> constructor = controller.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object instance = constructor.newInstance(adapter);
        reconcileController = controller.getDeclaredMethod("reconcile", Player.class, boolean.class);
        reconcileController.setAccessible(true);
        retireController = controller.getDeclaredMethod("retire", UUID.class);
        retireController.setAccessible(true);
        return instance;
    }

    private void prepareManager() throws ReflectiveOperationException {
        Field componentsField = staffPlugin.getClass().getDeclaredField("runtimeComponents");
        componentsField.setAccessible(true);
        Object components = componentsField.get(staffPlugin);
        Method vanishAccessor = components.getClass().getDeclaredMethod("vanish");
        vanishAccessor.setAccessible(true);
        vanishManager = vanishAccessor.invoke(components);
        Field visibilityField = vanishManager.getClass().getDeclaredField("visibility");
        visibilityField.setAccessible(true);
        visibility = visibilityField.get(vanishManager);
        Class<?> rankClass = staffLoader.loadClass("net.enthusia.staff.domain.auth.StaffRank");
        rankAdmin = rankClass.getField("ADMIN").get(null);
        setVanished = visibility.getClass().getMethod(
                "setVanished", UUID.class, rankClass, boolean.class);
        reconcileManager = vanishManager.getClass().getDeclaredMethod("reconcileNoclip", Player.class);
        reconcileManager.setAccessible(true);
        noclipField = vanishManager.getClass().getDeclaredField("noclip");
        noclipField.setAccessible(true);
        originalNoclip = noclipField.get(vanishManager);

        Field issuesField = staffPlugin.getClass().getDeclaredField("featureIssues");
        issuesField.setAccessible(true);
        Object issues = issuesField.get(staffPlugin);
        evidence("STAFF_PLUGIN|enabled=" + staffPlugin.isEnabled() + "|featureIssues=" + issues);
    }

    private void firstSession(Player player) {
        try {
            preparePlayer(player);
            productProbe(player);
            later(20L, () -> restoreProductProbe(player));
            later(40L, () -> beginBypassMatrix(player));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("FIRST_SESSION", exception);
        }
    }

    private void productProbe(Player player) throws ReflectiveOperationException {
        setVanish(player, true);
        boolean reconciled = invokeBoolean(reconcileManager, vanishManager, player);
        evidence("PRODUCT_MANAGER|reconciled=" + reconciled
                + "|noPhysics=" + player.hasNoPhysics() + "|serverMode=" + player.getGameMode());
        later(4L, () -> marker(player, "PRODUCT_PROBE"));
    }

    private void restoreProductProbe(Player player) {
        try {
            setVanish(player, false);
            boolean reconciled = invokeBoolean(reconcileManager, vanishManager, player);
            evidence("PRODUCT_RESTORE|reconciled=" + reconciled
                    + "|noPhysics=" + player.hasNoPhysics() + "|serverMode=" + player.getGameMode());
            later(4L, () -> marker(player, "PRODUCT_RESTORED"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("PRODUCT_RESTORE", exception);
        }
    }

    private void beginBypassMatrix(Player player) {
        try {
            injectBypass();
            Location base = setupChamber(player);
            setVanish(player, true);
            boolean active = reconcile(player, true);
            evidence("ACTIVE|reconciled=" + active + "|noPhysics=" + player.hasNoPhysics()
                    + "|serverMode=" + player.getGameMode());
            for (long tick : new long[]{1L, 2L, 5L, 10L, 15L}) {
                later(tick, () -> evidence("PHYSICS_STABILITY|tick=" + tick
                        + "|noPhysics=" + player.hasNoPhysics()
                        + "|serverMode=" + player.getGameMode()));
            }
            later(2L, () -> marker(player, "ACTIVE:"
                    + base.getX() + ":" + base.getY() + ":" + base.getZ()));
            later(16L, () -> movementSample(
                    player, "WALL", player.getLocation().getX() > base.getX() + 1.5D));
            later(36L, () -> movementSample(
                    player, "FLOOR", player.getLocation().getY() < base.getY() - 1.5D));
            later(56L, () -> movementSample(
                    player, "CEILING", player.getLocation().getY() > base.getY() + 2.5D));
            later(70L, () -> teleportWhileActive(player));
            later(90L, () -> restoreMatrix(player));
            later(110L, () -> forcedFailure(player));
            later(130L, () -> reconnectWhileActive(player));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("BYPASS_START", exception);
        }
    }

    private void injectBypass() {
        try {
            noclipField.set(vanishManager, bypassController);
            managerInjected = noclipField.get(vanishManager) == bypassController;
        } catch (IllegalAccessException exception) {
            managerInjected = false;
            evidence("MANAGER_INJECTION_ERROR=" + compact(exception));
        }
        evidence("MANAGER_INJECTION=" + managerInjected);
    }

    private Location setupChamber(Player player) {
        Location current = player.getLocation();
        Location base = new Location(
                player.getWorld(),
                current.getBlockX() + 0.5D,
                current.getBlockY(),
                current.getBlockZ() + 0.5D
        );
        if (!player.teleport(base)) {
            throw new IllegalStateException("Could not center protocol client for movement chamber");
        }
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int y = by - 2; y <= by + 3; y++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                player.getWorld().getBlockAt(bx + 1, y, z).setType(Material.STONE, false);
            }
        }
        for (int x = bx - 1; x <= bx + 4; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                player.getWorld().getBlockAt(x, by - 1, z).setType(Material.STONE, false);
                player.getWorld().getBlockAt(x, by + 2, z).setType(Material.STONE, false);
            }
        }
        evidence("CHAMBER_READY=true|x=" + base.getX() + "|y=" + base.getY() + "|z=" + base.getZ());
        return base;
    }

    private void movementSample(Player player, String name, boolean passed) {
        evidence("MOVEMENT|" + name + "|pass=" + passed
                + "|x=" + player.getLocation().getX()
                + "|y=" + player.getLocation().getY()
                + "|z=" + player.getLocation().getZ()
                + "|noPhysics=" + player.hasNoPhysics());
    }

    private void teleportWhileActive(Player player) {
        try {
            player.teleport(new Location(player.getWorld(), 10.5D, 100.0D, 0.5D));
            later(4L, () -> {
                try {
                    boolean reconciled = reconcile(player, true);
                    evidence("TELEPORT_ACTIVE|reconciled=" + reconciled
                            + "|noPhysics=" + player.hasNoPhysics()
                            + "|serverMode=" + player.getGameMode()
                            + "|x=" + player.getLocation().getX());
                    marker(player, "TELEPORT_ACTIVE");
                } catch (ReflectiveOperationException exception) {
                    fail("TELEPORT_ACTIVE", exception);
                }
            });
        } catch (RuntimeException exception) {
            fail("TELEPORT_ACTIVE", exception);
        }
    }

    private void restoreMatrix(Player player) {
        try {
            setVanish(player, false);
            boolean restored = reconcile(player, false);
            evidence("RESTORE|reconciled=" + restored + "|noPhysics=" + player.hasNoPhysics()
                    + "|serverMode=" + player.getGameMode());
            later(4L, () -> marker(player, "RESTORED"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("RESTORE", exception);
        }
    }

    private void forcedFailure(Player player) {
        try {
            player.setNoPhysics(false);
            boolean result = invokeBoolean(reconcileController, badController, player, true);
            Field clientModesField = badController.getClass().getDeclaredField("clientModes");
            clientModesField.setAccessible(true);
            boolean available = invokeBoolean(clientModesField.get(badController), "available");
            evidence("FORCED_FAILURE|reconciled=" + result + "|adapterAvailable=" + available
                    + "|noPhysics=" + player.hasNoPhysics());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("FORCED_FAILURE", exception);
        }
    }

    private void reconnectWhileActive(Player player) {
        try {
            setVanish(player, true);
            boolean active = reconcile(player, true);
            evidence("RECONNECT_PREP|reconciled=" + active + "|noPhysics=" + player.hasNoPhysics());
            later(4L, () -> marker(player, "RECONNECT_ACTIVE"));
            later(20L, () -> {
                try {
                    if (!managerInjected) {
                        retireController.invoke(bypassController, player.getUniqueId());
                    }
                    player.kick(Component.text("runtime-proof reconnect"));
                } catch (ReflectiveOperationException exception) {
                    fail("RECONNECT_KICK", exception);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("RECONNECT_PREP", exception);
        }
    }

    private void secondSession(Player player) {
        try {
            preparePlayer(player);
            evidence("RECONNECT_JOIN_STATE|noPhysics=" + player.hasNoPhysics()
                    + "|serverMode=" + player.getGameMode());
            setVanish(player, true);
            boolean restored = reconcile(player, true);
            evidence("RECONNECT_RESTORED|reconciled=" + restored
                    + "|noPhysics=" + player.hasNoPhysics() + "|serverMode=" + player.getGameMode());
            later(4L, () -> marker(player, "RECONNECT_RESTORED"));
            later(30L, () -> secondUnvanish(player));
            later(55L, () -> disablePreparation(player));
            later(75L, () -> pluginDisableCleanup(player));
            later(105L, () -> getServer().dispatchCommand(getServer().getConsoleSender(), "stop"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("SECOND_SESSION", exception);
        }
    }

    private void secondUnvanish(Player player) {
        try {
            setVanish(player, false);
            boolean restored = reconcile(player, false);
            evidence("RECONNECT_UNVANISHED|reconciled=" + restored
                    + "|noPhysics=" + player.hasNoPhysics() + "|serverMode=" + player.getGameMode());
            later(4L, () -> marker(player, "RECONNECT_UNVANISHED"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("RECONNECT_UNVANISH", exception);
        }
    }

    private void disablePreparation(Player player) {
        try {
            setVanish(player, true);
            boolean active = reconcile(player, true);
            evidence("DISABLE_PREP|reconciled=" + active + "|noPhysics=" + player.hasNoPhysics());
            later(4L, () -> marker(player, "DISABLE_ACTIVE"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("DISABLE_PREP", exception);
        }
    }

    private void pluginDisableCleanup(Player player) {
        try {
            Method handler = vanishManager.getClass().getMethod("onPluginDisable", PluginDisableEvent.class);
            handler.invoke(vanishManager, new PluginDisableEvent(staffPlugin));
            evidence("PLUGIN_DISABLE_CLEANUP|noPhysics=" + player.hasNoPhysics()
                    + "|serverMode=" + player.getGameMode());
            setVanish(player, false);
            if (managerInjected) {
                noclipField.set(vanishManager, originalNoclip);
            }
            later(4L, () -> marker(player, "DISABLE_CLEANED"));
            later(8L, () -> evidence("DONE=true"));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            fail("PLUGIN_DISABLE", exception);
        }
    }

    private void preparePlayer(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        if (!player.hasNoPhysics()) {
            player.setNoPhysics(false);
        }
    }

    private boolean reconcile(Player player, boolean vanished) throws ReflectiveOperationException {
        if (managerInjected) {
            return invokeBoolean(reconcileManager, vanishManager, player);
        }
        return invokeBoolean(reconcileController, bypassController, player, vanished);
    }

    private void setVanish(Player player, boolean vanished) throws ReflectiveOperationException {
        if (managerInjected || noclipField.get(vanishManager) == originalNoclip) {
            setVanished.invoke(visibility, player.getUniqueId(), rankAdmin, vanished);
        }
    }

    private void marker(Player player, String name) {
        if (player.isOnline()) {
            player.sendMessage(Component.text("RTPROOF:" + name));
        }
    }

    private void later(long ticks, Runnable operation) {
        getServer().getScheduler().runTaskLater(this, operation, ticks);
    }

    private Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, getClass().getClassLoader());
    }

    private String classSha256(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            if (stream == null) {
                return "missing";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(stream.readAllBytes()));
        } catch (Exception exception) {
            return "error-" + exception.getClass().getSimpleName();
        }
    }

    private static String optionalInt(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "missing";
    }

    private boolean invokeBoolean(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(target);
    }

    private String invokeString(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return String.valueOf(method.invoke(target));
    }

    private static boolean invokeBoolean(Method method, Object target, Object... arguments)
            throws ReflectiveOperationException {
        return (boolean) method.invoke(target, arguments);
    }

    private void fail(String phase, Exception exception) {
        evidence("FAIL|" + phase + "|error=" + compact(exception));
        getLogger().severe("Runtime proof phase " + phase + " failed: " + compact(exception));
    }

    private static String compact(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getName() + ":" + String.valueOf(current.getMessage()).replace('\n', ' ');
    }

    private synchronized void evidence(String line) {
        try {
            Files.writeString(
                    EVIDENCE,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            getLogger().info("[RTPROOF] " + line);
        } catch (Exception exception) {
            getLogger().severe("Could not write runtime proof evidence: " + compact(exception));
        }
    }
}
