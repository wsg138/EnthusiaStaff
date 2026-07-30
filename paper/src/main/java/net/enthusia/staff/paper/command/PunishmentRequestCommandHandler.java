package net.enthusia.staff.paper.command;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentRequestCommandHandler {
    public static final String REVIEW_PERMISSION = "enthusiastaff.punishment.requests.review";
    private static final List<String> SUBCOMMANDS = List.of("requests", "review", "approve", "deny");

    private final JavaPlugin plugin;
    private final Supplier<PunishmentRequestService> services;
    private final AuthorizationPolicy authorization;
    private final PunishmentRequestGuiController gui;
    private final ExecutorService workers;

    public PunishmentRequestCommandHandler(
            JavaPlugin plugin,
            Supplier<PunishmentRequestService> services,
            AuthorizationPolicy authorization,
            PunishmentRequestGuiController gui,
            ExecutorService workers
    ) {
        if (plugin == null || services == null || authorization == null || gui == null || workers == null) {
            throw new IllegalArgumentException("punishment request command dependencies must be present");
        }
        this.plugin = plugin;
        this.services = services;
        this.authorization = authorization;
        this.gui = gui;
        this.workers = workers;
    }

    boolean handles(String commandName, String[] args) {
        return "punish".equalsIgnoreCase(commandName)
                && args.length > 0
                && SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT));
    }

    void execute(CommandSender sender, String[] args, Actor actor) {
        if (!authorizedReviewer(sender, actor)) {
            return;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "requests" -> queue(sender, actor);
            case "review" -> review(sender, args, actor);
            case "approve" -> decide(sender, args, actor, true);
            case "deny" -> decide(sender, args, actor, false);
            default -> throw new IllegalStateException("Unsupported punishment request subcommand");
        }
    }

    List<String> complete(String commandName, String[] args) {
        if (!"punish".equalsIgnoreCase(commandName)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private boolean authorizedReviewer(CommandSender sender, Actor actor) {
        if (!sender.hasPermission(REVIEW_PERMISSION)
                || actor == null
                || !authorization.permits(actor, ModerationAction.APPROVE_POLICY_SANCTION)
                || !actor.rank().canApprovePunishmentRequests()) {
            sender.sendMessage(Component.text(
                    "Only Mod, Admin, or Founder may review punishment requests.",
                    NamedTextColor.RED
            ));
            return false;
        }
        return true;
    }

    private void queue(CommandSender sender, Actor actor) {
        if (sender instanceof Player player) {
            gui.openQueue(player);
            return;
        }
        submit(sender, () -> {
            List<PunishmentApprovalRequest> pending = reviewable(services.get().pending(500), actor)
                    .stream()
                    .limit(45)
                    .toList();
            onMain(() -> {
                sender.sendMessage(Component.text(
                        "Reviewable punishment requests: " + pending.size(),
                        NamedTextColor.GOLD
                ));
                if (pending.isEmpty()) {
                    sender.sendMessage(Component.text("No punishment requests are currently available.", NamedTextColor.GRAY));
                } else {
                    pending.forEach(request -> sender.sendMessage(summary(request)));
                }
            });
        });
    }

    private void review(CommandSender sender, String[] args, Actor actor) {
        UUID requestId = requestId(sender, args, 1);
        if (requestId == null) {
            return;
        }
        if (sender instanceof Player player) {
            gui.openReview(player, requestId);
            return;
        }
        submit(sender, () -> {
            PunishmentApprovalRequest request = reviewable(services.get().pending(500), actor).stream()
                    .filter(value -> value.requestId().equals(requestId))
                    .findFirst()
                    .orElse(null);
            onMain(() -> {
                if (request == null) {
                    sender.sendMessage(Component.text(
                            "The request is not pending or you are not authorized to review it.",
                            NamedTextColor.RED
                    ));
                } else {
                    sendDetails(sender, request);
                }
            });
        });
    }

    private void decide(CommandSender sender, String[] args, Actor actor, boolean approve) {
        UUID requestId = requestId(sender, args, 1);
        if (requestId == null) {
            return;
        }
        String denialNote = approve ? null : denialNote(sender, args);
        if (!approve && denialNote == null) {
            return;
        }
        submit(sender, () -> {
            PunishmentRequestService service = services.get();
            PunishmentRequestResult acquired = service.acquire(requestId, actor);
            PunishmentRequestResult result;
            if (acquired instanceof PunishmentRequestResult.Leased leased) {
                result = approve
                        ? service.approve(leased.lease(), actor)
                        : service.deny(leased.lease(), actor, denialNote);
            } else {
                result = acquired;
            }
            onMain(() -> sendDecision(sender, result));
        });
    }

    private void submit(CommandSender sender, Runnable operation) {
        try {
            workers.submit(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Punishment request command failed", exception);
                    onMain(() -> sender.sendMessage(Component.text(
                            "Punishment request storage is unavailable; no decision was made.",
                            NamedTextColor.RED
                    )));
                }
            });
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().log(Level.WARNING, "Punishment request worker rejected command", exception);
            sender.sendMessage(Component.text(
                    "Punishment request storage is unavailable; try again shortly.",
                    NamedTextColor.RED
            ));
        }
    }

    private void onMain(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private static List<PunishmentApprovalRequest> reviewable(
            List<PunishmentApprovalRequest> requests,
            Actor actor
    ) {
        return requests.stream()
                .filter(request -> !request.proposal().requester().id().equals(actor.id()))
                .filter(request -> meetsRequiredApprovalRank(actor.rank(), request.proposal().requiredRank()))
                .toList();
    }

    private static boolean meetsRequiredApprovalRank(StaffRank approver, StaffRank required) {
        return switch (required) {
            case HELPER, MOD -> approver.canApprovePunishmentRequests();
            case ADMIN -> approver == StaffRank.ADMIN || approver == StaffRank.FOUNDER;
            case FOUNDER -> approver == StaffRank.FOUNDER;
            case DEVELOPER, SYSTEM -> false;
        };
    }

    private static UUID requestId(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(Component.text(
                    "Usage: /punish review|approve <request-id> or /punish deny <request-id> <reason>",
                    NamedTextColor.RED
            ));
            return null;
        }
        try {
            return UUID.fromString(args[index]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Invalid punishment request ID.", NamedTextColor.RED));
            return null;
        }
    }

    private static String denialNote(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /punish deny <request-id> <reason>",
                    NamedTextColor.RED
            ));
            return null;
        }
        String note = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
        if (note.isBlank()) {
            sender.sendMessage(Component.text("A denial reason is required.", NamedTextColor.RED));
            return null;
        }
        return note;
    }

    private static void sendDecision(CommandSender sender, PunishmentRequestResult result) {
        if (result instanceof PunishmentRequestResult.Approved approved) {
            sender.sendMessage(Component.text(
                    "Approved punishment request as case " + approved.caseId().value() + '.',
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentRequestResult.Denied) {
            sender.sendMessage(Component.text("Punishment request denied.", NamedTextColor.YELLOW));
        } else if (result instanceof PunishmentRequestResult.Rejected rejected) {
            sender.sendMessage(Component.text(rejected.code() + ": " + rejected.message(), NamedTextColor.RED));
        }
    }

    private static Component summary(PunishmentApprovalRequest request) {
        return Component.text(request.requestId() + " ", NamedTextColor.AQUA)
                .append(Component.text(
                        request.proposal().requester().displayName() + " -> target | "
                                + request.proposal().reasonId() + " | "
                                + describe(request.proposal().sanctions()) + " | expires "
                                + request.expiresAt(),
                        NamedTextColor.GRAY
                ));
    }

    private static void sendDetails(CommandSender sender, PunishmentApprovalRequest request) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Punishment request", NamedTextColor.GOLD));
        lines.add(Component.text("Status: " + request.status(), NamedTextColor.GRAY));
        lines.add(Component.text(
                "Requester: " + request.proposal().requester().displayName()
                        + " (" + request.proposal().requester().rank() + ')',
                NamedTextColor.GRAY
        ));
        lines.add(Component.text("Reason: " + request.proposal().reasonId(), NamedTextColor.GRAY));
        lines.add(Component.text(
                "Sanctions: " + describe(request.proposal().sanctions()),
                NamedTextColor.GOLD
        ));
        lines.add(Component.text(
                "Required rank: " + request.proposal().requiredRank()
                        + " | revision " + request.revision(),
                NamedTextColor.GRAY
        ));
        lines.add(Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY));
        lines.add(Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY));
        lines.forEach(sender::sendMessage);
    }

    private static String describe(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(specification -> {
            String type = specification.type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (specification.length().isPermanent()) {
                return type + " permanent";
            }
            if (specification.length().isInstant()) {
                return type;
            }
            return type + ' ' + humanDuration(specification.length().temporary().orElseThrow());
        }).reduce((left, right) -> left + ", " + right).orElse("no sanction");
    }

    private static String humanDuration(Duration duration) {
        if (duration.toDays() > 0 && duration.minusDays(duration.toDays()).isZero()) {
            return duration.toDays() + "d";
        }
        if (duration.toHours() > 0 && duration.minusHours(duration.toHours()).isZero()) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() > 0 && duration.minusMinutes(duration.toMinutes()).isZero()) {
            return duration.toMinutes() + "m";
        }
        return duration.toSeconds() + "s";
    }
}
