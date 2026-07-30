package net.enthusia.staff.paper.command;

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
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.paper.punishment.PunishmentRequestGuiController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class PunishmentRequestCommandHandler {
    private static final String REVIEW_PERMISSION = "enthusiastaff.punishment.requests.review";
    private static final List<String> SUBCOMMANDS = List.of("requests", "review", "approve", "deny");

    private final JavaPlugin plugin;
    private final Supplier<PunishmentRequestService> services;
    private final PunishmentRequestGuiController gui;
    private final ExecutorService workers;

    PunishmentRequestCommandHandler(
            JavaPlugin plugin,
            Supplier<PunishmentRequestService> services,
            PunishmentRequestGuiController gui,
            ExecutorService workers
    ) {
        if (plugin == null || services == null || gui == null || workers == null) {
            throw new IllegalArgumentException("punishment request command dependencies must be present");
        }
        this.plugin = plugin;
        this.services = services;
        this.gui = gui;
        this.workers = workers;
    }

    boolean handles(String commandName, String[] args) {
        return "punish".equalsIgnoreCase(commandName)
                && args.length > 0
                && SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT));
    }

    void execute(CommandSender sender, String[] args, Actor actor) {
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(REVIEW_PERMISSION)) {
            sender.sendMessage(Component.text(
                    "You do not have permission to review punishment requests.",
                    NamedTextColor.RED
            ));
            return;
        }
        switch (subcommand) {
            case "requests" -> queue(sender);
            case "review" -> review(sender, args);
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

    private void queue(CommandSender sender) {
        if (sender instanceof Player player) {
            gui.openQueue(player);
            return;
        }
        submit(sender, () -> {
            List<PunishmentApprovalRequest> pending = services.get().pending(45);
            onMain(() -> {
                sender.sendMessage(Component.text(
                        "Pending punishment requests: " + pending.size(),
                        NamedTextColor.GOLD
                ));
                pending.forEach(request -> sender.sendMessage(summary(request)));
            });
        });
    }

    private void review(CommandSender sender, String[] args) {
        UUID requestId = requestId(sender, args, 1);
        if (requestId == null) {
            return;
        }
        if (sender instanceof Player player) {
            gui.openReview(player, requestId);
            return;
        }
        submit(sender, () -> {
            PunishmentApprovalRequest request = services.get().pending(500).stream()
                    .filter(value -> value.requestId().equals(requestId))
                    .findFirst()
                    .orElse(null);
            onMain(() -> {
                if (request == null) {
                    sender.sendMessage(Component.text(
                            "The request is not pending or does not exist.",
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
            PunishmentRequestResult acquired = services.get().acquire(requestId, actor);
            PunishmentRequestResult result;
            if (acquired instanceof PunishmentRequestResult.Leased leased) {
                result = approve
                        ? services.get().approve(leased.lease(), actor)
                        : services.get().deny(leased.lease(), actor, denialNote);
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
                    "Approved punishment request " + approved.request().requestId()
                            + " as case " + approved.caseId().value() + '.',
                    NamedTextColor.GREEN
            ));
        } else if (result instanceof PunishmentRequestResult.Denied denied) {
            sender.sendMessage(Component.text(
                    "Denied punishment request " + denied.request().requestId() + '.',
                    NamedTextColor.YELLOW
            ));
        } else if (result instanceof PunishmentRequestResult.Rejected rejected) {
            sender.sendMessage(Component.text(
                    rejected.code() + ": " + rejected.message(),
                    NamedTextColor.RED
            ));
        }
    }

    private static Component summary(PunishmentApprovalRequest request) {
        return Component.text(request.requestId() + " ", NamedTextColor.AQUA)
                .append(Component.text(
                        request.proposal().requester().displayName() + " -> "
                                + request.proposal().targetId() + " | "
                                + request.proposal().reasonId() + " | "
                                + describe(request.proposal().sanctions()) + " | expires "
                                + request.expiresAt(),
                        NamedTextColor.GRAY
                ));
    }

    private static void sendDetails(CommandSender sender, PunishmentApprovalRequest request) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Punishment request " + request.requestId(), NamedTextColor.GOLD));
        lines.add(Component.text("Status: " + request.status(), NamedTextColor.GRAY));
        lines.add(Component.text(
                "Requester: " + request.proposal().requester().displayName()
                        + " (" + request.proposal().requester().rank() + ')',
                NamedTextColor.GRAY
        ));
        lines.add(Component.text("Target: " + request.proposal().targetId(), NamedTextColor.GRAY));
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
        lines.add(Component.text("Expires: " + request.expiresAt(), NamedTextColor.GRAY));
        lines.forEach(sender::sendMessage);
    }

    private static String describe(List<SanctionSpec> sanctions) {
        return sanctions.stream().map(specification -> {
            String type = specification.type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            return specification.length().isPermanent()
                    ? type + " permanent"
                    : type + ' ' + humanDuration(specification.length().duration());
        }).reduce((left, right) -> left + ", " + right).orElse("no sanction");
    }

    private static String humanDuration(java.time.Duration duration) {
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
