package net.enthusia.staff.paper.command;

import java.time.Duration;
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
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
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
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentDraftWorkflow> workflows;
    private final Supplier<PlayerDirectory> players;
    private final AuthorizationPolicy authorization;
    private final PunishmentGuiController gui;
    private final PunishmentRequestCommandHandler requestCommands;
    private final ExecutorService workers;

    public PunishmentCommand(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflows,
            Supplier<PlayerDirectory> players,
            AuthorizationPolicy authorization,
            PunishmentGuiController gui,
            PunishmentRequestCommandHandler requestCommands,
            ExecutorService workers
    ) {
        if (plugin == null || mode == null || workflows == null || players == null || authorization == null
                || gui == null || requestCommands == null || workers == null) {
            throw new IllegalArgumentException("punishment command dependencies must be present");
        }
        this.plugin = plugin;
        this.mode = mode;
        this.workflows = workflows;
        this.players = players;
        this.authorization = authorization;
        this.gui = gui;
        this.requestCommands = requestCommands;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        if (requestCommands.handles(command.getName(), args)) {
            requestCommands.execute(sender, args, actor);
            return true;
        }
        if (!sender.hasPermission(PERMISSION) || !permitsPunishmentDraft(actor)) {
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
            resume(sender, actor, label, args);
            return true;
        }
        prepare(sender, actor, label, args);
        return true;
    }

    private void prepare(CommandSender sender, Actor actor, String label, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            gui.open(player, args[0], label);
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
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                send(sender, Component.text("Moderation storage is not ready; no draft was created.", NamedTextColor.RED));
                return;
            }
            PunishmentDraftEvaluation evaluation = workflow.prepare(
                    new PreparePunishmentDraftRequest(
                            target.playerId(),
                            actor,
                            reasonId,
                            explanation,
                            visibility,
                            label.toLowerCase(Locale.ROOT)
                    ),
                    mode.get()
            );
            if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
                send(sender, Component.text(rejected.code() + ": " + rejected.message(), NamedTextColor.RED));
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
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                send(sender, Component.text("Moderation storage is not ready; no action was taken.", NamedTextColor.RED));
                return;
            }
            PunishmentDraftConfirmation result;
            try {
                result = workflow.confirmRouted(draftId, actor, mode.get());
            } catch (PunishmentDraftCleanupException cleanup) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Punishment draft cleanup failed after case commit " + cleanup.accepted().caseId(),
                        cleanup
                );
                send(sender, Component.text(
                        "Punishment applied as case " + cleanup.accepted().caseId().value()
                                + ", but draft cleanup failed. Retrying confirmation is idempotent.",
                        NamedTextColor.YELLOW
                ));
                return;
            } catch (PunishmentRequestDraftCleanupException cleanup) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Punishment draft cleanup failed after request submission "
                                + cleanup.submitted().request().requestId(),
                        cleanup
                );
                send(sender, Component.text(
                        "Punishment request was submitted, but draft cleanup failed. Retrying is idempotent.",
                        NamedTextColor.YELLOW
                ));
                return;
            }
            sendConfirmation(sender, result);
        });
    }

    private void resume(CommandSender sender, Actor actor, String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /punish resume <target>", NamedTextColor.RED));
            return;
        }
        if (sender instanceof Player player) {
            gui.resume(player, args[1], label);
            return;
        }
        submit(sender, () -> {
            PlayerIdentity target = findTarget(sender, args[1]);
            if (target == null) {
                return;
            }
            PunishmentDraftWorkflow workflow = workflows.get();
            if (workflow == null) {
                send(sender, Component.text("Moderation storage is not ready; no draft was opened.", NamedTextColor.RED));
                return;
            }
            PunishmentDraft draft = workflow.resume(actor.id(), target.playerId()).orElse(null);
            if (draft == null) {
                send(sender, Component.text("No unexpired punishment draft exists for that target.", NamedTextColor.RED));
                return;
            }
            sendDraft(sender, target, draft);
        });
    }

    private PlayerIdentity findTarget(CommandSender sender, String input) {
        PlayerDirectory directory = players.get();
        if (directory == null) {
            send(sender, Component.text("Moderation storage is not ready; no player was resolved.", NamedTextColor.RED));
            return null;
        }
        PlayerIdentity target = directory.find(input).orElse(null);
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
                            "Punishment storage is unavailable; no punishment or request was created.",
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
                "Target: " + targetName(target) + " | reason: " + draft.reasonId(),
                NamedTextColor.GRAY
        ));
        send(sender, Component.text(
                "Recommended: " + describe(prepared.assessment().sanctions()),
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
                "Draft expires: " + draft.expiresAt(),
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
                "Target: " + targetName(target) + " | reason: " + draft.reasonId(),
                NamedTextColor.GRAY
        ));
        send(sender, Component.text(
                "Frozen recommendation: " + describe(draft.expectation().sanctions()),
                NamedTextColor.YELLOW
        ));
        send(sender, Component.text(
                "Policy version: " + draft.expectation().configurationVersion()
                        + " | expires: " + draft.expiresAt(),
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
                    "Punishment request " + (requested.submitted().replayed() ? "replayed" : "submitted")
                            + "; expires " + requested.submitted().request().expiresAt() + '.',
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentDraftConfirmation.Rejected rejected) {
            send(sender, Component.text(rejected.code() + ": " + rejected.message(), NamedTextColor.RED));
        }
    }

    private void send(CommandSender sender, Component message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    private boolean permitsPunishmentDraft(Actor actor) {
        return actor != null && (authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || authorization.permits(actor, ModerationAction.REQUEST_POLICY_SANCTION));
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

    private static String targetName(PlayerIdentity target) {
        return target.currentUsername().orElse("offline target");
    }

    private static String describe(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(PunishmentCommand::describe)
                .reduce((left, right) -> left + " + " + right)
                .orElse("No sanction");
    }

    private static String describe(SanctionSpec sanction) {
        SanctionLength length = sanction.length();
        if (length.isInstant()) {
            return sanction.type().name();
        }
        if (length.isPermanent()) {
            return "permanent " + sanction.type().name();
        }
        return duration(length.temporary().orElseThrow()) + ' ' + sanction.type().name();
    }

    private static String duration(Duration duration) {
        if (duration.toDaysPart() > 0 && duration.toHoursPart() == 0
                && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toDays() + "d";
        }
        if (duration.toHours() > 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toHours() + "h";
        }
        return duration.toString();
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
