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
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import net.enthusia.staff.paper.punishment.PunishmentRequestPresentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentRequestCommandHandler {
    public static final String REVIEW_PERMISSION = "enthusiastaff.punishment.requests.review";
    private static final int CONSOLE_QUEUE_LIMIT = 45;
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

    public void bindPlayerDirectory(Supplier<PlayerDirectory> players) {
        gui.bindPlayerDirectory(players);
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
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "requests" -> queue(sender, actor);
            case "review" -> review(sender, args, actor);
            case "approve" -> decide(sender, args, actor, true);
            case "deny" -> decide(sender, args, actor, false);
            default -> throw new IllegalStateException("Unsupported punishment request subcommand");
        }
    }

    List<String> complete(String commandName, String[] args) {
        if (!"punish".equalsIgnoreCase(commandName) || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(value -> value.startsWith(prefix)).toList();
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
            List<PunishmentApprovalRequest> pending = services.get().reviewable(actor, CONSOLE_QUEUE_LIMIT);
            onMain(() -> sendQueue(sender, pending));
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
            PunishmentRequestService service = services.get();
            PunishmentApprovalRequest request = service.find(requestId).orElse(null);
            boolean visible = request != null && service.mayReview(actor, request);
            onMain(() -> {
                if (!visible) {
                    sender.sendMessage(Component.text(
                            "The request does not exist or you are not authorized to review it.",
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
            PunishmentRequestResult result = acquired instanceof PunishmentRequestResult.Leased leased
                    ? decideLeased(service, leased, actor, approve, denialNote)
                    : acquired;
            onMain(() -> sendDecision(sender, result));
        });
    }

    private static PunishmentRequestResult decideLeased(
            PunishmentRequestService service,
            PunishmentRequestResult.Leased leased,
            Actor actor,
            boolean approve,
            String denialNote
    ) {
        return approve
                ? service.approve(leased.lease(), actor)
                : service.deny(leased.lease(), actor, denialNote);
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

    private void sendQueue(CommandSender sender, List<PunishmentApprovalRequest> pending) {
        sender.sendMessage(Component.text(
                "Reviewable punishment requests: " + pending.size(),
                NamedTextColor.GOLD
        ));
        if (pending.isEmpty()) {
            sender.sendMessage(Component.text("No punishment requests are currently available.", NamedTextColor.GRAY));
            return;
        }
        pending.forEach(request -> sender.sendMessage(summary(request)));
    }

    private Component summary(PunishmentApprovalRequest request) {
        return Component.text(request.requestId() + " ", NamedTextColor.AQUA)
                .append(Component.text(
                        gui.targetName(request) + " | "
                                + request.proposal().requester().displayName() + " | "
                                + request.proposal().reasonId() + " | "
                                + PunishmentRequestPresentation.sanctions(request.proposal().sanctions()) + " | expires "
                                + request.expiresAt(),
                        NamedTextColor.GRAY
                ));
    }

    private void sendDetails(CommandSender sender, PunishmentApprovalRequest request) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Punishment request " + request.requestId(), NamedTextColor.GOLD));
        lines.add(Component.text(
                "Status: " + PunishmentRequestPresentation.status(request.status()),
                PunishmentRequestPresentation.statusColor(request.status())
        ));
        lines.add(Component.text("Target: " + gui.targetName(request), NamedTextColor.WHITE));
        lines.add(Component.text(
                "Requester: " + request.proposal().requester().displayName()
                        + " (" + request.proposal().requester().rank() + ')',
                NamedTextColor.GRAY
        ));
        lines.add(Component.text("Reason: " + request.proposal().reasonId(), NamedTextColor.GRAY));
        lines.add(Component.text(
                "Sanctions: " + PunishmentRequestPresentation.sanctions(request.proposal().sanctions()),
                NamedTextColor.GOLD
        ));
        lines.add(Component.text("Visibility: " + request.proposal().visibility(), NamedTextColor.GRAY));
        lines.add(Component.text(
                "Required rank: " + request.proposal().requiredRank() + " | revision " + request.revision(),
                NamedTextColor.GRAY
        ));
        lines.add(Component.text("Created: " + request.createdAt(), NamedTextColor.GRAY));
        lines.add(Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY));
        if (request.status() != net.enthusia.staff.domain.application.PunishmentRequestStatus.PENDING) {
            lines.add(Component.text(
                    "Resolution: " + PunishmentRequestPresentation.resolution(request),
                    PunishmentRequestPresentation.statusColor(request.status())
            ));
        }
        lines.forEach(sender::sendMessage);
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
        String note = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (note.isBlank()) {
            sender.sendMessage(Component.text("A denial reason is required.", NamedTextColor.RED));
            return null;
        }
        return note;
    }

    private static void sendDecision(CommandSender sender, PunishmentRequestResult result) {
        if (result instanceof PunishmentRequestResult.Approved approved) {
            sender.sendMessage(Component.text(
                    "Approved punishment request as case " + approved.caseId().value()
                            + (approved.replayed() ? " (idempotent replay)." : "."),
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentRequestResult.Denied denied) {
            sender.sendMessage(Component.text(
                    denied.replayed()
                            ? "Punishment request denial replayed safely."
                            : "Punishment request denied.",
                    NamedTextColor.YELLOW
            ));
        } else if (result instanceof PunishmentRequestResult.Rejected rejected) {
            sender.sendMessage(Component.text(rejected.code() + ": " + rejected.message(), NamedTextColor.RED));
        }
    }
}
