package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PreparePunishmentDraftRequest;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentDraftCleanupException;
import net.enthusia.staff.domain.application.PunishmentDraftConfirmation;
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentRequestDraftCleanupException;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PlayerIdentity;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.punishment.PunishmentCommandFilter;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
import net.enthusia.staff.paper.punishment.PunishmentGuiRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "enthusiastaff.punish.configured";
    private static final String PRIVATE_FLAG = "--private";

    private final JavaPlugin plugin;
    private final Supplier<PunishmentDraftWorkflow> workflows;
    private final Supplier<PlayerDirectory> players;
    private final AuthorizationPolicy authorization;
    private final PunishmentGuiController gui;
    private final PunishmentRequestCommandHandler requestCommands;
    private final ExecutorService workers;

    public PunishmentCommand(
            JavaPlugin plugin,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            PunishmentGuiController gui,
            PunishmentRequestCommandHandler requestCommands,
            ExecutorService workers
    ) {
        if (plugin == null || workflows == null || players == null || authorization == null
                || gui == null || requestCommands == null || workers == null) {
            throw new IllegalArgumentException("punishment command dependencies must be present");
        }
        this.plugin = plugin;
        this.workflows = workflows;
        this.players = players;
        this.authorization = authorization;
        this.gui = gui;
        this.requestCommands = requestCommands;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Actor actor = PaperActorResolver.actor(sender);
        if (requestCommands.handles(command.getName(), args)) {
            requestCommands.execute(sender, args, actor);
            return true;
        }
        if (!sender.hasPermission(PERMISSION)
                || !permitsPunishmentDraft(actor)) {
            sender.sendMessage(Component.text(
                    "You are not allowed to prepare configured punishments or requests.",
                    NamedTextColor.RED
            ));
            return true;
        }
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        if ("confirm".equalsIgnoreCase(args[0])) {
            confirm(sender, actor, args);
            return true;
        }
        if ("resume".equalsIgnoreCase(args[0])) {
            resume(sender, actor, args);
            return true;
        }
        prepare(sender, actor, label, args);
        return true;
    }

    private void prepare(CommandSender sender, Actor actor, String label, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            PlayerIdentity target = resolveTarget(sender, args[0]);
            if (target != null) {
                gui.open(player, target, label);
            }
            return;
        }
        if (args.length < 2) {
            usage(sender, label);
            return;
        }
        submit(sender, () -> {
            PlayerIdentity target = findTarget(sender, args[0]);
            if (target == null) {
                return;
            }
            String reasonId = args[1];
            CaseVisibility visibility = containsIgnoreCase(args, PRIVATE_FLAG)
                    ? CaseVisibility.PRIVATE
                    : CaseVisibility.PUBLIC;
            String explanation = explanation(args, 2);
            if (explanation.isBlank()) {
                explanation = "Prepared via /" + label + " for configured reason " + reasonId;
            }
            PunishmentDraftEvaluation evaluation = workflows.get().prepare(
                    new PreparePunishmentDraftRequest(
                            target.playerId(),
                            actor,
                            reasonId,
                            explanation,
                            visibility,
                            label
                    ),
                    OperationalMode.ACTIVE
            );
            if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
                send(sender, Component.text(
                        rejected.code() + ": " + rejected.message(),
                        NamedTextColor.RED
                ));
                return;
            }
            PunishmentDraftEvaluation.Prepared prepared = (PunishmentDraftEvaluation.Prepared) evaluation;
            sendPrepared(sender, target, prepared);
        });
    }

    private void confirm(CommandSender sender, Actor actor, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /punish confirm <draft-id>", NamedTextColor.RED));
            return;
        }
        UUID draftId = parseUuid(sender, args[1]);
        if (draftId == null) {
            return;
        }
        submit(sender, () -> {
            PunishmentDraftConfirmation result;
            try {
                result = workflows.get().confirmRouted(draftId, actor, OperationalMode.ACTIVE);
            } catch (PunishmentDraftCleanupException cleanup) {
                send(sender, Component.text(
                        "Punishment applied as case " + cleanup.accepted().caseId().value()
                                + ", but draft cleanup failed. Retrying confirmation is idempotent.",
                        NamedTextColor.YELLOW
                ));
                return;
            } catch (PunishmentRequestDraftCleanupException cleanup) {
                send(sender, Component.text(
                        "Punishment request " + cleanup.submitted().request().requestId()
                                + " was submitted, but draft cleanup failed. Retrying is idempotent.",
                        NamedTextColor.YELLOW
                ));
                return;
            }
            sendConfirmation(sender, result);
        });
    }

    private void resume(CommandSender sender, Actor actor, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /punish resume <target>", NamedTextColor.RED));
            return;
        }
        submit(sender, () -> {
            PlayerIdentity target = findTarget(sender, args[1]);
            if (target == null) {
                return;
            }
            PunishmentDraft draft = workflows.get().resume(actor.id(), target.playerId()).orElse(null);
            if (draft == null) {
                send(sender, Component.text(
                        "No unexpired punishment draft exists for that target.",
                        NamedTextColor.RED
                ));
                return;
            }
            sendDraft(sender, target, draft);
        });
    }

    private PlayerIdentity resolveTarget(CommandSender sender, String input) {
        try {
            return findTarget(sender, input);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to resolve punishment target", exception);
            sender.sendMessage(Component.text(
                    "Punishment storage is unavailable; no draft was created.",
                    NamedTextColor.RED
            ));
            return null;
        }
    }

    private PlayerIdentity findTarget(CommandSender sender, String input) {
        PlayerIdentity target = players.get().find(input).orElse(null);
        if (target == null) {
            send(sender, Component.text("Unknown player: " + input, NamedTextColor.RED));
        }
        return target;
    }

    private void submit(CommandSender sender, Runnable operation) {
        try {
            workers.submit(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Punishment command storage operation failed", exception);
                    send(sender, Component.text(
                            "Punishment storage is unavailable; no punishment was created.",
                            NamedTextColor.RED
                    ));
                }
            });
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().log(Level.WARNING, "Punishment worker rejected command", exception);
            sender.sendMessage(Component.text(
                    "Punishment storage is unavailable; try again shortly.",
                    NamedTextColor.RED
            ));
        }
    }

    private void sendPrepared(
            CommandSender sender,
            PlayerIdentity target,
            PunishmentDraftEvaluation.Prepared prepared
    ) {
        PunishmentDraft draft = prepared.draft();
        send(sender, Component.text("Punishment draft prepared", NamedTextColor.GOLD));
        send(sender, Component.text(
                "Target: " + target.lastKnownName() + " | reason: " + draft.reasonId(),
                NamedTextColor.GRAY
        ));
        send(sender, Component.text(
                "Recommended: " + PunishmentGuiRenderer.describe(prepared.assessment().sanctions()),
                NamedTextColor.YELLOW
        ));
        send(sender, Component.text(
                "Prior ordinal: " + prepared.assessment().escalation().rawOrdinal()
                        + " | selected step: " + prepared.assessment().escalation().selectedStep().label(),
                NamedTextColor.GRAY
        ));
        prepared.assessment().policy().examples().stream().limit(3).forEach(example -> send(
                sender,
                Component.text("Example: " + example, NamedTextColor.DARK_GRAY)
        ));
        send(sender, Component.text(
                "Draft ID: " + draft.draftId() + " | expires: " + draft.expiresAt(),
                NamedTextColor.GRAY
        ));
        send(sender, Component.text(
                "Review the frozen outcome, then run /punish confirm " + draft.draftId()
                        + ". Required approvals are submitted durably instead of applied directly.",
                NamedTextColor.AQUA
        ));
    }

    private void sendDraft(CommandSender sender, PlayerIdentity target, PunishmentDraft draft) {
        send(sender, Component.text("Resumed punishment draft", NamedTextColor.GOLD));
        send(sender, Component.text(
                "Target: " + target.lastKnownName() + " | reason: " + draft.reasonId(),
                NamedTextColor.GRAY
        ));
        send(sender, Component.text(
                "Frozen recommendation: " + PunishmentGuiRenderer.describe(draft.expectation().sanctions()),
                NamedTextColor.YELLOW
        ));
        send(sender, Component.text(
                "Policy version: " + draft.expectation().configurationVersion()
                        + " | draft revision: " + draft.revision(),
                NamedTextColor.GRAY
        ));
        send(sender, Component.text(
                "Confirm with /punish confirm " + draft.draftId(),
                NamedTextColor.AQUA
        ));
    }

    private void sendConfirmation(CommandSender sender, PunishmentDraftConfirmation result) {
        if (result instanceof PunishmentDraftConfirmation.Applied applied) {
            send(sender, Component.text(
                    "Punishment applied as case " + applied.accepted().caseId().value()
                            + (applied.accepted().replayed() ? " (idempotent replay)." : "."),
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentDraftConfirmation.Requested requested) {
            send(sender, Component.text(
                    "Punishment request " + requested.submitted().request().requestId()
                            + (requested.submitted().replayed() ? " replayed" : " submitted")
                            + "; expires " + requested.submitted().request().expiresAt() + '.',
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentDraftConfirmation.Rejected rejected) {
            send(sender, Component.text(
                    rejected.code() + ": " + rejected.message(),
                    NamedTextColor.RED
            ));
        }
    }

    private void send(CommandSender sender, Component message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    private static boolean permitsPunishmentDraft(Actor actor) {
        AuthorizationPolicy policy = new net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy();
        return policy.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || policy.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION);
    }

    private static UUID parseUuid(CommandSender sender, String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Invalid draft ID.", NamedTextColor.RED));
            return null;
        }
    }

    private static boolean containsIgnoreCase(String[] args, String value) {
        return Arrays.stream(args).anyMatch(argument -> value.equalsIgnoreCase(argument));
    }

    private static String explanation(String[] args, int fromIndex) {
        List<String> values = Arrays.stream(args)
                .skip(fromIndex)
                .filter(value -> !PRIVATE_FLAG.equalsIgnoreCase(value))
                .toList();
        return String.join(" ", values).trim();
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Usage: /" + label + " <target> [reason-id] [--private] [internal explanation]",
                NamedTextColor.YELLOW
        ));
        sender.sendMessage(Component.text(
                "Draft controls: /punish resume <target> | /punish confirm <draft-id>",
                NamedTextColor.GRAY
        ));
        if ("punish".equalsIgnoreCase(label)) {
            sender.sendMessage(Component.text(
                    "Request review: /punish requests | review | approve | deny",
                    NamedTextColor.GRAY
            ));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>(requestCommands.complete(command.getName(), args));
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("confirm".startsWith(prefix)) {
                completions.add("confirm");
            }
            if ("resume".startsWith(prefix)) {
                completions.add("resume");
            }
            if (sender instanceof Player player) {
                player.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .forEach(completions::add);
            }
            return completions.stream().distinct().toList();
        }
        if (args.length == 2 && "resume".equalsIgnoreCase(args[0]) && sender instanceof Player player) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return player.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return completions;
    }
}
