package proof.reset;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffStateResetProof extends JavaPlugin implements Listener {
    private static final int GEOMETRY_ACCEPTANCE_TICKS = 40;
    private static final Path EVIDENCE = Path.of("staff-state-reset-proof.txt");
    private final AtomicBoolean started = new AtomicBoolean();
    private Object staffMode;
    private Object visibility;
    private Object vanishManager;
    private Object adminRank;
    private Object founderRank;
    private Object recoveryGate;
    private Method clearRecoveryGate;
    private Method staffActive;
    private Method isVanished;
    private Method applyVanishMemoryState;
    private Map<Object, Object> activeSessions;
    private Map<Object, Object> ranks;
    private Map<Object, Object> toolSessions;
    private Object injectedSession;
    private String toolToken;

    @Override
    public void onEnable() {
        try {
            Files.deleteIfExists(EVIDENCE);
            prepareRuntime();
            getServer().getPluginManager().registerEvents(this, this);
            evidence("HARNESS_READY=true");
        } catch (Exception exception) {
            fail("ENABLE", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void prepareRank(PlayerJoinEvent event) {
        event.getPlayer().addAttachment(this, "enthusiastaff.identity.admin", true);
        event.getPlayer().addAttachment(this, "enthusiastaff.rank.admin", true);
        event.getPlayer().addAttachment(this, "enthusiastaff.vanish", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        evidence("MODE_EVENT|from=" + player.getGameMode()
                + "|to=" + event.getNewGameMode()
                + "|cancelled=" + event.isCancelled()
                + "|identityOwner=" + player.hasPermission("enthusiastaff.identity.owner")
                + "|identityAdmin=" + player.hasPermission("enthusiastaff.identity.admin")
                + "|legacyAdmin=" + player.hasPermission("enthusiastaff.rank.admin"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        later(10L, () -> begin(event.getPlayer()));
    }

    private void prepareRuntime() throws Exception {
        Plugin staff = requireStaffPlugin();
        ClassLoader loader = staff.getClass().getClassLoader(); // NOPMD - cross-plugin proof must use the target plugin loader.
        Field componentsField = staff.getClass().getDeclaredField("runtimeComponents");
        componentsField.setAccessible(true); // NOPMD - proof inspects the private runtime composition without widening production API.
        Object components = componentsField.get(staff);

        staffMode = accessor(components, "staffMode").invoke(components);
        visibility = accessor(components, "visibility").invoke(components);
        vanishManager = accessor(components, "vanish").invoke(components);
        staffActive = staffMode.getClass().getMethod("active", UUID.class);
        isVanished = visibility.getClass().getMethod("isVanished", UUID.class);

        Class<?> rankClass = loader.loadClass("net.enthusia.staff.domain.auth.StaffRank");
        adminRank = rankClass.getField("ADMIN").get(null);
        founderRank = rankClass.getField("FOUNDER").get(null);
        Field recoveryGateField = staffMode.getClass().getDeclaredField("recoveryGate");
        recoveryGateField.setAccessible(true); // NOPMD - proof verifies recovery fencing without widening production API.
        recoveryGate = recoveryGateField.get(staffMode);
        clearRecoveryGate = recoveryGate.getClass().getDeclaredMethod("clear", UUID.class);
        clearRecoveryGate.setAccessible(true); // NOPMD - proof clears only its injected test identity.
        applyVanishMemoryState = vanishManager.getClass().getDeclaredMethod(
                "applyReconciledMemoryState", Player.class, rankClass, boolean.class);
        applyVanishMemoryState.setAccessible(true); // NOPMD - proof drives the real vanish state machine without persistence.

        activeSessions = rawMap(staffMode, "active");
        ranks = rawMap(staffMode, "ranks");
        toolSessions = rawMap(staffMode, "toolSessions");
        evidence(RuntimeIdentity.describe());
    }

    private Plugin requireStaffPlugin() {
        Plugin staff = getServer().getPluginManager().getPlugin("EnthusiaStaff");
        if (staff == null || !staff.isEnabled()) {
            throw new IllegalStateException("EnthusiaStaff is not enabled");
        }
        return staff;
    }

    private void begin(Player player) {
        try {
            player.setGameMode(GameMode.CREATIVE);
            injectActiveSession(player);
            setVanish(player, false);
            evidence("RANK_AUTHORITY|identityAdmin="
                    + player.hasPermission("enthusiastaff.identity.admin")
                    + "|legacyAdmin=" + player.hasPermission("enthusiastaff.rank.admin"));
            state(player, "VISIBLE_CREATIVE", false, GameMode.CREATIVE);
            transition(player, GameMode.SURVIVAL, "VISIBLE_SURVIVAL", false,
                    () -> transition(player, GameMode.SPECTATOR, "VISIBLE_SPECTATOR", false,
                            () -> geometryMatrix(player, "VISIBLE", () ->
                                    transition(player, GameMode.CREATIVE, "VISIBLE_CREATIVE_2", false,
                                            () -> vanishOn(player)))));
        } catch (Exception exception) {
            fail("BEGIN", exception);
        }
    }

    private void vanishOn(Player player) {
        try {
            setVanish(player, true);
            state(player, "VANISH_ON_CREATIVE", true, GameMode.CREATIVE);
            selectWhileVanished(
                    player,
                    GameMode.SURVIVAL,
                    "VANISHED_SELECT_SURVIVAL",
                    () -> adminVanishOffSurvival(player)
            );
        } catch (Exception exception) {
            fail("VANISH_ON", exception);
        }
    }

    private void adminVanishOffSurvival(Player player) {
        try {
            setVanish(player, false);
            state(player, "VANISH_OFF_SURVIVAL", false, GameMode.SURVIVAL);
            setVanish(player, true);
            state(player, "VANISH_ON_SURVIVAL", true, GameMode.SURVIVAL);
            selectWhileVanished(
                    player,
                    GameMode.SPECTATOR,
                    "VANISHED_SELECT_SPECTATOR",
                    () -> geometryMatrix(player, "VANISHED", () -> adminVanishOffSpectator(player))
            );
        } catch (Exception exception) {
            fail("VANISH_SURVIVAL_TOGGLE", exception);
        }
    }

    private void adminVanishOffSpectator(Player player) {
        try {
            setVanish(player, false);
            state(player, "VANISH_OFF_SPECTATOR", false, GameMode.SPECTATOR);
            setVanish(player, true);
            state(player, "VANISH_ON_SPECTATOR", true, GameMode.SPECTATOR);
            selectWhileVanished(
                    player,
                    GameMode.CREATIVE,
                    "VANISHED_SELECT_CREATIVE",
                    () -> rejectAdventure(player)
            );
        } catch (Exception exception) {
            fail("VANISH_SPECTATOR_TOGGLE", exception);
        }
    }

    private void rejectAdventure(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        later(2L, () -> {
            state(player, "ADVENTURE_REJECTED", true, GameMode.CREATIVE);
            vanishOffToCreative(player);
        });
    }

    private void vanishOffToCreative(Player player) {
        try {
            setVanish(player, false);
            state(player, "VANISH_OFF_CREATIVE", false, GameMode.CREATIVE);
            transition(player, GameMode.SPECTATOR, "VISIBLE_SPECTATOR_FINAL", false,
                    () -> {
                        Location base = chamber(player);
                        geometryCase(
                                player,
                                "VISIBLE_FINAL_TELEPORT_WALL",
                                base,
                                base.clone().add(2.0D, 0.0D, 0.0D),
                                position -> position.getX() > base.getX() + 1.5D,
                                () -> beginFounderMatrix(player)
                        );
                    });
        } catch (Exception exception) {
            fail("VANISH_OFF_CREATIVE", exception);
        }
    }

    private void beginFounderMatrix(Player player) {
        player.addAttachment(this, "enthusiastaff.identity.owner", true);
        ranks.put(player.getUniqueId(), founderRank);
        evidence("FOUNDER_AUTHORITY|identityOwner="
                + player.hasPermission("enthusiastaff.identity.owner"));
        transition(player, GameMode.CREATIVE, "FOUNDER_VISIBLE_CREATIVE", false,
                () -> transition(player, GameMode.SURVIVAL, "FOUNDER_VISIBLE_SURVIVAL", false,
                        () -> transition(player, GameMode.SPECTATOR, "FOUNDER_VISIBLE_SPECTATOR", false,
                                () -> transition(player, GameMode.CREATIVE, "FOUNDER_VISIBLE_CREATIVE_2", false,
                                        () -> founderVanishOn(player)))));
    }

    private void founderVanishOn(Player player) {
        try {
            setVanish(player, true);
            state(player, "FOUNDER_VANISH_ON_CREATIVE", true, GameMode.CREATIVE);
            selectWhileVanished(
                    player,
                    GameMode.SURVIVAL,
                    "FOUNDER_VANISHED_SELECT_SURVIVAL",
                    () -> founderVanishOffSurvival(player)
            );
        } catch (Exception exception) {
            fail("FOUNDER_VANISH_ON", exception);
        }
    }

    private void founderVanishOffSurvival(Player player) {
        try {
            setVanish(player, false);
            state(player, "FOUNDER_VANISH_OFF_SURVIVAL", false, GameMode.SURVIVAL);
            setVanish(player, true);
            state(player, "FOUNDER_VANISH_ON_SURVIVAL", true, GameMode.SURVIVAL);
            selectWhileVanished(
                    player,
                    GameMode.SPECTATOR,
                    "FOUNDER_VANISHED_SELECT_SPECTATOR",
                    () -> founderVanishOffSpectator(player)
            );
        } catch (Exception exception) {
            fail("FOUNDER_VANISH_SURVIVAL_TOGGLE", exception);
        }
    }

    private void founderVanishOffSpectator(Player player) {
        try {
            setVanish(player, false);
            state(player, "FOUNDER_VANISH_OFF_SPECTATOR", false, GameMode.SPECTATOR);
            setVanish(player, true);
            state(player, "FOUNDER_VANISH_ON_SPECTATOR", true, GameMode.SPECTATOR);
            selectWhileVanished(
                    player,
                    GameMode.CREATIVE,
                    "FOUNDER_VANISHED_SELECT_CREATIVE",
                    () -> founderVanishOffCreative(player)
            );
        } catch (Exception exception) {
            fail("FOUNDER_VANISH_SPECTATOR_TOGGLE", exception);
        }
    }

    private void founderVanishOffCreative(Player player) {
        try {
            setVanish(player, false);
            state(player, "FOUNDER_VANISH_OFF_CREATIVE", false, GameMode.CREATIVE);
            finish();
        } catch (Exception exception) {
            fail("FOUNDER_VANISH_OFF_CREATIVE", exception);
        }
    }

    private void selectWhileVanished(
            Player player,
            GameMode selected,
            String label,
            Runnable next
    ) {
        player.setGameMode(selected);
        later(2L, () -> {
            state(player, label, true, selected);
            marker(player, "STATE:" + label);
            later(2L, next);
        });
    }

    private void transition(Player player, GameMode mode, String label, boolean vanished, Runnable next) {
        player.setGameMode(mode);
        later(2L, () -> {
            state(player, label, vanished, mode);
            marker(player, "STATE:" + label);
            later(2L, next);
        });
    }

    private void geometryMatrix(Player player, String prefix, Runnable next) {
        Location base = chamber(player);
        geometryCase(
                player,
                prefix + "_WALL",
                base,
                base.clone().add(2.0D, 0.0D, 0.0D),
                position -> position.getX() > base.getX() + 1.5D,
                () -> geometryCase(
                        player,
                        prefix + "_FLOOR",
                        base,
                        base.clone().add(0.0D, -2.5D, 0.0D),
                        position -> position.getY() < base.getY() - 1.5D,
                        () -> geometryCase(
                                player,
                                prefix + "_CEILING",
                                base,
                                base.clone().add(0.0D, 3.5D, 0.0D),
                                position -> position.getY() > base.getY() + 2.5D,
                                next
                        )
                )
        );
    }

    private void geometryCase(
            Player player,
            String label,
            Location base,
            Location target,
            java.util.function.Predicate<Location> passed,
            Runnable next
    ) {
        player.teleport(base);
        later(4L, () -> {
            marker(player, "MOVE:" + label + ":" + target.getX() + ":" + target.getY() + ":" + target.getZ());
            waitForGeometryAcceptance(player, label, passed, next, GEOMETRY_ACCEPTANCE_TICKS);
        });
    }

    private void waitForGeometryAcceptance(
            Player player,
            String label,
            java.util.function.Predicate<Location> passed,
            Runnable next,
            int remainingTicks
    ) {
        Location position = player.getLocation();
        boolean success = passed.test(position);
        if (!success && remainingTicks > 0) {
            later(1L, () -> waitForGeometryAcceptance(player, label, passed, next, remainingTicks - 1));
            return;
        }
        evidence("GEOMETRY|" + label + "|pass=" + success
                + "|mode=" + player.getGameMode()
                + "|vanish=" + vanished(player)
                + "|x=" + position.getX()
                + "|y=" + position.getY()
                + "|z=" + position.getZ());
        if (!success) {
            evidence("FAIL|GEOMETRY|" + label);
        }
        later(2L, next);
    }

    private Location chamber(Player player) {
        Location current = player.getLocation();
        Location base = new Location(
                player.getWorld(),
                current.getBlockX() + 0.5D,
                current.getBlockY(),
                current.getBlockZ() + 0.5D
        );
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        for (int dx = -2; dx <= 4; dx++) {
            for (int dy = -3; dy <= 5; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    player.getWorld().getBlockAt(bx + dx, by + dy, bz + dz).setType(Material.AIR, false);
                }
            }
        }
        for (int y = by - 2; y <= by + 3; y++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                player.getWorld().getBlockAt(bx + 1, y, z).setType(Material.STONE, false);
            }
        }
        for (int dx = -1; dx <= 4; dx++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                player.getWorld().getBlockAt(bx + dx, by - 1, z).setType(Material.STONE, false);
                player.getWorld().getBlockAt(bx + dx, by + 2, z).setType(Material.STONE, false);
            }
        }
        return base;
    }

    private void injectActiveSession(Player player) throws Exception {
        UUID playerId = player.getUniqueId();
        injectedSession = newSession(playerId);
        toolToken = UUID.randomUUID().toString();
        clearRecoveryGate.invoke(recoveryGate, playerId);
        activeSessions.put(playerId, injectedSession);
        ranks.put(playerId, adminRank);
        toolSessions.put(playerId, toolToken);
        evidence("RECOVERY_FENCE_CLEARED=true");
    }

    private Object newSession(UUID playerId) throws Exception {
        ClassLoader loader = staffMode.getClass().getClassLoader(); // NOPMD - session type belongs to the target plugin loader.
        Class<?> stateClass = loader.loadClass("net.enthusia.staff.domain.staff.StaffSessionState");
        Class<?> snapshotClass = loader.loadClass("net.enthusia.staff.domain.staff.StaffSessionSnapshot");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object activeState = Enum.valueOf((Class<? extends Enum>) stateClass.asSubclass(Enum.class), "ACTIVE");
        Constructor<?> constructor = snapshotClass.getConstructor(
                UUID.class,
                UUID.class,
                String.class,
                stateClass,
                boolean.class,
                int.class,
                String.class,
                byte[].class,
                Instant.class,
                long.class
        );
        return constructor.newInstance(
                UUID.randomUUID(),
                playerId,
                "paper:reset-proof",
                activeState,
                false,
                1,
                "0".repeat(64),
                new byte[]{1},
                Instant.now(),
                1L
        );
    }

    private void state(Player player, String label, boolean expectedVanish, GameMode expectedMode) {
        try {
            UUID playerId = player.getUniqueId();
            boolean staff = (boolean) staffActive.invoke(staffMode, playerId);
            boolean vanish = vanished(player);
            boolean snapshotSame = activeSessions.get(playerId) == injectedSession; // NOPMD - identity is the assertion.
            boolean toolSame = toolToken.equals(toolSessions.get(playerId));
            boolean pass = staff
                    && vanish == expectedVanish
                    && player.getGameMode() == expectedMode
                    && snapshotSame
                    && toolSame;
            evidence("STATE|" + label
                    + "|pass=" + pass
                    + "|staff=" + staff
                    + "|vanish=" + vanish
                    + "|mode=" + player.getGameMode()
                    + "|snapshotSame=" + snapshotSame
                    + "|toolSame=" + toolSame);
            if (!pass) {
                evidence("FAIL|STATE|" + label);
            }
        } catch (Exception exception) {
            fail("STATE_" + label, exception);
        }
    }

    private void setVanish(Player player, boolean vanished) throws Exception {
        Object rank = ranks.getOrDefault(player.getUniqueId(), adminRank);
        applyVanishMemoryState.invoke(vanishManager, player, rank, vanished);
    }

    private boolean vanished(Player player) {
        try {
            return (boolean) isVanished.invoke(visibility, player.getUniqueId());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void finish() {
        evidence("DONE=true");
        getServer().getOnlinePlayers().forEach(player -> marker(player, "DONE"));
        later(8L, () -> getServer().dispatchCommand(getServer().getConsoleSender(), "stop"));
    }

    private void marker(Player player, String value) {
        if (player.isOnline()) {
            player.sendMessage(Component.text("RESET_PROOF:" + value));
        }
    }

    private void later(long ticks, Runnable task) {
        getServer().getScheduler().runTaskLater(this, task, ticks);
    }

    private static Method accessor(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true); // NOPMD - proof accesses private record accessors without widening production API.
        return method;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> rawMap(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); // NOPMD - proof inspects private state identity without widening production API.
        return (Map<Object, Object>) field.get(target);
    }

    private void fail(String phase, Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        evidence("FAIL|" + phase + "|" + root.getClass().getName() + ":" + root.getMessage());
    }

    private void evidence(String line) {
        synchronized (this) {
            try {
                Files.writeString(
                        EVIDENCE,
                        line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
                getLogger().info("[RESET-PROOF] " + line);
            } catch (Exception exception) {
                getLogger().severe("Could not write reset proof evidence: " + exception);
            }
        }
    }
}
