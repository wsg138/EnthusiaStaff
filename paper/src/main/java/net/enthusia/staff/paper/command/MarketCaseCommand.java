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
    private static final String USAGE_PREFIX = "Usage: /";
    private static final String ACTION_PREPARE = "prepare";
    private static final String ACTION_APPROVE = "approve";
    private static final String ACTION_RELEASE = "release";
    private static final String ACTION_RESTORE = "restore";
    private static final String ACTION_BLACKLIST = "blacklist";
    private static final String ACTION_UNBLACKLIST = "unblacklist";
    private static final String ACTION_STATUS = "status";
    private static final List<String> ACTIONS = List.of(
            ACTION_PREPARE,
            ACTION_APPROVE,
            ACTION_RELEASE,
            ACTION_RESTORE,
            ACTION_BLACKLIST,
            ACTION_UNBLACKLIST,
            ACTION_STATUS
    );
    private static final List<String> OPERATION_ACTIONS = List.of(
            ACTION_APPROVE,
            ACTION_RELEASE,
            ACTION_RESTORE
    );
    private static final List<String> CONFIRMATION_ACTIONS = List.of(
            ACTION_PREPARE,
            ACTION_BLACKLIST,
            ACTION_UNBLACKLIST
    );
    private static final int ACTION_ARGUMENT_COUNT = 1;
    private static final int STATUS_ARGUMENT_COUNT = 2;
    private static final int OPERATION_ARGUMENT_COUNT = 3;
    private static final int EXPIRATION_ARGUMENT_COUNT = 4;
    private static final int TARGET_OPERATION_ARGUMENT_COUNT = 5;
    private static final long MINIMUM_REVISION = 1L;

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
            case ACTION_PREPARE -> prepare(sender, label, arguments, actor, coordinator);
            case ACTION_APPROVE -> operation(
                    sender, label, arguments, actor, ACTION_APPROVE, coordinator::approveConfiscation
            );
            case ACTION_RELEASE -> operation(
                    sender, label, arguments, actor, ACTION_RELEASE, coordinator::release
            );
            case ACTION_RESTORE -> restore(sender, label, arguments, actor, coordinator);
            case ACTION_BLACKLIST -> blacklist(sender, label, arguments, actor, coordinator);
            case ACTION_UNBLACKLIST -> unblacklist(sender, label, arguments, actor, coordinator);
            case ACTION_STATUS -> status(sender, label, arguments, actor, coordinator);
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
        if (arguments.length != TARGET_OPERATION_ARGUMENT_COUNT || !confirmed(arguments[4])) {
            sender.sendMessage(Component.text(
                    USAGE_PREFIX + label + " prepare <player|uuid> <case-id> <stall-id> CONFIRM"
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
        if (arguments.length != TARGET_OPERATION_ARGUMENT_COUNT || !confirmed(arguments[4])) {
            sender.sendMessage(Component.text(
                    USAGE_PREFIX + label
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
        if (arguments.length != TARGET_OPERATION_ARGUMENT_COUNT || !confirmed(arguments[4])) {
            sender.sendMessage(Component.text(
                    USAGE_PREFIX + label
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
        return !permitted || operation(
                sender, label, arguments, actor, ACTION_RESTORE, coordinator::restore
        );
    }

    private boolean operation(
            CommandSender sender,
            String label,
            String[] arguments,
            Actor actor,
            String action,
            OperationAction operation
    ) {
        if (arguments.length != OPERATION_ARGUMENT_COUNT || !confirmed(arguments[2])) {
            sender.sendMessage(Component.text(
                    USAGE_PREFIX + label + ' ' + action + " <operation-id> CONFIRM"
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
        if (arguments.length != STATUS_ARGUMENT_COUNT) {
            sender.sendMessage(Component.text(USAGE_PREFIX + label + " status <operation-id>"));
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
            if (parsed >= MINIMUM_REVISION) {
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
                USAGE_PREFIX + label
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
        String action = arguments[0].toLowerCase(Locale.ROOT);
        return switch (arguments.length) {
            case ACTION_ARGUMENT_COUNT -> matches(arguments[0], ACTIONS);
            case OPERATION_ARGUMENT_COUNT -> operationConfirmation(action, arguments[2]);
            case EXPIRATION_ARGUMENT_COUNT -> expirationCompletion(action, arguments[3]);
            case TARGET_OPERATION_ARGUMENT_COUNT -> targetConfirmation(action, arguments[4]);
            default -> List.of();
        };
    }

    private static List<String> operationConfirmation(String action, String prefix) {
        return OPERATION_ACTIONS.contains(action) ? matches(prefix, List.of(CONFIRM)) : List.of();
    }

    private static List<String> expirationCompletion(String action, String prefix) {
        return ACTION_BLACKLIST.equals(action) ? matches(prefix, List.of("permanent")) : List.of();
    }

    private static List<String> targetConfirmation(String action, String prefix) {
        return CONFIRMATION_ACTIONS.contains(action) ? matches(prefix, List.of(CONFIRM)) : List.of();
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
