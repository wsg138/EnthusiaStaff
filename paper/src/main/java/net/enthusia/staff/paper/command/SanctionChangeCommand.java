package net.enthusia.staff.paper.command;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
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
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctionChangeCommand implements CommandExecutor, TabCompleter {
    private static final Set<SanctionType> ALL_TYPES = Set.copyOf(EnumSet.allOf(SanctionType.class));
    private static final List<String> CENTRAL_ACTIONS = List.of(
            "end", "reduce", "replace-expiration", "revoke", "full-overturn",
            "remove-escalation", "restore-escalation", "request-overturn",
            "approve-overturn", "deny-overturn"
    );

    private final JavaPlugin plugin;
    private final Supplier<OperationalMode> mode;
    private final Supplier<SanctionChangeService> service;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<CaseLookup> cases;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;

    public SanctionChangeCommand(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<SanctionChangeService> service,
            Supplier<PlayerDirectory> players,
            Supplier<CaseLookup> cases,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.mode = mode;
        this.service = service;
        this.players = players;
        this.cases = cases;
        this.authorization = authorization;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        if (actor == null || !canChangeAnything(actor)) {
            sender.sendMessage(Component.text("You do not have punishment modification authority."));
            return true;
        }
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        boolean central = lowerLabel.equals("removepunishment");
        int minimum = central ? 3 : 2;
        if (arguments.length < minimum) {
            sender.sendMessage(Component.text(central
                    ? "Usage: /removepunishment <player|case> <action> [expiration] <reason> [CONFIRM]"
                    : "Usage: /" + label + " <player|case> <reason> [CONFIRM]"));
            return true;
        }
        SanctionChangeAction action = central ? parseAction(arguments[1]) : aliasAction(lowerLabel);
        if (action == null) {
            sender.sendMessage(Component.text("Unknown sanction change action."));
            return true;
        }
        if (!authorization.permits(actor, action.requiredModerationAction())) {
            sender.sendMessage(Component.text("You are not permitted to perform that punishment change."));
            return true;
        }
        if (!sender.hasPermission(permissionFor(action))) {
            sender.sendMessage(Component.text("You do not have permission for that punishment change."));
            return true;
        }
        int reasonStart = central ? 2 : 1;
        Optional<Instant> expiration = Optional.empty();
        if (action == SanctionChangeAction.REDUCE_DURATION
                || action == SanctionChangeAction.REPLACE_EXPIRATION) {
            if (arguments.length <= reasonStart + 1) {
                sender.sendMessage(Component.text("This action requires an ISO-8601 expiration and a written reason."));
                return true;
            }
            try {
                expiration = Optional.of(Instant.parse(arguments[reasonStart]));
            } catch (java.time.format.DateTimeParseException exception) {
                sender.sendMessage(Component.text("Expiration must be an ISO-8601 instant such as 2026-08-01T00:00:00Z."));
                return true;
            }
            reasonStart++;
        }
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        int reasonEnd = confirmed ? arguments.length - 1 : arguments.length;
        String reason = String.join(" ", Arrays.copyOfRange(arguments, reasonStart, reasonEnd)).trim();
        if (reason.isBlank()) {
            sender.sendMessage(Component.text("A written reason is required."));
            return true;
        }
        if (!confirmed) {
            sender.sendMessage(Component.text("Review only: " + action + " for " + arguments[0] + "."));
            sender.sendMessage(Component.text("No change was made. Append the exact word CONFIRM to commit."));
            return true;
        }
        Optional<Instant> requestedExpiration = expiration;
        submit(sender, () -> apply(
                sender, lowerLabel, arguments[0], action, requestedExpiration, reason, actor
        ));
        return true;
    }

    private void apply(
            CommandSender sender,
            String label,
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
        Set<SanctionType> types = aliasTypes(label);
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
            sender.sendMessage(Component.text("The moderation work queue is full; no change was made."));
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }

    private static SanctionChangeAction aliasAction(String label) {
        return switch (label) {
            case "unban", "unmute" -> SanctionChangeAction.END_EARLY;
            case "removewarning", "unwarn" -> SanctionChangeAction.REVOKE;
            default -> null;
        };
    }

    private static Set<SanctionType> aliasTypes(String label) {
        return switch (label) {
            case "unban" -> Set.of(
                    SanctionType.BAN, SanctionType.NETWORK_BAN, SanctionType.NETWORK_IDENTITY_BAN
            );
            case "unmute" -> Set.of(SanctionType.MUTE);
            case "removewarning", "unwarn" -> Set.of(SanctionType.WARNING);
            default -> ALL_TYPES;
        };
    }

    private static SanctionChangeAction parseAction(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "end", "end-early" -> SanctionChangeAction.END_EARLY;
            case "reduce" -> SanctionChangeAction.REDUCE_DURATION;
            case "expiration", "replace-expiration" -> SanctionChangeAction.REPLACE_EXPIRATION;
            case "revoke" -> SanctionChangeAction.REVOKE;
            case "overturn", "full-overturn" -> SanctionChangeAction.FULL_OVERTURN;
            case "remove-escalation" -> SanctionChangeAction.REMOVE_ESCALATION_CONTRIBUTION;
            case "restore-escalation" -> SanctionChangeAction.RESTORE_ESCALATION_CONTRIBUTION;
            case "request-overturn" -> SanctionChangeAction.REQUEST_FULL_OVERTURN;
            case "approve-overturn" -> SanctionChangeAction.APPROVE_FULL_OVERTURN;
            case "deny-overturn" -> SanctionChangeAction.DENY_FULL_OVERTURN;
            default -> null;
        };
    }

    private boolean canChangeAnything(Actor actor) {
        return Arrays.stream(SanctionChangeAction.values())
                .anyMatch(action -> authorization.permits(actor, action.requiredModerationAction()));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        if (actor == null || !canChangeAnything(actor)
                || !alias.equalsIgnoreCase("removepunishment") || arguments.length != 2) {
            return List.of();
        }
        String prefix = arguments[1].toLowerCase(Locale.ROOT);
        return CENTRAL_ACTIONS.stream()
                .filter(value -> value.startsWith(prefix))
                .filter(value -> {
                    SanctionChangeAction action = parseAction(value);
                    return action != null
                            && authorization.permits(actor, action.requiredModerationAction())
                            && sender.hasPermission(permissionFor(action));
                })
                .toList();
    }

    private static String permissionFor(SanctionChangeAction action) {
        return switch (action) {
            case END_EARLY -> "enthusiastaff.remove.end";
            case REVOKE -> "enthusiastaff.remove.revoke";
            case REDUCE_DURATION, REMOVE_ESCALATION_CONTRIBUTION -> "enthusiastaff.remove.lower";
            case REPLACE_EXPIRATION -> "enthusiastaff.remove.custom-duration";
            case RESTORE_ESCALATION_CONTRIBUTION -> "enthusiastaff.remove.raise";
            case FULL_OVERTURN -> "enthusiastaff.remove.full-overturn";
            case REQUEST_FULL_OVERTURN -> "enthusiastaff.remove.request-overturn";
            case APPROVE_FULL_OVERTURN, DENY_FULL_OVERTURN -> "enthusiastaff.remove.approve-overturn";
        };
    }
}
