package net.enthusia.staff.paper.command;

import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.sanction.SanctionChangeAccess;
import net.enthusia.staff.paper.sanction.SanctionChangeGuiController;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctionChangeCommand implements CommandExecutor, TabCompleter {
    private static final int SINGLE_ARGUMENT = 1;
    private static final int ALIAS_MIN_ARGUMENTS = 2;
    private static final int CENTRAL_MIN_ARGUMENTS = 3;
    private final JavaPlugin plugin;
    private final Supplier<OperationalMode> mode;
    private final Supplier<SanctionChangeService> service;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<CaseLookup> cases;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;
    private final SanctionChangeGuiController gui;
    private volatile ExactSanctionPickerGui exactPicker;

    public SanctionChangeCommand(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<SanctionChangeService> service,
            Supplier<PlayerDirectory> players,
            Supplier<CaseLookup> cases,
            AuthorizationPolicy authorization,
            ExecutorService workers,
            SanctionChangeGuiController gui
    ) {
        this.plugin = plugin;
        this.mode = mode;
        this.service = service;
        this.players = players;
        this.cases = cases;
        this.authorization = authorization;
        this.workers = workers;
        this.gui = gui;
    }

    public void configureExactSanctionPicker(ExactSanctionPickerGui picker) {
        exactPicker = java.util.Objects.requireNonNull(picker, "picker");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        Actor actor = authorizedActor(sender);
        if (actor == null) {
            return true;
        }
        String route = CommandRoute.canonicalName(command);
        boolean central = route.equals("removepunishment");
        if (sender instanceof Player && !central && arguments.length > SINGLE_ARGUMENT) {
            sender.sendMessage(StaffMessageStyle.usage(
                    "Use /" + route + " <player> to choose the exact punishment in the GUI. "
                            + "Multi-argument legacy shortcuts are console-only."
            ));
            return true;
        }
        if (openAliasGui(sender, arguments, route, central)) {
            return true;
        }
        if (!hasMinimumArguments(sender, label, arguments, central)) {
            return true;
        }
        SanctionChangeAction action = authorizedAction(sender, actor, arguments, route, central);
        if (action == null) {
            return true;
        }
        ChangeInput input = changeInput(sender, arguments, action, central);
        if (input == null) {
            return true;
        }
        if (!input.confirmed()) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("Review only: " + action + " for " + arguments[0] + ".")));
            sender.sendMessage(StaffMessageStyle.style(Component.text("No change was made. Append the exact word CONFIRM to commit.")));
            return true;
        }
        submit(sender, () -> apply(
                sender, route, arguments[0], action, input.expiration(), input.reason(), actor
        ));
        return true;
    }

    private Actor authorizedActor(CommandSender sender) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        if (actor != null && SanctionChangeAccess.canChangeAnything(authorization, actor)) {
            return actor;
        }
        sender.sendMessage(StaffMessageStyle.style(Component.text("You do not have punishment modification authority.")));
        return null;
    }

    private boolean openAliasGui(CommandSender sender, String[] arguments, String route, boolean central) {
        if (arguments.length != SINGLE_ARGUMENT || !(sender instanceof Player player)) {
            return false;
        }
        ExactSanctionPickerGui picker = exactPicker;
        if (picker != null) {
            SanctionChangeAction action = central ? null : SanctionChangeAccess.aliasAction(route);
            String selection = action == SanctionChangeAction.END_EARLY ? "end"
                    : action == SanctionChangeAction.REVOKE ? "remove" : "change";
            picker.open(player, arguments[0], selection, SanctionChangeAccess.aliasTypes(route));
            return true;
        }
        if (central) {
            return false;
        }
        gui.open(player, arguments[0], route);
        return true;
    }

    private static boolean hasMinimumArguments(
            CommandSender sender,
            String label,
            String[] arguments,
            boolean central
    ) {
        int minimum = central ? CENTRAL_MIN_ARGUMENTS : ALIAS_MIN_ARGUMENTS;
        if (arguments.length >= minimum) {
            return true;
        }
        sender.sendMessage(StaffMessageStyle.style(Component.text(central
                ? "Usage: /removepunishment <player|case> <action> [expiration] <reason> [CONFIRM]"
                : "Usage: /" + label + " <player|case> <reason> [CONFIRM]")));
        return false;
    }

    private SanctionChangeAction authorizedAction(
            CommandSender sender,
            Actor actor,
            String[] arguments,
            String route,
            boolean central
    ) {
        SanctionChangeAction action = central
                ? SanctionChangeAccess.parseAction(arguments[1])
                : SanctionChangeAccess.aliasAction(route);
        if (action == null) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("Unknown sanction change action.")));
            return null;
        }
        if (!authorization.permits(actor, action.requiredModerationAction())) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("You are not permitted to perform that punishment change.")));
            return null;
        }
        if (!sender.hasPermission(SanctionChangeAccess.permissionFor(action))) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("You do not have permission for that punishment change.")));
            return null;
        }
        return action;
    }

    private static ChangeInput changeInput(
            CommandSender sender,
            String[] arguments,
            SanctionChangeAction action,
            boolean central
    ) {
        int reasonStart = central ? 2 : 1;
        Optional<Instant> expiration = Optional.empty();
        if (requiresExpiration(action)) {
            if (arguments.length <= reasonStart + 1) {
                sender.sendMessage(StaffMessageStyle.style(Component.text(
                        "This action requires an ISO-8601 expiration and a written reason."
                )));
                return null;
            }
            expiration = expiration(sender, arguments[reasonStart]);
            if (expiration.isEmpty()) {
                return null;
            }
            reasonStart++;
        }
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        int reasonEnd = confirmed ? arguments.length - 1 : arguments.length;
        String reason = String.join(" ", Arrays.copyOfRange(arguments, reasonStart, reasonEnd)).trim();
        if (reason.isBlank()) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("A written reason is required.")));
            return null;
        }
        return new ChangeInput(expiration, reason, confirmed);
    }

    private static boolean requiresExpiration(SanctionChangeAction action) {
        return action == SanctionChangeAction.REDUCE_DURATION
                || action == SanctionChangeAction.REPLACE_EXPIRATION;
    }

    private static Optional<Instant> expiration(CommandSender sender, String input) {
        try {
            return Optional.of(Instant.parse(input));
        } catch (java.time.format.DateTimeParseException exception) {
            sender.sendMessage(StaffMessageStyle.style(Component.text(
                    "Expiration must be an ISO-8601 instant such as 2026-08-01T00:00:00Z."
            )));
            return Optional.empty();
        }
    }

    private record ChangeInput(Optional<Instant> expiration, String reason, boolean confirmed) {
    }

    private void apply(
            CommandSender sender,
            String route,
            String target,
            SanctionChangeAction action,
            Optional<Instant> expiration,
            String reason,
            Actor actor
    ) {
        SanctionChangeService loadedService = service.get();
        PlayerDirectory directory = players.get();
        CaseLookup lookup = cases.get();
        if (loadedService == null || directory == null || lookup == null) {
            send(sender, "Moderation storage is not ready; no change was made.");
            return;
        }
        Set<SanctionType> types = SanctionChangeAccess.aliasTypes(route);
        CaseId caseId = resolveCase(target, types, directory, lookup);
        if (caseId == null) {
            send(sender, "No matching case was found for that player, UUID, or case ID.");
            return;
        }
        SanctionChangeRequest request = new SanctionChangeRequest(
                new IdempotencyKey("change:" + UUID.randomUUID()),
                caseId,
                actor,
                action,
                expiration,
                reason
        );
        SanctionChangeResult result = loadedService.apply(request, mode.get());
        if (result instanceof SanctionChangeResult.Applied applied) {
            send(sender, "Sanction change committed for case " + caseId + "; affected sanctions="
                    + applied.affectedSanctions() + '.');
        } else {
            SanctionChangeResult.Rejected rejected = (SanctionChangeResult.Rejected) result;
            send(sender, rejected.code() + ": " + rejected.message());
        }
    }

    private static CaseId resolveCase(
            String target,
            Set<SanctionType> types,
            PlayerDirectory directory,
            CaseLookup cases
    ) {
        try {
            CaseId direct = new CaseId(target);
            if (cases.exists(direct)) {
                return direct;
            }
        } catch (IllegalArgumentException ignored) {
            // Continue with UUID or historical username resolution.
        }
        PlayerIdentity player = directory.find(target).orElse(null);
        return player == null ? null : cases.latestCase(player.playerId(), types, true).orElse(null);
    }

    private void submit(CommandSender sender, Runnable action) {
        try {
            workers.execute(action);
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(StaffMessageStyle.style(Component.text("The moderation work queue is full; no change was made.")));
        }
    }

    private void send(CommandSender sender, String message) {
        // Async storage results must reach Players through their entity scheduler.
        // The shared dispatcher uses the global scheduler only for console senders.
        new CommandResponseDispatcher(plugin).send(sender, Component.text(message));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        String route = CommandRoute.canonicalName(command);
        if (actor == null || !SanctionChangeAccess.canChangeAnything(authorization, actor)
                || !route.equals("removepunishment") || arguments.length != 2) {
            return List.of();
        }
        String prefix = arguments[1].toLowerCase(Locale.ROOT);
        return SanctionChangeAccess.CENTRAL_ACTIONS.stream()
                .filter(value -> value.startsWith(prefix))
                .filter(value -> {
                    SanctionChangeAction action = SanctionChangeAccess.parseAction(value);
                    return action != null
                            && authorization.permits(actor, action.requiredModerationAction())
                            && sender.hasPermission(SanctionChangeAccess.permissionFor(action));
                })
                .toList();
    }
}
