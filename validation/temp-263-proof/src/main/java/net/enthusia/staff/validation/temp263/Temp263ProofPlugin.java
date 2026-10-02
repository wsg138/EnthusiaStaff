package net.enthusia.staff.validation.temp263;

import io.papermc.paper.ServerBuildInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Disposable proof harness only. Product classes are loaded from the frozen
 * EnthusiaStaff JAR and are never copied or changed here.
 */
public final class Temp263ProofPlugin extends JavaPlugin implements Listener {
    private static final String EXPECTED_MINECRAFT = "26.3";
    private static final int EXPECTED_BUILD = 134;

    private Object exactAdapter;
    private Object noclipController;
    private Object syntheticVanishManager;
    private ExecutorService syntheticWorkers;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Plugin staff = staffPlugin();
        getLogger().info("VPROOF_STARTUP staff_present=" + (staff != null)
                + " staff_enabled=" + (staff != null && staff.isEnabled())
                + " staff_version=" + (staff == null ? "missing" : staff.getPluginMeta().getVersion()));
    }

    @Override
    public void onDisable() {
        if (syntheticWorkers != null) {
            syntheticWorkers.shutdownNow();
        }
        getLogger().info("VPROOF_HARNESS_DISABLED");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onProofCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.equalsIgnoreCase("/vproof") && !message.toLowerCase(java.util.Locale.ROOT).startsWith("/vproof ")) {
            return;
        }
        event.setCancelled(true);
        String tail = message.length() > 7 ? message.substring(7).trim() : "";
        String[] args = tail.isEmpty() ? new String[0] : tail.split("\\s+");
        runProof(event.getPlayer(), args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("vproof requires a real protocol player");
            return true;
        }
        runProof(player, args);
        return true;
    }

    private void runProof(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("usage: /vproof <rawphysics|abi|baseline|begin|status|teleport|geometry|checkpos|disable|forced-failure|plugin-disable|finalize>");
            return;
        }
        try {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "abi" -> proveAbi(player);
                case "baseline" -> proveBaselineOwnership(player);
                case "begin" -> beginVanish(player);
                case "status" -> status(player, args.length > 1 ? args[1] : "current");
                case "teleport" -> teleportWhileVanished(player);
                case "geometry" -> geometry(player, requireArg(args, 1, "geometry case"));
                case "checkpos" -> position(player, requireArg(args, 1, "position case"));
                case "disable" -> disableVanish(player);
                case "forced-failure" -> forcedFailure(player);
                case "plugin-disable" -> pluginDisable(player);
                case "finalize" -> finalizeProof(player);
                default -> player.sendMessage("unknown vproof action");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().severe("VPROOF_FAILURE action=" + args[0] + " type="
                    + exception.getClass().getName() + " message=" + exception.getMessage());
            exception.printStackTrace();
            player.sendMessage("VPROOF_FAILURE action=" + args[0] + " reason=" + exception.getClass().getSimpleName());
        }
    }

    private void rawPhysics(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setNoPhysics(false);
        player.setNoPhysics(true);
        mark(player, "VPROOF_RAW_NO_PHYSICS_IMMEDIATE server_mode="
                + player.getGameMode() + " noPhysics=" + player.hasNoPhysics());
        getServer().getScheduler().runTaskLater(this, () -> {
            boolean retained = player.hasNoPhysics();
            mark(player, "VPROOF_RAW_NO_PHYSICS_AFTER_1_TICK retained=" + retained
                    + " server_mode=" + player.getGameMode()
                    + " noPhysics=" + player.hasNoPhysics());
            player.setNoPhysics(false);
        }, 1L);
    }

    private void proveAbi(Player player) throws ReflectiveOperationException {
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        OptionalInt build = info.buildNumber();
        require(ServerBuildInfo.BRAND_PAPER_ID.equals(info.brandId()), "brand is not Paper");
        require(EXPECTED_MINECRAFT.equals(info.minecraftVersionId()),
                "minecraft version mismatch: " + info.minecraftVersionId());
        require(build.isPresent() && build.getAsInt() == EXPECTED_BUILD,
                "Paper build mismatch: " + (build.isPresent() ? build.getAsInt() : "missing"));

        ClassLoader loader = frozenLoader();
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> listener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> gameEvent = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundGameEventPacket");

        Method getHandle = craftPlayer.getMethod("getHandle");
        Field connection = serverPlayer.getField("connection");
        Field changeGameMode = gameEvent.getField("CHANGE_GAME_MODE");
        Constructor<?> packetConstructor = gameEvent.getConstructor(changeGameMode.getType(), float.class);
        Method send = listener.getMethod("send", packet);

        Class<?> adapterClass = Class.forName(
                "net.enthusia.staff.paper.visibility.Paper26VanishClientGameModeAdapter", true, loader);
        Method reflectionAccess = adapterClass.getDeclaredMethod("reflectionAccess");
        reflectionAccess.setAccessible(true);
        Object access = reflectionAccess.invoke(null);
        require(access != null, "frozen reflectionAccess returned null");

        Method install = adapterClass.getDeclaredMethod("install", Logger.class);
        install.setAccessible(true);
        Object guarded = install.invoke(null, getLogger());
        Method guardedAvailableMethod = guarded.getClass().getMethod("available");
        guardedAvailableMethod.setAccessible(true);
        boolean guardedAvailable = (boolean) guardedAvailableMethod.invoke(guarded);
        require(!guardedAvailable, "frozen 26.2 exact-build guard unexpectedly accepted Paper 26.3");

        exactAdapter = instantiateAdapter(adapterClass, access);
        require(adapterAvailable(exactAdapter), "direct frozen ABI adapter did not initialize");
        noclipController = instantiateController(exactAdapter);
        ensureSyntheticManager(player);

        mark(player, "VPROOF_ABI_OK brand=" + info.brandId()
                + " minecraft=" + info.minecraftVersionId()
                + " build=" + build.getAsInt()
                + " simple=" + sanitize(info.asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE))
                + " getHandle=" + getHandle
                + " connection=" + connection.getType().getName()
                + " packetCtor=" + packetConstructor
                + " send=" + send
                + " guarded_26_2=false");
    }

    private void proveBaselineOwnership(Player player) throws ReflectiveOperationException {
        ensureRuntime(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setNoPhysics(true);
        require(reconcile(player, true), "baseline-true enable failed");
        require(player.hasNoPhysics(), "noPhysics cleared during enable");
        require(reconcile(player, false), "baseline-true disable failed");
        require(player.hasNoPhysics(), "pre-existing noPhysics=true baseline was not preserved");
        player.setNoPhysics(false);
        invokeController("retire", new Class<?>[]{UUID.class}, player.getUniqueId());
        mark(player, "VPROOF_BASELINE_OK baseline_true_preserved=true reset_for_main=true");
    }

    private void beginVanish(Player player) throws ReflectiveOperationException {
        ensureRuntime(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.setNoPhysics(false);
        require(reconcile(player, true), "frozen controller could not enable");
        require(player.getGameMode() == GameMode.SURVIVAL, "server game mode mutated");
        require(player.hasNoPhysics(), "server noPhysics was not enabled");
        mark(player, "VPROOF_BEGIN_OK server_mode=SURVIVAL noPhysics=true");
    }

    private void disableVanish(Player player) throws ReflectiveOperationException {
        ensureRuntime(player);
        require(reconcile(player, false), "frozen controller could not disable");
        require(player.getGameMode() == GameMode.SURVIVAL, "server game mode changed during disable");
        require(!player.hasNoPhysics(), "server noPhysics baseline was not restored");
        mark(player, "VPROOF_DISABLE_OK server_mode=SURVIVAL noPhysics=false");
    }

    private void teleportWhileVanished(Player player) throws ReflectiveOperationException {
        ensureRuntime(player);
        Location destination = new Location(player.getWorld(), 140.5, 82.0, 140.5, 0.0f, 0.0f);
        require(player.teleport(destination), "server teleport failed");
        require(reconcile(player, true), "post-teleport no-clip reconciliation failed");
        require(player.getGameMode() == GameMode.SURVIVAL, "server mode changed after teleport");
        require(player.hasNoPhysics(), "noPhysics was lost after teleport");
        mark(player, "VPROOF_TELEPORT_OK x=" + fmt(player.getX())
                + " y=" + fmt(player.getY()) + " z=" + fmt(player.getZ())
                + " server_mode=SURVIVAL noPhysics=true");
    }

    private void geometry(Player player, String geometryCase) throws ReflectiveOperationException {
        ensureRuntime(player);
        require(reconcile(player, true), "geometry requires enabled no-clip");
        World world = player.getWorld();
        clearBox(world, 190, 72, 190, 230, 92, 210);
        switch (geometryCase) {
            case "wall" -> {
                for (int y = 79; y <= 83; y++) {
                    world.getBlockAt(202, y, 200).setType(Material.STONE, false);
                }
                player.teleport(new Location(world, 200.5, 80.0, 200.5, -90.0f, 0.0f));
                mark(player, "VPROOF_GEOMETRY_READY case=wall target=204.5,80.0,200.5");
            }
            case "floor" -> {
                for (int x = 209; x <= 211; x++) {
                    for (int z = 199; z <= 201; z++) {
                        world.getBlockAt(x, 79, z).setType(Material.STONE, false);
                    }
                }
                player.teleport(new Location(world, 210.5, 81.5, 200.5, 0.0f, 90.0f));
                mark(player, "VPROOF_GEOMETRY_READY case=floor target=210.5,76.5,200.5");
            }
            case "ceiling" -> {
                for (int x = 219; x <= 221; x++) {
                    for (int z = 199; z <= 201; z++) {
                        world.getBlockAt(x, 82, z).setType(Material.STONE, false);
                    }
                }
                player.teleport(new Location(world, 220.5, 78.0, 200.5, 0.0f, -90.0f));
                mark(player, "VPROOF_GEOMETRY_READY case=ceiling target=220.5,85.0,200.5");
            }
            default -> throw new IllegalArgumentException("unknown geometry case " + geometryCase);
        }
        require(reconcile(player, true), "post-geometry teleport reconciliation failed");
    }

    private void position(Player player, String label) {
        mark(player, "VPROOF_POSITION case=" + label
                + " x=" + fmt(player.getX()) + " y=" + fmt(player.getY()) + " z=" + fmt(player.getZ())
                + " server_mode=" + player.getGameMode() + " noPhysics=" + player.hasNoPhysics());
    }

    private void status(Player player, String label) {
        mark(player, "VPROOF_STATUS label=" + label
                + " server_mode=" + player.getGameMode()
                + " noPhysics=" + player.hasNoPhysics()
                + " staff_enabled=" + (staffPlugin() != null && staffPlugin().isEnabled()));
    }

    private void forcedFailure(Player player) throws ReflectiveOperationException {
        ClassLoader loader = frozenLoader();
        Class<?> adapterClass = Class.forName(
                "net.enthusia.staff.paper.visibility.Paper26VanishClientGameModeAdapter", true, loader);
        Method reflectionAccess = adapterClass.getDeclaredMethod("reflectionAccess");
        reflectionAccess.setAccessible(true);
        Object access = reflectionAccess.invoke(null);
        Class<?> accessClass = access.getClass();

        Method getHandle = (Method) recordValue(access, "getHandle");
        Field connection = (Field) recordValue(access, "connection");
        Constructor<?> packetConstructor = (Constructor<?>) recordValue(access, "gameEventPacket");
        Object changeGameMode = recordValue(access, "changeGameMode");
        Method deliberatelyWrongSend = String.class.getMethod("length");

        Constructor<?> accessConstructor = accessClass.getDeclaredConstructors()[0];
        accessConstructor.setAccessible(true);
        Object brokenAccess = accessConstructor.newInstance(
                getHandle, connection, packetConstructor, changeGameMode, deliberatelyWrongSend);
        Object brokenAdapter = instantiateAdapter(adapterClass, brokenAccess);

        Method present = adapterClass.getMethod("present", Player.class, GameMode.class);
        present.setAccessible(true);
        boolean first = (boolean) present.invoke(brokenAdapter, player, GameMode.SPECTATOR);
        boolean healthyAfter = adapterAvailable(brokenAdapter);
        boolean second = (boolean) present.invoke(brokenAdapter, player, GameMode.SPECTATOR);
        require(!first && !healthyAfter && !second,
                "forced reflection/send failure did not permanently fail closed");
        mark(player, "VPROOF_FORCED_FAILURE_OK first=false healthy_after=false second=false");
    }

    private void pluginDisable(Player player) throws ReflectiveOperationException {
        ensureRuntime(player);
        require(reconcile(player, true), "plugin-disable setup could not enable no-clip");
        ensureSyntheticManager(player);
        Plugin staff = requireStaffPlugin();
        mark(player, "VPROOF_PLUGIN_DISABLE_ARMED staff_enabled=" + staff.isEnabled()
                + " noPhysics=" + player.hasNoPhysics());
        getServer().getPluginManager().disablePlugin(staff);
        require(!staff.isEnabled(), "frozen Staff plugin did not disable");
        require(!player.hasNoPhysics(), "frozen VanishManager plugin-disable cleanup did not restore noPhysics");
        mark(player, "VPROOF_PLUGIN_DISABLE_OK staff_enabled=false server_mode="
                + player.getGameMode() + " noPhysics=false");
    }

    private void finalizeProof(Player player) {
        require(player.getGameMode() == GameMode.SURVIVAL, "final server mode is not survival");
        require(!player.hasNoPhysics(), "final noPhysics state is not restored");
        Plugin staff = staffPlugin();
        require(staff != null && !staff.isEnabled(), "final frozen Staff disable state not observed");
        mark(player, "VPROOF_FINAL_OK server_mode=SURVIVAL noPhysics=false staff_enabled=false");
    }

    private void ensureRuntime(Player player) throws ReflectiveOperationException {
        if (exactAdapter == null || noclipController == null) {
            proveAbi(player);
        }
        ensureSyntheticManager(player);
    }

    private void ensureSyntheticManager(Player player) throws ReflectiveOperationException {
        if (syntheticVanishManager == null) {
            Plugin staff = requireStaffPlugin();
            require(staff instanceof JavaPlugin, "EnthusiaStaff is not a JavaPlugin");
            JavaPlugin staffJava = (JavaPlugin) staff;
            ClassLoader loader = frozenLoader();

            Class<?> visibilityClass = Class.forName(
                    "net.enthusia.staff.paper.visibility.DefaultStaffVisibilityService", true, loader);
            Method defaultMatrix = visibilityClass.getMethod("defaultMatrix");
            @SuppressWarnings("unchecked")
            Map<Object, Object> matrix = (Map<Object, Object>) defaultMatrix.invoke(null);
            Object visibility = visibilityClass.getConstructor(Map.class).newInstance(matrix);

            Class<?> staffModeClass = Class.forName(
                    "net.enthusia.staff.paper.staff.StaffModeManager", true, loader);
            syntheticWorkers = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "temp263-proof-worker");
                thread.setDaemon(true);
                return thread;
            });
            Supplier<Object> emptyStore = () -> null;
            Object staffMode = staffModeClass
                    .getConstructor(JavaPlugin.class, Clock.class, String.class, Supplier.class, ExecutorService.class)
                    .newInstance(staffJava, Clock.systemUTC(), "TEMP-PROOF", emptyStore, syntheticWorkers);

            Class<?> managerClass = Class.forName(
                    "net.enthusia.staff.paper.visibility.VanishManager", true, loader);
            Constructor<?> managerConstructor = Arrays.stream(managerClass.getConstructors())
                    .filter(candidate -> candidate.getParameterCount() == 8)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException("VanishManager 8-argument constructor"));
            syntheticVanishManager = managerConstructor.newInstance(
                    staffJava,
                    Clock.systemUTC(),
                    visibility,
                    emptyStore,
                    emptyStore,
                    staffMode,
                    syntheticWorkers,
                    noclipController
            );
            getServer().getPluginManager().registerEvents((Listener) syntheticVanishManager, this);
        }
        registerAudience(player);
    }

    private void registerAudience(Player player) throws ReflectiveOperationException {
        Field audiences = syntheticVanishManager.getClass().getDeclaredField("audiences");
        audiences.setAccessible(true);
        Object coordinator = audiences.get(syntheticVanishManager);
        Method register = Arrays.stream(coordinator.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals("register") && method.getParameterCount() == 3)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("VanishAudienceCoordinator.register"));
        register.setAccessible(true);
        register.invoke(coordinator, player.getUniqueId(), player, player.getGameMode());
    }

    private boolean reconcile(Player player, boolean enabled) throws ReflectiveOperationException {
        Method method = noclipController.getClass().getDeclaredMethod("reconcile", Player.class, boolean.class);
        method.setAccessible(true);
        return (boolean) invoke(method, noclipController, player, enabled);
    }

    private void invokeController(String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method method = noclipController.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        invoke(method, noclipController, args);
    }

    private Object instantiateController(Object adapter) throws ReflectiveOperationException {
        Class<?> controllerClass = Class.forName(
                "net.enthusia.staff.paper.visibility.VanishNoclipController", true, frozenLoader());
        Constructor<?> constructor = Arrays.stream(controllerClass.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 1)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("VanishNoclipController(adapter)"));
        constructor.setAccessible(true);
        return constructor.newInstance(adapter);
    }

    private Object instantiateAdapter(Class<?> adapterClass, Object access) throws ReflectiveOperationException {
        Constructor<?> constructor = Arrays.stream(adapterClass.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 2)
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException("Paper26 adapter constructor"));
        constructor.setAccessible(true);
        return constructor.newInstance(getLogger(), access);
    }

    private boolean adapterAvailable(Object adapter) throws ReflectiveOperationException {
        Method available = adapter.getClass().getMethod("available");
        available.setAccessible(true);
        return (boolean) available.invoke(adapter);
    }

    private Object recordValue(Object record, String accessor) throws ReflectiveOperationException {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private ClassLoader frozenLoader() {
        return requireStaffPlugin().getClass().getClassLoader();
    }

    private Plugin requireStaffPlugin() {
        Plugin plugin = staffPlugin();
        require(plugin != null, "frozen EnthusiaStaff plugin is not loaded");
        return plugin;
    }

    private Plugin staffPlugin() {
        return getServer().getPluginManager().getPlugin("EnthusiaStaff");
    }

    private static Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("unexpected checked exception", cause);
        }
    }

    private void mark(Player player, String marker) {
        getLogger().info(marker);
        player.sendMessage(marker);
    }

    private static String requireArg(String[] args, int index, String label) {
        if (args.length <= index) {
            throw new IllegalArgumentException(label + " is required");
        }
        return args[index].toLowerCase();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void clearBox(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String sanitize(String value) {
        return value == null ? "null" : value.replace(' ', '_');
    }
}
