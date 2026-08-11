package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPresence;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.economy.EconomyCoordinator;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.enthusia.staff.paper.inventory.InventoryCoordinator;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class InspectCommand implements CommandExecutor, TabCompleter {
    private static final int IDENTITY_ARGUMENT_COUNT = 1;
    private static final long MINIMUM_CONFISCATION_AMOUNT = 1L;
    private static final String INSPECT_PERMISSION = "enthusiastaff.inspect";
    private static final String INVENTORY_VIEW_PERMISSION = "enthusiastaff.inventory.view";
    private static final String ECONOMY_CONFISCATION_PERMISSION = "enthusiastaff.confiscate.economy";
    private static final String ECONOMY_SUBCOMMAND = "economy";
    private static final String ENDER_SUBCOMMAND = "ender";
    private static final String PLAYER_ABSENT_MESSAGE =
            "That player is absent from the authoritative directory.";

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<PlayerDirectory> directory;
    private final Supplier<CaseLookup> cases;
    private final Supplier<EconomyCoordinator> economy;
    private final Supplier<ConfiscationCoordinator> confiscation;
    private final InventoryCoordinator inventories;
    private final AuthorizationPolicy authorization;
    private final Supplier<MarketIntegration> market;
    private final Supplier<ReputationIntegration> reputation;
    private final ExecutorService workers;

    public InspectCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<PlayerDirectory> directory,
            Supplier<CaseLookup> cases,
            Supplier<EconomyCoordinator> economy,
            Supplier<ConfiscationCoordinator> confiscation,
            InventoryCoordinator inventories,
            AuthorizationPolicy authorization,
            Supplier<MarketIntegration> market,
            Supplier<ReputationIntegration> reputation,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.directory = java.util.Objects.requireNonNull(directory, "directory");
        this.cases = java.util.Objects.requireNonNull(cases, "cases");
        this.economy = java.util.Objects.requireNonNull(economy, "economy");
        this.confiscation = java.util.Objects.requireNonNull(confiscation, "confiscation");
        this.inventories = java.util.Objects.requireNonNull(inventories, "inventories");
        this.authorization = java.util.Objects.requireNonNull(authorization, "authorization");
        this.market = java.util.Objects.requireNonNull(market, "market");
        this.reputation = java.util.Objects.requireNonNull(reputation, "reputation");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!CommandPermissionGate.require(
                sender,
                INSPECT_PERMISSION,
                "You do not have permission to inspect players."
        )) {
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("The player inspector requires an in-game staff viewer.");
            return true;
        }
        if (arguments.length == IDENTITY_ARGUMENT_COUNT) {
            submitOrMessage(viewer, () -> showIdentity(viewer, arguments[0]));
            return true;
        }
        if (arguments.length == 2
                && (arguments[0].equalsIgnoreCase("inventory")
                || arguments[0].equalsIgnoreCase(ENDER_SUBCOMMAND))) {
            if (!CommandPermissionGate.require(
                    viewer,
                    INVENTORY_VIEW_PERMISSION,
                    "You do not have permission to inspect inventories."
            )) {
                return true;
            }
            submitOrMessage(
                    viewer,
                    () -> openInventory(
                            viewer,
                            arguments[1],
                            arguments[0].equalsIgnoreCase(ENDER_SUBCOMMAND)
                    )
            );
            return true;
        }
        if (arguments.length == 5 && arguments[0].equalsIgnoreCase(ECONOMY_SUBCOMMAND)) {
            if (!canApplyCaseConfiscation(viewer)) {
                viewer.sendMessage(Component.text("You do not have case confiscation authority."));
                return true;
            }
            confiscateEconomy(viewer, arguments);
            return true;
        }
        if (arguments.length == 3 && arguments[0].equalsIgnoreCase("items")) {
            if (!canApplyCaseConfiscation(viewer)) {
                viewer.sendMessage(Component.text("You do not have case confiscation authority."));
                return true;
            }
            confiscateItems(viewer, arguments[1], arguments[2]);
            return true;
        }
        String usage = "Usage: /" + label + " <player> | /" + label
                + " <inventory|ender> <player>";
        if (canApplyCaseConfiscation(viewer)) {
            usage += " | /" + label + " items <player> <case-id> | /" + label
                    + " economy <player> <case-id> <all|amount> CONFIRM";
        }
        viewer.sendMessage(Component.text(usage));
        return true;
    }

    private void showIdentity(Player viewer, String targetInput) {
        PlayerDirectory loaded = directory.get();
        if (loaded == null) {
            message(viewer, "Player directory storage is not ready.");
            return;
        }
        try {
            PlayerIdentity target = loaded.find(targetInput).orElse(null);
            if (target == null) {
                message(viewer, PLAYER_ABSENT_MESSAGE);
                return;
            }
            PlayerPresence presence = loaded.presence(target.playerId()).orElse(null);
            String name = target.currentUsername().orElse(target.playerId().toString());
            String server = presence == null
                    ? "offline/unknown"
                    : presence.currentServer().orElse("offline");
            String summary = "Inspector: " + name
                    + " | UUID " + target.playerId()
                    + " | platform " + target.platform()
                    + " | server " + server
                    + " | last seen " + target.lastSeenAt()
                    + ". Use /inspect inventory, /inspect ender, or the case-linked economy action.";
            message(viewer, summary);
            showReputation(viewer, target.playerId());
            showMarket(viewer, target.playerId());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Player inspector lookup failed", exception);
            message(viewer, "Player inspector storage lookup failed.");
        }
    }

    private void showReputation(Player viewer, UUID playerId) {
        ReputationIntegration integration = reputation.get();
        if (integration == null
                || integration.availability()
                != net.enthusia.staff.domain.evidence.IntegrationAvailability.AVAILABLE) {
            message(viewer, "Reputation blacklist: "
                    + (integration == null ? "UNAVAILABLE" : integration.availability()));
            return;
        }
        org.enthusia.rep.api.ReputationBlacklist blacklist = integration.blacklist(playerId)
                .map(value -> value.effectiveAt(clock.instant()))
                .orElse(null);
        if (blacklist == null) {
            message(viewer, "Reputation blacklist: none");
            return;
        }
        message(viewer, "Reputation blacklist: " + blacklist.status()
                + " case=" + blacklist.caseId()
                + " expires=" + blacklist.expirationAt().map(Object::toString).orElse("permanent")
                + " revision=" + blacklist.revision());
    }

    private void showMarket(Player viewer, UUID playerId) {
        MarketIntegration integration = market.get();
        if (integration == null
                || integration.availability()
                != net.enthusia.staff.domain.evidence.IntegrationAvailability.AVAILABLE) {
            message(viewer, "Market status: "
                    + (integration == null ? "UNAVAILABLE" : integration.availability()));
            return;
        }
        try {
            MarketIntegration.PlayerMarketStatus status = integration.status(playerId)
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            message(viewer, "Market status: stalls=" + status.stalls().size()
                    + " blacklist=" + status.blacklist()
                            .map(value -> value.status() + " case=" + value.caseId()
                                    + " expires=" + value.expirationAt()
                                            .map(Object::toString).orElse("permanent"))
                            .orElse("none"));
            for (MarketIntegration.StallView stall : status.stalls().stream().limit(10).toList()) {
                message(viewer, "Market stall " + stall.id() + " world=" + stall.world()
                        + " state=" + stall.state() + " owner=" + stall.ownerType()
                        + ':' + stall.ownerId().orElse("none"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            message(viewer, "Market status lookup was interrupted.");
        } catch (ExecutionException | TimeoutException exception) {
            plugin.getLogger().log(Level.WARNING, "Market status lookup failed", exception);
            message(viewer, "Market status lookup is temporarily unavailable.");
        }
    }

    private void openInventory(Player viewer, String targetInput, boolean enderChest) {
        PlayerDirectory loaded = directory.get();
        if (loaded == null) {
            message(viewer, "Player directory storage is not ready.");
            return;
        }
        try {
            PlayerIdentity target = loaded.find(targetInput).orElse(null);
            if (target == null) {
                message(viewer, PLAYER_ABSENT_MESSAGE);
                return;
            }
            onViewer(viewer, () -> inventories.open(viewer, target, enderChest));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Inspector inventory lookup failed", exception);
            message(viewer, "Player inventory lookup failed.");
        }
    }

    private void confiscateEconomy(Player viewer, String[] arguments) {
        if (!viewer.hasPermission(ECONOMY_CONFISCATION_PERMISSION)) {
            viewer.sendMessage(Component.text("You do not have economy confiscation permission."));
            return;
        }
        if (!arguments[4].equals("CONFIRM")) {
            viewer.sendMessage(Component.text("Economy confiscation requires the exact final token CONFIRM."));
            return;
        }
        CaseId caseId;
        OptionalLong amount;
        try {
            caseId = new CaseId(arguments[2]);
            amount = arguments[3].equalsIgnoreCase("all")
                    ? OptionalLong.empty()
                    : OptionalLong.of(parsePositiveAmount(arguments[3]));
        } catch (IllegalArgumentException exception) {
            viewer.sendMessage(Component.text("Invalid economy confiscation input: " + exception.getMessage()));
            return;
        }
        submitOrMessage(viewer, () -> resolveEconomyConfiscation(
                viewer,
                arguments[1],
                caseId,
                amount
        ));
    }

    private void confiscateItems(Player viewer, String targetInput, String caseInput) {
        if (!viewer.hasPermission("enthusiastaff.confiscate.items")) {
            viewer.sendMessage(Component.text("You do not have item confiscation permission."));
            return;
        }
        CaseId caseId;
        try {
            caseId = new CaseId(caseInput);
        } catch (IllegalArgumentException exception) {
            viewer.sendMessage(Component.text("Invalid case ID: " + exception.getMessage()));
            return;
        }
        submitOrMessage(viewer, () -> resolveItemConfiscation(viewer, targetInput, caseId));
    }

    private void resolveItemConfiscation(
            Player viewer,
            String targetInput,
            CaseId caseId
    ) {
        PlayerDirectory loadedDirectory = directory.get();
        CaseLookup loadedCases = cases.get();
        ConfiscationCoordinator coordinator = confiscation.get();
        if (loadedDirectory == null || loadedCases == null) {
            message(viewer, "Inspector storage is not ready; no asset lock was created.");
            return;
        }
        if (coordinator == null) {
            message(viewer, "Item confiscation safety integration is unavailable.");
            return;
        }
        try {
            PlayerIdentity target = loadedDirectory.find(targetInput).orElse(null);
            if (target == null) {
                message(viewer, PLAYER_ABSENT_MESSAGE);
                return;
            }
            UUID caseTarget = loadedCases.target(caseId).orElse(null);
            if (caseTarget == null) {
                message(viewer, "That case does not exist.");
                return;
            }
            if (!caseTarget.equals(target.playerId())) {
                message(viewer, "That case belongs to another player; no asset lock was created.");
                return;
            }
            if (!loadedCases.containsSanction(
                    caseId,
                    java.util.Set.of(
                            SanctionType.INVENTORY_CONFISCATION,
                            SanctionType.ENDER_CHEST_CONFISCATION
                    ),
                    true
            )) {
                message(viewer, "That case has no active item-confiscation sanction.");
                return;
            }
            onViewer(viewer, () -> {
                Player onlineTarget = plugin.getServer().getPlayer(target.playerId());
                if (onlineTarget == null) {
                    viewer.sendMessage(Component.text(
                            "Item confiscation selection requires the target on this backend."
                    ));
                    return;
                }
                coordinator.open(viewer, onlineTarget, caseId);
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Item confiscation lookup failed", exception);
            message(viewer, "Item confiscation lookup failed; no asset lock was created.");
        }
    }

    private void resolveEconomyConfiscation(
            Player viewer,
            String targetInput,
            CaseId caseId,
            OptionalLong amount
    ) {
        PlayerDirectory loadedDirectory = directory.get();
        CaseLookup loadedCases = cases.get();
        EconomyCoordinator coordinator = economy.get();
        if (loadedDirectory == null || loadedCases == null) {
            message(viewer, "Inspector storage is not ready; no assets changed.");
            return;
        }
        if (coordinator == null) {
            message(viewer, "EnthusiaCurrency moderation integration is unavailable.");
            return;
        }
        try {
            PlayerIdentity target = loadedDirectory.find(targetInput).orElse(null);
            if (target == null) {
                message(viewer, PLAYER_ABSENT_MESSAGE);
                return;
            }
            UUID caseTarget = loadedCases.target(caseId).orElse(null);
            if (caseTarget == null) {
                message(viewer, "That case does not exist.");
                return;
            }
            if (!caseTarget.equals(target.playerId())) {
                message(viewer, "That case belongs to another player; no assets changed.");
                return;
            }
            if (!loadedCases.containsSanction(
                    caseId,
                    java.util.Set.of(SanctionType.ECONOMY_CONFISCATION),
                    true
            )) {
                message(viewer, "That case has no active economy-confiscation sanction.");
                return;
            }
            onViewer(viewer, () -> {
                Player onlineTarget = plugin.getServer().getPlayer(target.playerId());
                if (onlineTarget == null) {
                    viewer.sendMessage(Component.text(
                            "Economy confiscation requires the target on this backend."
                    ));
                    return;
                }
                coordinator.confiscate(viewer, onlineTarget, caseId.value(), amount);
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Economy confiscation lookup failed", exception);
            message(viewer, "Economy confiscation lookup failed; no assets changed.");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!CommandPermissionGate.allows(sender::hasPermission, INSPECT_PERMISSION)) {
            return List.of();
        }
        if (arguments.length == IDENTITY_ARGUMENT_COUNT) {
            List<String> actions = new ArrayList<>();
            if (CommandPermissionGate.allows(sender::hasPermission, INVENTORY_VIEW_PERMISSION)) {
                actions.add("inventory");
                actions.add(ENDER_SUBCOMMAND);
            }
            if (canApplyCaseConfiscation(sender)) {
                if (sender.hasPermission("enthusiastaff.confiscate.items")) {
                    actions.add("items");
                }
                if (sender.hasPermission(ECONOMY_CONFISCATION_PERMISSION)) {
                    actions.add(ECONOMY_SUBCOMMAND);
                }
            }
            return prefix(arguments[0], actions);
        }
        if (arguments.length == 2
                && visibleTargetSubcommand(sender, arguments[0])) {
            String prefix = arguments[1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(50)
                    .toList();
        }
        if (arguments.length == 4 && arguments[0].equalsIgnoreCase(ECONOMY_SUBCOMMAND)
                && canApplyCaseConfiscation(sender)
                && sender.hasPermission(ECONOMY_CONFISCATION_PERMISSION)) {
            return prefix(arguments[3], List.of("all"));
        }
        if (arguments.length == 5 && arguments[0].equalsIgnoreCase(ECONOMY_SUBCOMMAND)
                && canApplyCaseConfiscation(sender)
                && sender.hasPermission(ECONOMY_CONFISCATION_PERMISSION)) {
            return prefix(arguments[4], List.of("CONFIRM"));
        }
        return List.of();
    }

    private boolean visibleTargetSubcommand(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("inventory") || input.equalsIgnoreCase(ENDER_SUBCOMMAND)) {
            return CommandPermissionGate.allows(sender::hasPermission, INVENTORY_VIEW_PERMISSION);
        }
        if (!canApplyCaseConfiscation(sender)) {
            return false;
        }
        return input.equalsIgnoreCase("items")
                ? sender.hasPermission("enthusiastaff.confiscate.items")
                : input.equalsIgnoreCase(ECONOMY_SUBCOMMAND)
                        && sender.hasPermission(ECONOMY_CONFISCATION_PERMISSION);
    }

    private boolean canApplyCaseConfiscation(CommandSender sender) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        return actor != null
                && authorization.permits(actor, ModerationAction.APPLY_CASE_CONFISCATION);
    }

    private static List<String> prefix(String input, List<String> candidates) {
        String lowered = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lowered))
                .toList();
    }

    private static long parsePositiveAmount(String input) {
        try {
            long amount = Long.parseLong(input);
            if (amount < MINIMUM_CONFISCATION_AMOUNT) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("amount must be a whole number", exception);
        }
    }

    private void submitOrMessage(Player viewer, Runnable operation) {
        try {
            workers.execute(operation);
        } catch (RejectedExecutionException exception) {
            viewer.sendMessage(Component.text("The moderation work queue is full; nothing changed."));
        }
    }

    private void onViewer(Player viewer, Runnable operation) {
        viewer.getScheduler().execute(plugin, operation, null, 1L);
    }

    private void message(Player viewer, String body) {
        onViewer(viewer, () -> viewer.sendMessage(Component.text(body)));
    }
}
