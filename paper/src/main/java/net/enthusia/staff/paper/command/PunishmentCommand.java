package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentEvaluation;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.application.PunishmentService;
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
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentCommand implements CommandExecutor, TabCompleter {
    private static final Duration DRAFT_LIFETIME = Duration.ofMinutes(2);
    private static final int MAX_DRAFTS = 2_048;
    private static final Map<String, Set<SanctionType>> FILTERS = Map.of(
            "ban", Set.of(SanctionType.BAN, SanctionType.NETWORK_BAN),
            "ipban", Set.of(SanctionType.NETWORK_IDENTITY_BAN),
            "mute", Set.of(SanctionType.MUTE),
            "warn", Set.of(SanctionType.WARNING),
            "kick", Set.of(SanctionType.KICK)
    );

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentService> punishmentService;
    private final Supplier<PlayerDirectory> playerDirectory;
    private final AuthorizationPolicy authorization;
    private final ReasonPolicyRepository policies;
    private final ExecutorService workers;
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();

    public PunishmentCommand(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentService> punishmentService,
            Supplier<PlayerDirectory> playerDirectory,
            AuthorizationPolicy authorization,
            ReasonPolicyRepository policies,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.mode = mode;
        this.punishmentService = punishmentService;
        this.playerDirectory = playerDirectory;
        this.authorization = authorization;
        this.policies = policies;
        this.workers = workers;
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
        if (arguments.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /" + label + " <player|uuid> <reason-id> [--private] [internal explanation]"
            ));
            sender.sendMessage(Component.text("Confirm with /" + label + " confirm <token> after reviewing the result."));
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
            PunishmentService service = punishmentService.get();
            if (directory == null || service == null) {
                send(sender, "Moderation storage is not ready; no action was taken.");
                return;
            }
            PlayerIdentity target = directory.find(arguments[0]).orElse(null);
            if (target == null) {
                send(sender, "Player is not present in the authoritative directory. UUIDs and historical names are accepted.");
                return;
            }
            String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            CreatePunishmentRequest request = request(token, target.playerId(), actor, policy.id(), explanation, visibility);
            PunishmentEvaluation evaluation = service.evaluate(request, mode.get());
            if (evaluation instanceof PunishmentEvaluation.Rejected rejected) {
                send(sender, rejected.code() + ": " + rejected.message());
                return;
            }
            PunishmentAssessment assessment = ((PunishmentEvaluation.Allowed) evaluation).assessment();
            if (!matchesFilter(label, assessment.sanctions())) {
                send(sender, "The recommended step does not contain the sanction type selected by /" + label + ".");
                return;
            }
            pruneDrafts();
            if (drafts.size() >= MAX_DRAFTS) {
                send(sender, "The confirmation queue is full; retry after existing drafts expire.");
                return;
            }
            drafts.put(token, new Draft(
                    token,
                    actor.id(),
                    target.playerId(),
                    policy.id(),
                    explanation,
                    visibility,
                    label.toLowerCase(Locale.ROOT),
                    PunishmentExpectation.from(assessment),
                    clock.instant().plus(DRAFT_LIFETIME)
            ));
            send(sender, "Review: " + displayName(target) + " | " + policy.id() + " | step "
                    + assessment.escalation().effectiveOrdinal() + " | " + describe(assessment.sanctions()));
            send(sender, "No punishment has been created. Confirm within 2 minutes: /" + label + " confirm " + token);
        });
    }

    private void confirm(CommandSender sender, String label, String token, Actor actor) {
        Draft draft = drafts.remove(token.toUpperCase(Locale.ROOT));
        if (draft == null || draft.expiresAt().isBefore(clock.instant())) {
            sender.sendMessage(Component.text("That confirmation token is missing or expired."));
            return;
        }
        if (!draft.actorId().equals(actor.id()) || !draft.command().equals(label.toLowerCase(Locale.ROOT))) {
            sender.sendMessage(Component.text("That confirmation token belongs to a different actor or command."));
            return;
        }
        submit(sender, () -> {
            PunishmentService service = punishmentService.get();
            if (service == null) {
                send(sender, "Moderation storage is not ready; no action was taken.");
                return;
            }
            CreatePunishmentRequest request = request(
                    draft.token(), draft.targetId(), actor, draft.reasonId(), draft.explanation(), draft.visibility()
            );
            PunishmentResult result = service.createConfirmed(request, mode.get(), draft.expectation());
            if (result instanceof PunishmentResult.Accepted accepted) {
                send(sender, "Punishment committed as case " + accepted.caseId()
                        + (accepted.replayed() ? " (idempotent replay)" : "") + ".");
            } else {
                PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
                send(sender, rejected.code() + ": " + rejected.message());
            }
        });
    }

    private void submit(CommandSender sender, Runnable work) {
        try {
            workers.execute(work);
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The moderation work queue is full; no action was taken."));
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }

    private void pruneDrafts() {
        Instant now = clock.instant();
        drafts.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private static CreatePunishmentRequest request(
            String token,
            UUID target,
            Actor actor,
            String reason,
            String explanation,
            CaseVisibility visibility
    ) {
        return new CreatePunishmentRequest(
                new IdempotencyKey("command:" + actor.id() + ':' + token),
                target,
                actor,
                reason,
                explanation,
                visibility,
                List.of()
        );
    }

    private static boolean matchesFilter(String label, List<SanctionSpec> sanctions) {
        Set<SanctionType> required = FILTERS.get(label.toLowerCase(Locale.ROOT));
        return required == null || sanctions.stream().anyMatch(spec -> required.contains(spec.type()));
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
        if (arguments.length == 1 && "confirm".startsWith(arguments[0].toLowerCase(Locale.ROOT))) {
            return List.of("confirm");
        }
        if (arguments.length == 2 && !arguments[0].equalsIgnoreCase("confirm")) {
            String prefix = arguments[1].toLowerCase(Locale.ROOT);
            Set<SanctionType> filter = FILTERS.get(alias.toLowerCase(Locale.ROOT));
            List<String> matches = new ArrayList<>();
            for (ReasonPolicy policy : policies.all()) {
                if (policy.id().startsWith(prefix)
                        && (filter == null || policy.steps().stream().flatMap(step -> step.sanctions().stream())
                        .anyMatch(spec -> filter.contains(spec.type())))) {
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

    private record Draft(
            String token,
            UUID actorId,
            UUID targetId,
            String reasonId,
            String explanation,
            CaseVisibility visibility,
            String command,
            PunishmentExpectation expectation,
            Instant expiresAt
    ) {
    }
}
