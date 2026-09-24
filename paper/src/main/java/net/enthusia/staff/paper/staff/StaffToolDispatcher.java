package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.tester.CheatTesterManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dispatches authenticated staff-mode tools. Moderation actions deliberately reuse their command/service
 * paths so a hotbar click cannot bypass normal permissions, operational-mode checks, audit, or provider
 * availability behavior.
 */
public final class StaffToolDispatcher implements Listener, CommandExecutor, TabCompleter {
    private static final int NO_ARGUMENTS = 0;
    private static final int ACTION_ARGUMENTS = 1;
    private static final int TARGET_ARGUMENTS = 2;

    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final CheatTesterManager cheatTester;
    private final StaffToolSettings settings;
    private final StaffToolCooldowns cooldowns;
    private final StaffToolRandomTeleportService randomTeleport;
    private final StaffToolSpectateFlow spectateFlow;
    private final StaffToolsMenuController menu;
    private final Map<StaffToolDefinition, ToolAction> actions;

    public StaffToolDispatcher(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze,
            CheatTesterManager cheatTester
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        VanishManager checkedVanish = java.util.Objects.requireNonNull(vanish, "vanish");
        this.cheatTester = java.util.Objects.requireNonNull(cheatTester, "cheatTester");
        this.settings = StaffToolSettings.load(plugin.getConfig());
        this.cooldowns = new StaffToolCooldowns(java.util.Objects.requireNonNull(clock, "clock"));
        this.randomTeleport = new StaffToolRandomTeleportService(
                plugin,
                serverId,
                staffMode,
                checkedVanish,
                java.util.Objects.requireNonNull(freeze, "freeze"),
                settings
        );
        this.spectateFlow = createSpectateFlow(
                plugin,
                actor -> staffMode.authorizedForTool(actor, StaffToolDefinition.SPECTATE)
                        && actor.hasPermission(StaffToolDefinition.SPECTATE.permission()),
                checkedVanish::isVanished
        );
        this.menu = new StaffToolsMenuController(plugin, this, checkedVanish);
        this.actions = createActions();
    }

    static StaffToolSpectateFlow createSpectateFlow(
            Plugin plugin,
            Predicate<Player> actorAuthorized,
            Predicate<UUID> vanished
    ) {
        return new StaffToolSpectateFlow(plugin, actorAuthorized, vanished);
    }

    private Map<StaffToolDefinition, ToolAction> createActions() {
        EnumMap<StaffToolDefinition, ToolAction> configured = new EnumMap<>(StaffToolDefinition.class);
        configured.put(StaffToolDefinition.RANDOM_TELEPORT, (player, ignored) -> randomTeleport.begin(player));
        configured.put(
                StaffToolDefinition.PLAYER_INSPECTOR,
                (player, targetId) -> runTargetCommand(player, "inspect", targetId, null)
        );
        configured.put(
                StaffToolDefinition.FREEZE,
                (player, targetId) -> runTargetCommand(player, "freeze", targetId, "Staff-mode tool investigation")
        );
        configured.put(StaffToolDefinition.REPORTS, (player, ignored) -> runCommand(player, "reports"));
        configured.put(StaffToolDefinition.CHEAT_TESTER, (player, ignored) -> cheatTester.cycleSelection(player));
        configured.put(StaffToolDefinition.SPECTATE, spectateFlow::begin);
        configured.put(StaffToolDefinition.VANISH, (player, ignored) -> runCommand(player, "vanish"));
        configured.put(StaffToolDefinition.STAFF_CHAT, (player, ignored) -> runCommand(player, "staffchat"));
        configured.put(StaffToolDefinition.STAFF_TOOLS, (player, ignored) -> menu.open(player));
        return Map.copyOf(configured);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isPrimaryToolClick(event)) {
            return;
        }
        Player player = event.getPlayer();
        StaffToolResolution resolution = resolveHeldTool(player);
        if (!resolution.tagged()) {
            return;
        }
        event.setCancelled(true);
        if (resolution.valid()
                && resolution.tool() == StaffToolDefinition.CHEAT_TESTER
                && player.isSneaking()) {
            dispatchCheatConfiguration(player, resolution.tool());
            return;
        }
        dispatchResolved(player, resolution, null);
    }

    private static boolean isPrimaryToolClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        return event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event.getHand(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof Player target)) {
            return;
        }
        StaffToolResolution resolution = resolveHeldTool(player);
        if (!resolution.tagged()) {
            return;
        }
        event.setCancelled(true);
        if (!resolution.valid()) {
            player.sendMessage(Component.text(resolution.status().message(), NamedTextColor.RED));
            return;
        }
        if (resolution.tool() != StaffToolDefinition.CHEAT_TESTER) {
            return;
        }
        dispatchCheatRun(player, target, resolution.tool());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.clear(event.getPlayer().getUniqueId());
    }

    private void handleEntityInteraction(
            Player player,
            Entity clicked,
            EquipmentSlot hand,
            Consumer<Boolean> cancellation
    ) {
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        StaffToolResolution resolution = resolveHeldTool(player);
        if (!resolution.tagged()) {
            return;
        }
        cancellation.accept(true);
        if (resolution.valid()
                && resolution.tool() == StaffToolDefinition.CHEAT_TESTER
                && player.isSneaking()) {
            dispatchCheatConfiguration(player, resolution.tool());
            return;
        }
        Player target = clicked instanceof Player other ? other : null;
        dispatchResolved(player, resolution, target);
    }

    private StaffToolResolution resolveHeldTool(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return staffMode.resolveTool(player, held, player.getInventory().getHeldItemSlot());
    }

    private void dispatchResolved(Player player, StaffToolResolution resolution, Player target) {
        if (!resolution.valid()) {
            player.sendMessage(Component.text(resolution.status().message(), NamedTextColor.RED));
            return;
        }
        dispatch(player, resolution.tool(), target == null ? null : target.getUniqueId());
    }

    private void dispatch(Player player, StaffToolDefinition tool, UUID targetId) {
        if (!hasToolAuthority(player, tool) || !hasValidTarget(player, tool, targetId)) {
            return;
        }
        if (!acquireCooldown(player, tool)) {
            return;
        }
        ToolAction action = actions.get(tool);
        if (action == null) {
            player.sendMessage(Component.text("That staff tool is unavailable on this runtime.", NamedTextColor.RED));
            return;
        }
        action.execute(player, targetId);
    }

    void dispatchFromMenu(Player player, StaffToolDefinition tool, UUID targetId) {
        dispatch(player, tool, targetId);
    }

    boolean menuAuthorized(Player player) {
        return menuToolAvailable(player, StaffToolDefinition.STAFF_TOOLS);
    }

    boolean menuToolAvailable(Player player, StaffToolDefinition tool) {
        return player != null
                && tool != null
                && staffMode.authorizedForTool(player, tool)
                && player.hasPermission(tool.permission());
    }

    List<StaffToolDefinition> availableMenuTools(Player player) {
        if (!menuAuthorized(player)) {
            return List.of();
        }
        return java.util.Arrays.stream(StaffToolDefinition.values())
                .filter(tool -> tool != StaffToolDefinition.STAFF_TOOLS)
                .filter(tool -> menuToolAvailable(player, tool))
                .toList();
    }

    public Listener menuListener() {
        return menu;
    }

    void exitStaffMode(Player player) {
        runCommand(player, "staff");
    }

    private void dispatchCheatConfiguration(Player player, StaffToolDefinition tool) {
        if (!hasToolAuthority(player, tool) || !acquireCooldown(player, tool)) {
            return;
        }
        cheatTester.showConfiguration(player);
    }

    private void dispatchCheatRun(Player player, Player target, StaffToolDefinition tool) {
        if (!hasToolAuthority(player, tool)) {
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Cheat Tester cannot target yourself.", NamedTextColor.RED));
            return;
        }
        if (!acquireCooldown(player, tool)) {
            return;
        }
        cheatTester.runSelected(player, target);
    }

    private boolean hasToolAuthority(Player player, StaffToolDefinition tool) {
        if (!staffMode.authorizedForTool(player, tool)) {
            player.sendMessage(Component.text(
                    "That staff tool is no longer authorized for your active staff session.",
                    NamedTextColor.RED
            ));
            return false;
        }
        if (player.hasPermission(tool.permission())) {
            return true;
        }
        player.sendMessage(Component.text(
                "You do not have permission to use " + tool.displayName() + '.',
                NamedTextColor.RED
        ));
        return false;
    }

    private static boolean hasValidTarget(Player player, StaffToolDefinition tool, UUID targetId) {
        if (!tool.targetRequired()) {
            return true;
        }
        if (targetId == null) {
            player.sendMessage(Component.text(
                    "Right-click a player with " + tool.displayName() + " or use the documented command fallback.",
                    NamedTextColor.YELLOW
            ));
            return false;
        }
        if (!targetId.equals(player.getUniqueId())) {
            return true;
        }
        player.sendMessage(Component.text("That staff tool cannot target yourself.", NamedTextColor.RED));
        return false;
    }

    private boolean acquireCooldown(Player player, StaffToolDefinition tool) {
        StaffToolCooldowns.Result result = cooldowns.acquire(
                player.getUniqueId(),
                tool,
                settings.cooldownFor(tool)
        );
        if (result.allowed()) {
            return true;
        }
        player.sendMessage(Component.text(
                "That staff tool is cooling down for about " + result.remainingMillis() + " ms.",
                NamedTextColor.YELLOW
        ));
        return false;
    }

    private void runTargetCommand(Player actor, String command, UUID targetId, String suffix) {
        UUID actorId = actor.getUniqueId();
        onEntity(targetId, target -> {
            StringBuilder built = new StringBuilder(command).append(' ').append(target.getName());
            appendSuffix(built, suffix);
            onEntity(actorId, current -> runCommand(current, built.toString()));
        }, () -> message(actorId, "That player is no longer online."));
    }

    private static void appendSuffix(StringBuilder command, String suffix) {
        if (suffix != null && !suffix.isBlank()) {
            command.append(' ').append(suffix);
        }
    }

    private void runCommand(Player player, String commandLine) {
        if (!player.performCommand(commandLine)) {
            player.sendMessage(Component.text(
                    "That staff action is unavailable on this backend. Use /estaff verify and the command fallback.",
                    NamedTextColor.RED
            ));
        }
    }

    private void beginNamedFollowOrSpectate(UUID actorId, String targetName) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            UUID targetId = findOnlinePlayerId(targetName);
            if (targetId == null) {
                message(actorId, "That player is not online on this backend.");
                return;
            }
            onEntity(actorId, actor -> dispatch(actor, StaffToolDefinition.SPECTATE, targetId));
        });
    }

    private UUID findOnlinePlayerId(String targetName) {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(targetName))
                .map(Player::getUniqueId)
                .findFirst()
                .orElse(null);
    }

    public void openTextMenu(Player player) {
        menu.open(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Staff tools require an in-game staff session.");
            return true;
        }
        handlePlayerCommand(player, label, arguments);
        return true;
    }

    private void handlePlayerCommand(Player player, String label, String[] arguments) {
        if (!staffMode.active(player.getUniqueId())) {
            player.sendMessage(Component.text("Enter staff mode before using /" + label + '.'));
            return;
        }
        if (arguments.length == NO_ARGUMENTS) {
            dispatch(player, StaffToolDefinition.STAFF_TOOLS, null);
            return;
        }
        if (isRandomCommand(arguments)) {
            dispatch(player, StaffToolDefinition.RANDOM_TELEPORT, null);
            return;
        }
        if (isFollowCommand(arguments)) {
            beginNamedFollowOrSpectate(player.getUniqueId(), arguments[1]);
            return;
        }
        player.sendMessage(Component.text(
                "Usage: /" + label + " | /" + label + " random | /" + label + " spectate <player>"
        ));
    }

    private static boolean isRandomCommand(String[] arguments) {
        return arguments.length == ACTION_ARGUMENTS && arguments[0].equalsIgnoreCase("random");
    }

    private static boolean isFollowCommand(String[] arguments) {
        return arguments.length == TARGET_ARGUMENTS && isFollowAction(arguments[0]);
    }

    private static boolean isFollowAction(String argument) {
        return argument.equalsIgnoreCase("spectate") || argument.equalsIgnoreCase("follow");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!(sender instanceof Player player) || !staffMode.active(player.getUniqueId())) {
            return List.of();
        }
        if (arguments.length != ACTION_ARGUMENTS) {
            return List.of();
        }
        String prefix = arguments[0].toLowerCase(Locale.ROOT);
        return List.of("random", "spectate", "follow").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private void onEntity(UUID playerId, Consumer<Player> operation) {
        onEntity(playerId, operation, () -> {
        });
    }

    private void onEntity(UUID playerId, Consumer<Player> operation, Runnable retired) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                retired.run();
                return;
            }
            boolean scheduled = player.getScheduler().execute(
                    plugin,
                    () -> operation.accept(player),
                    retired,
                    1L
            );
            if (!scheduled) {
                retired.run();
            }
        });
    }

    private void message(UUID playerId, String text) {
        onEntity(playerId, player -> player.sendMessage(Component.text(text)));
    }

    @FunctionalInterface
    private interface ToolAction {
        void execute(Player player, UUID targetId);
    }
}
