package net.enthusia.staff.paper.command;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.market.MarketComplianceCoordinator;
import net.enthusia.staff.paper.market.MarketCoordinationResult;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/** Text-safe operator surface for case-linked EnthusiaMarket actions. */
public final class MarketCaseCommand implements TabExecutor {
    private static final String PERMISSION = "enthusiastaff.market.restrict";
    private static final String RESTORE_PERMISSION = "enthusiastaff.market.restore";
    private static final String CONFIRM = "CONFIRM";

    private final JavaPlugin plugin;
    private final Supplier<MarketComplianceCoordinator> coordinators;
    private final Supplier<PlayerDirectory> players;
    private final ExecutorService workers;
    private final CommandResponseDispatcher responses;

    public MarketCaseCommand(
            JavaPlugin plugin,
            Supplier<MarketComplianceCoordinator> coordinators,
            Supplier<PlayerDirectory> players,
            ExecutorService workers
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.coordinators = Objects.requireNonNull(coordinators, "coordinators");
        this.players = Objects.requireNonNull(players, "players");
        this.workers = Objects.requireNonNull(workers, "workers");
        responses = new CommandResponseDispatcher(plugin);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (!CommandPermissionGate.require(
                sender, PERMISSION, "You do not have permission to manage Market restrictions."
        )) {
            return true;
        }
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        MarketComplianceCoordinator coordinator = coordinators.get();
        if (actor == null || coordinator == null) {
            sender.sendMessage(Component.text("Market compliance coordination is unavailable."));
            return true;
        }
        if (arguments.length == 0) {
            usage(sender, label);
            return true;
        }
        return dispatch(sender, label, arguments, actor, coordinator);
    }

    private boolean dispatch(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            MarketComplianceCoordinator coordinator
    ) {
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "prepare" -> prepare(sender, label, arguments, actor, coordinator);
            case "approve" -> operation(
                    sender, label, arguments, actor, "approve", coordinator::approveConfiscation
            );
            case "release" -> operation(
                    sender, label, arguments, actor, "release", coordinator::release
            );
            case "restore" -> restore(sender, label, arguments, actor, coordinator);
            case "blacklist" -> blacklist(sender, label, arguments, actor, coordinator);
            case "unblacklist" -> unblacklist(sender, label, arguments, actor, coordinator);
            case "status" -> status(sender, label, arguments, actor, coordinator);
            default -> {
                usage(sender, label);
                yield true;
            }
        };
    }

    private boolean prepare(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            MarketComplianceCoordinator coordinator
    ) {
        if (arguments.length != 5 || !confirmed(arguments[4])) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label + " prepare <player|uuid> <case-id> <stall-id> CONFIRM"
            ));
            return true;
        }
        CaseId caseId = caseId(sender, arguments[2]);
        return caseId == null || withTarget(sender, arguments[1], target -> coordinator.prepareStall(
                actor, target.playerId(), caseId, arguments[3], Optional.empty()
        ));
    }

    private boolean blacklist(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            MarketComplianceCoordinator coordinator
    ) {
        if (arguments.length != 5 || !confirmed(arguments[4])) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label
                            + " blacklist <player|uuid> <case-id> <permanent|ISO-8601> CONFIRM"
            ));
            return true;
        }
        CaseId caseId = caseId(sender, arguments[2]);
        ExpirationParse expiration = expiration(sender, arguments[3]);
        if (caseId == null || !expiration.valid()) {
            return true;
        }
        return withTarget(sender, arguments[1], target -> coordinator.applyBlacklist(
                actor, target.playerId(), caseId, expiration.value()
        ));
    }

    private boolean unblacklist(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            MarketComplianceCoordinator coordinator
    ) {
        if (arguments.length != 5 || !confirmed(arguments[4])) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label
                            + " unblacklist <player|uuid> <case-id> <expected-revision> CONFIRM"
            ));
            return true;
        }
        CaseId caseId = caseId(sender, arguments[2]);
        Long revision = positiveLong(sender, arguments[3]);
        if (caseId == null || revision == null) {
            return true;
        }
        return withTarget(sender, arguments[1], target -> coordinator.removeBlacklist(
                actor, target.playerId(), caseId, revision
        ));
    }

    private boolean restore(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            MarketComplianceCoordinator coordinator
    ) {
        boolean permitted = CommandPermissionGate.require(
                sender, RESTORE_PERMISSION, "Only the Founder may restore Market assets."
        );
        return !permitted || operation(sender, label, arguments, actor, "restore", coordinator::restore);
    }

    private boolean operation(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            String action,
            OperationAction operation
    ) {
        if (arguments.length != 3 || !confirmed(arguments[2])) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label + ' ' + action + " <operation-id> CONFIRM"
            ));
            return true;
        }
        UUID operationId = operationId(sender, arguments[1]);
        if (operationId != null) {
            invoke(sender, () -> operation.apply(actor, operationId));
        }
        return true;
    }

    private boolean status(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            MarketComplianceCoordinator coordinator
    ) {
        if (arguments.length != 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " status <operation-id>"));
            return true;
        }
        UUID operationId = operationId(sender, arguments[1]);
        if (operationId != null) {
            invoke(sender, () -> coordinator.find(actor, operationId));
        }
        return true;
    }

    private boolean withTarget(
            CommandSender sender,
            String query,
            java.util.function.Function<PlayerIdentity, CompletionStage<MarketCoordinationResult>> action
    ) {
        try {
            workers.execute(() -> {
                try {
                    PlayerDirectory directory = players.get();
                    PlayerIdentity target = directory == null ? null : directory.find(query).orElse(null);
                    if (target == null) {
                        responses.send(sender, Component.text("Player was not found; nothing changed."));
                        return;
                    }
                    complete(sender, action.apply(target));
                } catch (RuntimeException exception) {
                    failed(sender, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The moderation work queue is full; nothing changed."));
        }
        return true;
    }

    private void invoke(
            CommandSender sender,
            Supplier<CompletionStage<MarketCoordinationResult>> action
    ) {
        try {
            complete(sender, action.get());
        } catch (RuntimeException exception) {
            failed(sender, exception);
        }
    }

    private void complete(
            CommandSender sender,
            CompletionStage<MarketCoordinationResult> stage
    ) {
        Objects.requireNonNull(stage, "Market action returned no completion stage")
                .whenComplete((result, failure) -> {
                    if (failure != null || result == null) {
                        failed(sender, failure);
                        return;
                    }
                    responses.send(sender, message(result));
                });
    }

    private void failed(CommandSender sender, Throwable failure) {
        Throwable logged = failure == null
                ? new IllegalStateException("Market command completed without a result")
                : failure;
        plugin.getLogger().log(Level.SEVERE, "Market command failed safely", logged);
        responses.send(sender, Component.text("Market operation failed safely; review server logs."));
    }

    private static Component message(MarketCoordinationResult result) {
        MarketComplianceOperation operation = result.operation().orElse(null);
        if (operation == null) {
            return Component.text(result.status() + ": " + result.detail());
        }
        String stall = operation.request().stallId().map(value -> ", stall " + value).orElse("");
        return Component.text(
                result.status() + ": operation " + operation.operationId()
                        + " is " + operation.state()
                        + " for case " + operation.request().caseId().value()
                        + stall + ". " + result.detail()
        );
    }

    private static CaseId caseId(CommandSender sender, String value) {
        try {
            return new CaseId(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Invalid case ID."));
            return null;
        }
    }

    private static UUID operationId(CommandSender sender, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Invalid operation ID."));
            return null;
        }
    }

    private static Long positiveLong(CommandSender sender, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0L) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Report the same bounded validation error below.
        }
        sender.sendMessage(Component.text("Expected revision must be a positive number."));
        return null;
    }

    private static ExpirationParse expiration(CommandSender sender, String value) {
        if (value.equalsIgnoreCase("permanent")) {
            return new ExpirationParse(true, Optional.empty());
        }
        try {
            return new ExpirationParse(true, Optional.of(Instant.parse(value)));
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text("Expiration must be permanent or an ISO-8601 instant."));
            return new ExpirationParse(false, Optional.empty());
        }
    }

    private static boolean confirmed(String value) {
        return CONFIRM.equals(value);
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Usage: /" + label
                        + " <prepare|approve|release|restore|blacklist|unblacklist|status> ..."
        ));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (arguments.length == 1) {
            return matches(
                    arguments[0],
                    List.of("prepare", "approve", "release", "restore", "blacklist", "unblacklist", "status")
            );
        }
        String action = arguments[0].toLowerCase(Locale.ROOT);
        if (arguments.length == 3 && List.of("approve", "release", "restore").contains(action)) {
            return matches(arguments[2], List.of(CONFIRM));
        }
        if (arguments.length == 4 && action.equals("blacklist")) {
            return matches(arguments[3], List.of("permanent"));
        }
        if (arguments.length == 5 && List.of("prepare", "blacklist", "unblacklist").contains(action)) {
            return matches(arguments[4], List.of(CONFIRM));
        }
        return List.of();
    }

    private static List<String> matches(String prefix, List<String> candidates) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    @FunctionalInterface
    private interface OperationAction {
        CompletionStage<MarketCoordinationResult> apply(Actor actor, UUID operationId);
    }

    private record ExpirationParse(boolean valid, Optional<Instant> value) {
        private ExpirationParse {
            value = Objects.requireNonNull(value, "value");
        }
    }
}
