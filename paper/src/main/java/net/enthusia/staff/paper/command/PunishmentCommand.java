package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PreparePunishmentDraftRequest;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentDraftCleanupException;
import net.enthusia.staff.domain.application.PunishmentDraftEvaluation;
import net.enthusia.staff.domain.application.PunishmentDraftWorkflow;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.punishment.PunishmentCommandFilter;
import net.enthusia.staff.paper.punishment.PunishmentGuiController;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentDraftWorkflow> workflow;
    private final Supplier<PlayerDirectory> playerDirectory;
    private final AuthorizationPolicy authorization;
    private final ReasonPolicyRepository policies;
    private final ExecutorService workers;
    private final PunishmentGuiController gui;

    public PunishmentCommand(
            JavaPlugin plugin,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentDraftWorkflow> workflow,
            Supplier<PlayerDirectory> playerDirectory,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            ExecutorService workers,
            PunishmentGuiController gui
    ) {
        this.plugin = plugin;
        this.mode = mode;
        this.workflow = workflow;
        this.playerDirectory = playerDirectory;
        this.authorization = authorization;
        this.policies = policies;
        this.workers = workers;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        if (actor == null
                || !authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || !sender.hasPermission("enthusiastaff.punish.configured")) {
            sender.sendMessage(Component.text("You do not have punishment authority."));
            return true;
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("confirm")) {
            confirm(sender, label, arguments[1], actor);
            return true;
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("resume")) {
            if (sender instanceof Player player) {
                gui.resume(player, arguments[1], label);
                return true;
            }
            resume(sender, label, arguments[1], actor);
            return true;
        }
        if (arguments.length == 1
                && !arguments[0].equalsIgnoreCase("confirm")
                && !arguments[0].equalsIgnoreCase("resume")
                && sender instanceof Player player) {
            gui.open(player, arguments[0], label);
            return true;
        }
        if (arguments.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label + " <player|uuid> <reason-id> [--private] [internal explanation]"
            ));
            sender.sendMessage(Component.text("Resume with /punish resume <player>; confirm with /"
                    + label + " confirm <draft-id>."));
            return true;
        }
        preview(sender, label, arguments, actor);
        return true;
    }

    private void preview(CommandSender sender, String label, String[] arguments, Actor actor) {
        ReasonPolicy policy = policies.find(arguments[1]).orElse(null);
        if (policy == null) {
            sender.sendMessage(Component.text("Unknown configured reason: " + arguments[1]));
            return;
        }
        boolean explicitlyPrivate = arguments.length > 2 && arguments[2].equalsIgnoreCase("--private");
        CaseVisibility visibility = explicitlyPrivate ? CaseVisibility.PRIVATE : CaseVisibility.PUBLIC;
        int explanationStart = explicitlyPrivate ? 3 : 2;
        String explanation = arguments.length > explanationStart
                ? String.join(" ", Arrays.copyOfRange(arguments, explanationStart, arguments.length)).trim()
                : "Issued through the central punishment command";
        submit(sender, () -> {
            PlayerDirectory directory = playerDirectory.get();
            PunishmentDraftWorkflow drafts = workflow.get();
            if (directory == null || drafts == null) {
                send(sender, "Moderation storage is not ready; no action was taken.");
                return;
            }
            PlayerIdentity target = directory.find(arguments[0]).orElse(null);
            if (target == null) {
                send(sender, "Player is not present in the authoritative directory. UUIDs and historical names are accepted.");
                return;
            }
            PunishmentDraftEvaluation evaluation = drafts.prepare(
                    new PreparePunishmentDraftRequest(
                            target.playerId(), actor, policy.id(), explanation, visibility,
                            label.toLowerCase(Locale.ROOT)
                    ),
                    mode.get()
            );
            if (evaluation instanceof PunishmentDraftEvaluation.Rejected rejected) {
                send(sender, rejected.code() + ": " + rejected.message());
                return;
            }
            PunishmentDraftEvaluation.Prepared prepared = (PunishmentDraftEvaluation.Prepared) evaluation;
            PunishmentAssessment assessment = prepared.assessment();
            if (!PunishmentCommandFilter.matches(label, assessment.sanctions())) {
                drafts.discard(prepared.draft().draftId(), actor.id());
                send(sender, "The recommended step does not contain the sanction type selected by /" + label + ".");
                return;
            }
            send(sender, "Review: " + displayName(target) + " | " + policy.id() + " | step "
                    + assessment.escalation().effectiveOrdinal() + " | " + describe(assessment.sanctions()));
            send(sender, "Visibility: " + visibility.name() + ". No punishment has been created.");
            send(sender, "Confirm within 24 hours: /" + label + " confirm " + prepared.draft().draftId());
        });
    }

    private void confirm(CommandSender sender, String label, String token, Actor actor) {
        UUID draftId;
        try {
            draftId = UUID.fromString(token);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("That punishment draft identifier is invalid."));
            return;
        }
        submit(sender, () -> {
            PunishmentDraftWorkflow drafts = workflow.get();
            if (drafts == null) {
                send(sender, "Moderation storage is not ready; no action was taken.");
                return;
            }
            PunishmentDraft draft = drafts.find(draftId, actor.id()).orElse(null);
            if (draft == null) {
                send(sender, "That punishment draft is missing, expired, or belongs to another actor.");
                return;
            }
            if (!draft.commandName().equals(label.toLowerCase(Locale.ROOT))) {
                send(sender, "Confirm this draft with /" + draft.commandName() + " confirm " + draft.draftId() + '.');
                return;
            }
            PunishmentResult result;
            try {
                result = drafts.confirm(draftId, actor, mode.get());
            } catch (PunishmentDraftCleanupException exception) {
                plugin.getLogger().log(
                        java.util.logging.Level.SEVERE,
                        "Punishment draft cleanup failed after case commit " + exception.accepted().caseId(),
                        exception
                );
                send(sender, "Punishment committed as case " + exception.accepted().caseId()
                        + ", but draft cleanup failed. Retrying the same confirmation is idempotent.");
                return;
            }
            if (result instanceof PunishmentResult.Accepted accepted) {
                send(sender, "Punishment committed as case " + accepted.caseId()
                        + (accepted.replayed() ? " (idempotent replay)" : "") + ".");
            } else {
                PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
                send(sender, rejected.code() + ": " + rejected.message());
                if ("RECOMMENDATION_CHANGED".equals(rejected.code())) {
                    send(sender, "The stale draft was retained. Run the preview again to replace it after review.");
                }
            }
        });
    }

    private void resume(CommandSender sender, String label, String player, Actor actor) {
        submit(sender, () -> {
            PlayerDirectory directory = playerDirectory.get();
            PunishmentDraftWorkflow drafts = workflow.get();
            if (directory == null || drafts == null) {
                send(sender, "Moderation storage is not ready; no action was taken.");
                return;
            }
            PlayerIdentity target = directory.find(player).orElse(null);
            if (target == null) {
                send(sender, "Player is not present in the authoritative directory. UUIDs and historical names are accepted.");
                return;
            }
            PunishmentDraft draft = drafts.resume(actor.id(), target.playerId()).orElse(null);
            if (draft == null) {
                send(sender, "No unexpired punishment draft exists for " + displayName(target) + ".");
                return;
            }
            if (!"punish".equalsIgnoreCase(label)
                    && !draft.commandName().equals(label.toLowerCase(Locale.ROOT))) {
                send(sender, "That draft belongs to /" + draft.commandName() + ". Resume it with /punish instead.");
                return;
            }
            send(sender, "Resumed: " + displayName(target) + " | " + draft.reasonId() + " | step "
                    + draft.expectation().stepOrdinal() + " (" + draft.expectation().stepLabel() + ") | "
                    + describe(draft.expectation().sanctions()));
            send(sender, "Visibility: " + draft.visibility().name() + ". Confirm with /"
                    + draft.commandName() + " confirm " + draft.draftId());
        });
    }

    private void submit(CommandSender sender, Runnable work) {
        try {
            workers.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Punishment workflow failed", exception);
                    send(sender, "The punishment workflow failed and its outcome was not confirmed. Check case history before retrying.");
                }
            });
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The moderation work queue is full; no action was taken."));
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }

    private static String displayName(PlayerIdentity target) {
        return target.currentUsername().orElse(target.playerId().toString()) + " (" + target.playerId() + ')';
    }

    private static String describe(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(PunishmentCommand::describe).reduce((left, right) -> left + " + " + right)
                .orElseThrow();
    }

    private static String describe(SanctionSpec sanction) {
        SanctionLength length = sanction.length();
        if (length.isInstant()) {
            return sanction.type().name();
        }
        if (length.isPermanent()) {
            return "permanent " + sanction.type().name();
        }
        return length.temporary().orElseThrow().toHours() + "h " + sanction.type().name();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        Actor actor = PaperActorResolver.resolve(sender).orElse(null);
        if (actor == null
                || !authorization.permits(actor, ModerationAction.ISSUE_POLICY_SANCTION)
                || !sender.hasPermission("enthusiastaff.punish.configured")) {
            return List.of();
        }
        if (arguments.length == 1) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("confirm", "resume")
                    .filter(candidate -> candidate.startsWith(prefix))
                    .toList();
        }
        if (arguments.length == 2
                && !arguments[0].equalsIgnoreCase("confirm")
                && !arguments[0].equalsIgnoreCase("resume")) {
            String prefix = arguments[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (ReasonPolicy policy : policies.all()) {
                if (policy.id().startsWith(prefix)
                        && PunishmentCommandFilter.includes(alias, policy)) {
                    matches.add(policy.id());
                }
            }
            matches.sort(Comparator.naturalOrder());
            return matches.size() > 50 ? matches.subList(0, 50) : matches;
        }
        if (arguments.length == 3 && !arguments[0].equalsIgnoreCase("confirm")
                && "--private".startsWith(arguments[2].toLowerCase(Locale.ROOT))) {
            return List.of("--private");
        }
        return List.of();
    }

}
