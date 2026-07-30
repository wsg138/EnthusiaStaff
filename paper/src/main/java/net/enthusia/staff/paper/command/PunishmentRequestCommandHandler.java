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
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
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
    private static final int NO_ARGUMENTS = 0;
    private static final int SINGLE_ARGUMENT_COUNT = 1;
    private static final int REQUEST_ID_ARGUMENT_INDEX = 1;
    private static final int DENIAL_NOTE_START_INDEX = 2;
    private static final int DENIAL_MINIMUM_ARGUMENT_COUNT = 3;
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
                && args.length > NO_ARGUMENTS
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
        if (!"punish".equalsIgnoreCase(commandName) || args.length != SINGLE_ARGUMENT_COUNT) {
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
            List<RequestView> pending = services.get().reviewable(actor, CONSOLE_QUEUE_LIMIT).stream()
                    .map(this::view)
                    .toList();
            onMain(() -> sendQueue(sender, pending));
        });
    }

    private void review(CommandSender sender, String[] args, Actor actor) {
        UUID requestId = requestId(sender, args, REQUEST_ID_ARGUMENT_INDEX);
        if (requestId == null) {
            return;
        }
        if (sender instanceof Player player) {
            gui.openReview(player, requestId);
            return;
        }
        submit(sender, () -> sendConsoleReview(sender, actor, requestId));
    }

    private void sendConsoleReview(CommandSender sender, Actor actor, UUID requestId) {
        PunishmentRequestService service = services.get();
        PunishmentApprovalRequest request = service.find(requestId).orElse(null);
        RequestView view = request != null && service.mayReview(actor, request) ? view(request) : null;
        onMain(() -> {
            if (view == null) {
                sender.sendMessage(Component.text(
                        "The request does not exist or you are not authorized to review it.",
                        NamedTextColor.RED
                ));
            } else {
                sendDetails(sender, view);
            }
        });
    }

    private void decide(CommandSender sender, String[] args, Actor actor, boolean approve) {
        UUID requestId = requestId(sender, args, REQUEST_ID_ARGUMENT_INDEX);
        if (requestId == null) {
            return;
        }
        String denialNote = approve ? null : denialNote(sender, args);
        if (!approve && denialNote == null) {
            return;
        }
        submit(sender, () -> decideStored(sender, actor, requestId, approve, denialNote));
    }

    private void decideStored(
            CommandSender sender,
            Actor actor,
            UUID requestId,
            boolean approve,
            String denialNote
    ) {
        PunishmentRequestService service = services.get();
        PunishmentRequestResult acquired = service.acquire(requestId, actor);
        PunishmentRequestResult result = acquired instanceof PunishmentRequestResult.Leased leased
                ? decideLeased(service, leased, actor, approve, denialNote)
                : acquired;
        onMain(() -> sendDecision(sender, result));
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
            workers.submit(() -> runOperation(sender, operation));
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().log(Level.WARNING, "Punishment request worker rejected command", exception);
            sender.sendMessage(Component.text(
                    "Punishment request storage is unavailable; try again shortly.",
                    NamedTextColor.RED
            ));
        }
    }

    private void runOperation(CommandSender sender, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Punishment request command failed", exception);
            onMain(() -> sender.sendMessage(Component.text(
                    "Punishment request storage is unavailable; no decision was made.",
                    NamedTextColor.RED
            )));
        }
    }

    private void onMain(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private static void sendQueue(CommandSender sender, List<RequestView> pending) {
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

    private static Component summary(RequestView view) {
        PunishmentApprovalRequest request = view.request();
        return Component.text(request.requestId() + " ", NamedTextColor.AQUA)
                .append(Component.text(
                        view.targetName() + " | "
                                + request.proposal().requester().displayName() + " | "
                                + request.proposal().reasonId() + " | "
                                + PunishmentRequestPresentation.sanctions(request.proposal().sanctions()) + " | expires "
                                + request.expiresAt(),
                        NamedTextColor.GRAY
                ));
    }

    private static void sendDetails(CommandSender sender, RequestView view) {
        PunishmentApprovalRequest request = view.request();
        List<Component> lines = requestDetailLines(view);
        if (request.status() != PunishmentRequestStatus.PENDING) {
            lines.add(Component.text(
                    "Resolution: " + PunishmentRequestPresentation.resolution(request),
                    PunishmentRequestPresentation.statusColor(request.status())
            ));
        }
        lines.forEach(sender::sendMessage);
    }

    private static List<Component> requestDetailLines(RequestView view) {
        PunishmentApprovalRequest request = view.request();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Punishment request " + request.requestId(), NamedTextColor.GOLD));
        lines.add(Component.text(
                "Status: " + PunishmentRequestPresentation.status(request.status()),
                PunishmentRequestPresentation.statusColor(request.status())
        ));
        lines.add(Component.text("Target: " + view.targetName(), NamedTextColor.WHITE));
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
        return lines;
    }

    private RequestView view(PunishmentApprovalRequest request) {
        return new RequestView(request, gui.targetName(request));
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
        if (args.length < DENIAL_MINIMUM_ARGUMENT_COUNT) {
            sender.sendMessage(Component.text(
                    "Usage: /punish deny <request-id> <reason>",
                    NamedTextColor.RED
            ));
            return null;
        }
        String note = String.join(
                " ",
                Arrays.copyOfRange(args, DENIAL_NOTE_START_INDEX, args.length)
        ).trim();
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

    private record RequestView(PunishmentApprovalRequest request, String targetName) {
        private RequestView {
            if (request == null || targetName == null || targetName.isBlank()) {
                throw new IllegalArgumentException("punishment request command view fields must be present");
            }
        }
    }
}
