package net.enthusia.staff.paper.staff;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.enthusia.staff.paper.freeze.FreezeManager;
import net.enthusia.staff.paper.visibility.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Dispatches authenticated staff-mode tools. Moderation actions deliberately reuse their command/service
 * paths so a hotbar click cannot bypass normal permissions, operational-mode checks, audit, or provider
 * availability behavior.
 */
public final class StaffToolDispatcher implements Listener, CommandExecutor, TabCompleter {
    private static final String RANDOM_EXEMPT_PERMISSION = "enthusiastaff.stafftools.random-exempt";
    private static final String SPECTATE_EXEMPT_PERMISSION = "enthusiastaff.stafftools.spectate-exempt";

    private final JavaPlugin plugin;
    private final String serverId;
    private final StaffModeManager staffMode;
    private final VanishManager vanish;
    private final FreezeManager freeze;
    private final StaffToolSettings settings;
    private final StaffToolCooldowns cooldowns;

    public StaffToolDispatcher(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            StaffModeManager staffMode,
            VanishManager vanish,
            FreezeManager freeze
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.serverId = java.util.Objects.requireNonNull(serverId, "serverId");
        this.staffMode = java.util.Objects.requireNonNull(staffMode, "staffMode");
        this.vanish = java.util.Objects.requireNonNull(vanish, "vanish");
        this.freeze = java.util.Objects.requireNonNull(freeze, "freeze");
        this.settings = StaffToolSettings.load(plugin.getConfig());
        this.cooldowns = new StaffToolCooldowns(java.util.Objects.requireNonNull(clock, "clock"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        StaffToolResolution resolution = resolveHeldTool(player);
        if (!resolution.tagged()) {
            return;
        }
        event.setCancelled(true);
        dispatchResolved(player, resolution, null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event.getHand(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityInteraction(event.getPlayer(), event.getRightClicked(), event.getHand(), event::setCancelled);
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
        Player target = clicked instanceof Player other ? other : null;
        dispatchResolved(player, resolution, target);
    }

    private StaffToolResolution resolveHeldTool(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return staffMode.resolveTool(player, held, player.getInventory().getHeldItemSlot());
    }

    private void dispatchResolved(Player player, StaffToolResolution resolution, Player target) {
        if (!resolution.valid()) {
            player.sendMessage(Component.text(rejectionMessage(resolution.status()), NamedTextColor.RED));
            return;
        }
        dispatch(player, resolution.tool(), target == null ? null : target.getUniqueId());
    }

    private void dispatch(Player player, StaffToolDefinition tool, UUID targetId) {
        if (!staffMode.authorizedForTool(player, tool)) {
            player.sendMessage(Component.text(
                    "That staff tool is no longer authorized for your active staff session.",
                    NamedTextColor.RED
            ));
            return;
        }
        if (!player.hasPermission(tool.permission())) {
            player.sendMessage(Component.text(
                    "You do not have permission to use " + tool.displayName() + '.',
                    NamedTextColor.RED
            ));
            return;
        }
        if (tool.targetRequired() && targetId == null) {
            player.sendMessage(Component.text(
                    "Right-click a player with " + tool.displayName() + " or use the documented command fallback.",
                    NamedTextColor.YELLOW
            ));
            return;
        }
        if (targetId != null && targetId.equals(player.getUniqueId())) {
            player.sendMessage(Component.text("That staff tool cannot target yourself.", NamedTextColor.RED));
            return;
        }
        StaffToolCooldowns.Result cooldown = cooldowns.acquire(
                player.getUniqueId(),
                tool,
                settings.cooldownFor(tool)
        );
        if (!cooldown.allowed()) {
            player.sendMessage(Component.text(
                    "That staff tool is cooling down for about " + cooldown.remainingMillis() + " ms.",
                    NamedTextColor.YELLOW
            ));
            return;
        }
        switch (tool) {
            case RANDOM_TELEPORT -> beginRandomTeleport(player);
            case PLAYER_INSPECTOR -> runTargetCommand(player, "inspect", targetId, null);
            case FREEZE -> runTargetCommand(player, "freeze", targetId, "Staff-mode tool investigation");
            case REPORTS -> runCommand(player, "reports");
            case CHEAT_TESTER -> player.sendMessage(Component.text(
                    "Cheat Tester is intentionally unavailable until package ES-P10 is completed.",
                    NamedTextColor.YELLOW
            ));
            case SPECTATE -> beginFollowOrSpectate(player, targetId);
            case VANISH -> runCommand(player, "vanish");
            case STAFF_CHAT -> runCommand(player, "staffchat");
            case STAFF_TOOLS -> openTextMenu(player);
        }
    }

    private void runTargetCommand(Player actor, String command, UUID targetId, String suffix) {
        if (targetId == null) {
            actor.sendMessage(Component.text("That staff tool requires an online player target."));
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(targetId, target -> {
            StringBuilder built = new StringBuilder(command).append(' ').append(target.getName());
            if (suffix != null && !suffix.isBlank()) {
                built.append(' ').append(suffix);
            }
            onEntity(actorId, current -> runCommand(current, built.toString()));
        }, () -> message(actorId, "That player is no longer online."));
    }

    private void runCommand(Player player, String commandLine) {
        if (!player.performCommand(commandLine)) {
            player.sendMessage(Component.text(
                    "That staff action is unavailable on this backend. Use /estaff verify and the command fallback.",
                    NamedTextColor.RED
            ));
        }
    }

    private void beginRandomTeleport(Player actor) {
        if (!settings.randomTeleportEnabledOn(serverId)) {
            actor.sendMessage(Component.text(
                    "Random staff teleport is disabled on backend " + serverId + '.',
                    NamedTextColor.YELLOW
            ));
            return;
        }
        UUID actorId = actor.getUniqueId();
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            List<UUID> candidates = plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getUniqueId)
                    .toList();
            if (candidates.isEmpty()) {
                message(actorId, "No suitable random-teleport target is online.");
                return;
            }
            ConcurrentLinkedQueue<TargetSnapshot> eligible = new ConcurrentLinkedQueue<>();
            AtomicInteger remaining = new AtomicInteger(candidates.size());
            Runnable finishedOne = () -> {
                if (remaining.decrementAndGet() == 0) {
                    finishRandomTeleport(actorId, eligible);
                }
            };
            for (UUID candidateId : candidates) {
                snapshotRandomCandidate(actorId, candidateId, eligible, finishedOne);
            }
        });
    }

    private void snapshotRandomCandidate(
            UUID actorId,
            UUID targetId,
            Collection<TargetSnapshot> eligible,
            Runnable finished
    ) {
        onEntity(targetId, target -> {
            try {
                boolean allowed = StaffToolTargetPolicy.eligibleRandomTarget(
                        actorId,
                        targetId,
                        staffMode.active(targetId),
                        vanish.isVanished(targetId),
                        freeze.isRestricted(targetId),
                        target.hasPermission(RANDOM_EXEMPT_PERMISSION),
                        target.isDead(),
                        target.isSleeping(),
                        target.isInsideVehicle(),
                        target.getGameMode(),
                        settings.worldEnabled(target.getWorld().getName())
                );
                if (allowed) {
                    eligible.add(new TargetSnapshot(targetId, target.getName(), target.getLocation().clone()));
                }
            } finally {
                finished.run();
            }
        }, finished);
    }

    private void finishRandomTeleport(UUID actorId, Collection<TargetSnapshot> candidates) {
        onEntity(actorId, actor -> {
            if (!staffMode.authorizedForTool(actor, StaffToolDefinition.RANDOM_TELEPORT)
                    || !actor.hasPermission(StaffToolDefinition.RANDOM_TELEPORT.permission())) {
                actor.sendMessage(Component.text(
                        "Random teleport was cancelled because your staff session or permission changed.",
                        NamedTextColor.RED
                ));
                return;
            }
            List<TargetSnapshot> shuffled = new ArrayList<>(candidates);
            if (shuffled.isEmpty()) {
                actor.sendMessage(Component.text("No suitable random-teleport target is online."));
                return;
            }
            TargetSnapshot target = shuffled.get(ThreadLocalRandom.current().nextInt(shuffled.size()));
            actor.teleportAsync(target.location()).whenComplete((success, failure) -> {
                if (failure != null || !Boolean.TRUE.equals(success)) {
                    message(actorId, "Random staff teleport failed safely; no state was changed.");
                    return;
                }
                message(actorId, "Teleported to a suitable random player: " + target.name() + '.');
            });
        });
    }

    private void beginFollowOrSpectate(Player actor, UUID targetId) {
        if (targetId == null) {
            actor.sendMessage(Component.text("Follow/Spectate requires an online player target."));
            return;
        }
        UUID actorId = actor.getUniqueId();
        onEntity(targetId, target -> {
            if (vanish.isVanished(targetId)) {
                message(actorId, "That target is vanished and cannot be selected through this tool.");
                return;
            }
            if (target.hasPermission(SPECTATE_EXEMPT_PERMISSION)) {
                message(actorId, "That target is exempt from staff follow/spectate tools.");
                return;
            }
            TargetSnapshot snapshot = new TargetSnapshot(targetId, target.getName(), target.getLocation().clone());
            onEntity(actorId, current -> followSnapshot(current, snapshot));
        }, () -> message(actorId, "That player is no longer online."));
    }

    private void beginNamedFollowOrSpectate(UUID actorId, String targetName) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            UUID targetId = plugin.getServer().getOnlinePlayers().stream()
                    .filter(player -> player.getName().equalsIgnoreCase(targetName))
                    .map(Player::getUniqueId)
                    .findFirst()
                    .orElse(null);
            if (targetId == null) {
                message(actorId, "That player is not online on this backend.");
                return;
            }
            onEntity(actorId, actor -> dispatch(actor, StaffToolDefinition.SPECTATE, targetId));
        });
    }

    private void followSnapshot(Player actor, TargetSnapshot target) {
        if (!staffMode.authorizedForTool(actor, StaffToolDefinition.SPECTATE)
                || !actor.hasPermission(StaffToolDefinition.SPECTATE.permission())) {
            actor.sendMessage(Component.text(
                    "Follow/Spectate was cancelled because your staff session or permission changed.",
                    NamedTextColor.RED
            ));
            return;
        }
        UUID actorId = actor.getUniqueId();
        actor.teleportAsync(target.location()).whenComplete((success, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                message(actorId, "Follow/Spectate teleport failed safely.");
                return;
            }
            onEntity(actorId, current -> {
                if (current.getGameMode() != GameMode.SPECTATOR) {
                    current.sendMessage(Component.text(
                            "Teleported to " + target.name()
                                    + ". Your current staff-rank profile remains in creative mode.",
                            NamedTextColor.GREEN
                    ));
                    return;
                }
                Player liveTarget = plugin.getServer().getPlayer(target.playerId());
                if (liveTarget == null || vanish.isVanished(target.playerId())) {
                    current.sendMessage(Component.text(
                            "Teleported to the last safe target location; direct spectating is no longer available.",
                            NamedTextColor.YELLOW
                    ));
                    return;
                }
                try {
                    current.setSpectatorTarget(liveTarget);
                    current.sendMessage(Component.text("Now spectating " + liveTarget.getName() + '.', NamedTextColor.GREEN));
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    current.sendMessage(Component.text(
                            "Teleported to " + target.name() + "; direct spectator attachment was unavailable.",
                            NamedTextColor.YELLOW
                    ));
                }
            });
        });
    }

    public void openTextMenu(Player player) {
        player.sendMessage(Component.text("EnthusiaStaff tools", NamedTextColor.GOLD));
        sendMenuLine(player, "/stafftools random", "Random teleport", "/stafftools random");
        sendMenuLine(player, "/inspect <player>", "Player inspector", "/inspect ");
        sendMenuLine(player, "/freeze <player> <reason>", "Freeze", "/freeze ");
        sendMenuLine(player, "/reports", "Reports", "/reports");
        sendMenuLine(player, "/stafftools spectate <player>", "Follow/Spectate", "/stafftools spectate ");
        sendMenuLine(player, "/vanish", "Vanish", "/vanish");
        sendMenuLine(player, "/staffchat", "Staff chat", "/staffchat");
        sendMenuLine(player, "/staff", "Exit staff mode", "/staff");
        player.sendMessage(Component.text(
                "Cheat Tester remains unavailable until ES-P10; no test state is changed by this package.",
                NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
                "Bedrock fallback: type the shown commands directly if clickable text is unavailable.",
                NamedTextColor.GRAY
        ));
    }

    private static void sendMenuLine(Player player, String commandText, String description, String suggestedCommand) {
        player.sendMessage(Component.text("• " + description + ": ", NamedTextColor.GRAY)
                .append(Component.text(commandText, NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand(suggestedCommand))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Staff tools require an in-game staff session.");
            return true;
        }
        if (!staffMode.active(player.getUniqueId())) {
            player.sendMessage(Component.text("Enter staff mode before using /" + label + '.'));
            return true;
        }
        if (arguments.length == 0) {
            dispatch(player, StaffToolDefinition.STAFF_TOOLS, null);
            return true;
        }
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("random")) {
            dispatch(player, StaffToolDefinition.RANDOM_TELEPORT, null);
            return true;
        }
        if (arguments.length == 2
                && (arguments[0].equalsIgnoreCase("spectate") || arguments[0].equalsIgnoreCase("follow"))) {
            beginNamedFollowOrSpectate(player.getUniqueId(), arguments[1]);
            return true;
        }
        player.sendMessage(Component.text(
                "Usage: /" + label + " | /" + label + " random | /" + label + " spectate <player>"
        ));
        return true;
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
        if (arguments.length == 1) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            return List.of("random", "spectate", "follow").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
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

    private static String rejectionMessage(StaffToolSessionPolicy.Status status) {
        return switch (status) {
            case UNKNOWN_TOOL -> "Unknown staff tool tag; the item is stale or spoofed.";
            case STALE_SESSION -> "That staff tool belongs to a stale or inactive staff session.";
            case RANK_UNAVAILABLE -> "That staff tool is not available to your current explicit staff rank.";
            case OWNER_MISMATCH -> "That staff tool belongs to another staff player and cannot be used.";
            case SESSION_MISMATCH -> "That staff tool belongs to an older staff session and cannot be used.";
            case SLOT_MISMATCH -> "That staff tool is outside its protected hotbar slot and cannot be used.";
            case MATERIAL_MISMATCH -> "That staff tool does not match the server-issued tool definition.";
            case VALID -> "The staff tool is valid.";
        };
    }

    private record TargetSnapshot(UUID playerId, String name, Location location) {
    }
}
