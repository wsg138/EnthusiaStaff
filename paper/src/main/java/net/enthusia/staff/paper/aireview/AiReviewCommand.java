package net.enthusia.staff.paper.aireview;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.enthusia.staff.paper.aireview.AiReviewModels.Correction;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AiReviewCommand implements CommandExecutor, TabCompleter {
    private final AiReviewSubsystem subsystem;
    private final AiReviewGuiController gui;
    private final Clock clock = Clock.systemUTC();

    AiReviewCommand(AiReviewSubsystem subsystem, AiReviewGuiController gui) {
        this.subsystem = subsystem;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments
    ) {
        if (!AiReviewPermissions.queue(sender)) {
            send(sender, "AI review permission denied.", NamedTextColor.RED);
            return true;
        }
        if (!subsystem.enabled()) {
            send(sender, subsystem.disabledReason(), NamedTextColor.GRAY);
            return true;
        }
        if (arguments.length == 0) {
            if (sender instanceof Player player) {
                gui.openQueue(player);
            } else {
                list(sender);
            }
            return true;
        }
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                list(sender);
                yield true;
            }
            case "refresh" -> {
                refresh(sender);
                yield true;
            }
            case "view" -> {
                view(sender, arguments);
                yield true;
            }
            case "allow", "block", "review", "label", "approve", "reject",
                    "adminapprove", "adminreject" -> {
                textWrite(sender, arguments);
                yield true;
            }
            default -> {
                usage(sender);
                yield true;
            }
        };
    }

    private void list(CommandSender sender) {
        AiReviewPollState.Snapshot snapshot = subsystem.snapshot();
        boolean fresh = snapshot.fresh(clock.instant(), subsystem.configuration().cacheStaleAfter());
        send(
                sender,
                "AI review queue: " + snapshot.items().size() + " pending, "
                        + snapshot.urgentCount() + " urgent"
                        + (fresh ? "." : " (cached/unavailable; refreshing)."),
                fresh ? NamedTextColor.GOLD : NamedTextColor.GRAY
        );
        snapshot.items().stream().limit(20).forEach(item -> send(
                sender,
                item.eventId() + " " + item.reviewPriority() + " "
                        + item.messageAction() + " " + item.semanticLabel(),
                item.reviewPriority() == ReviewPriority.URGENT
                        ? NamedTextColor.RED : NamedTextColor.GRAY
        ));
        if (!fresh) {
            subsystem.refreshQueue(false, null);
        }
    }

    private void refresh(CommandSender sender) {
        send(sender, "Refreshing the central AI review queue…", NamedTextColor.GRAY);
        subsystem.refreshQueue(true, () -> send(
                sender,
                "AI review queue refresh finished; use /aireview list.",
                subsystem.snapshot().authoritative() ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
    }

    private void view(CommandSender sender, String[] arguments) {
        if (arguments.length < 2) {
            usage(sender);
            return;
        }
        if (!AiReviewPermissions.detail(sender)) {
            send(sender, "AI review detail permission denied.", NamedTextColor.RED);
            return;
        }
        String eventId = arguments[1];
        if (sender instanceof Player player) {
            gui.openEvent(player, eventId, 0);
            return;
        }
        subsystem.loadEvent(
                eventId,
                details -> AiReviewPresentation.detailLines(details, subsystem.configuration())
                        .forEach(line -> send(sender, line, NamedTextColor.GRAY)),
                issue -> send(sender, "AI review unavailable: " + issue, NamedTextColor.YELLOW)
        );
    }

    private void textWrite(CommandSender sender, String[] arguments) {
        if (!(sender instanceof Player player)) {
            send(sender, "AI review writes require an in-game staff identity.", NamedTextColor.RED);
            return;
        }
        if (!AiReviewPermissions.correct(player)) {
            send(sender, "AI review correction permission denied.", NamedTextColor.RED);
            return;
        }
        String action = arguments[0].toLowerCase(Locale.ROOT);
        int requiredArguments = action.equals("label") ? 3
                : action.contains("approve") || action.contains("reject") ? 3 : 2;
        if (arguments.length < requiredArguments) {
            usage(sender);
            return;
        }
        boolean confirmed = arguments[arguments.length - 1].equals("CONFIRM");
        if (!confirmed) {
            send(
                    sender,
                    "Review only: no change was made. Append the exact word CONFIRM to commit this central review vote.",
                    NamedTextColor.YELLOW
            );
            return;
        }
        boolean admin = action.startsWith("admin");
        CorrectionAuthority authority;
        try {
            authority = AiReviewPermissions.authority(player, subsystem.configuration(), admin);
        } catch (SecurityException exception) {
            send(sender, "AI review ADMIN override is not authorized.", NamedTextColor.RED);
            return;
        }
        String eventId = arguments[1];
        subsystem.loadEvent(
                eventId,
                details -> performTextWrite(player, action, arguments, details, authority),
                issue -> send(sender, "No change was made: " + issue, NamedTextColor.YELLOW)
        );
    }

    private void performTextWrite(
            Player player,
            String action,
            String[] arguments,
            EventDetails details,
            CorrectionAuthority authority
    ) {
        if (!player.isOnline() || !AiReviewPermissions.correct(player)) {
            return;
        }
        if (authority == CorrectionAuthority.ADMIN) {
            try {
                AiReviewPermissions.authority(player, subsystem.configuration(), true);
            } catch (SecurityException exception) {
                send(player, "AI review ADMIN permission changed; no write was made.", NamedTextColor.RED);
                return;
            }
        }
        String reviewer = player.getUniqueId().toString();
        String note = "EnthusiaStaff text review: " + action;
        if (action.endsWith("approve")) {
            Correction pending = details.pendingCorrection(arguments[2]);
            if (pending == null) {
                stale(player);
                return;
            }
            subsystem.correct(
                    details.eventId(), reviewer, authority, pending.corrected(), note,
                    correction -> result(player, correction),
                    issue -> failed(player, issue)
            );
            return;
        }
        if (action.endsWith("reject")) {
            Correction pending = details.pendingCorrection(arguments[2]);
            if (pending == null) {
                stale(player);
                return;
            }
            subsystem.reject(
                    pending.proposalId(), reviewer, authority, note,
                    correction -> result(player, correction),
                    issue -> failed(player, issue)
            );
            return;
        }
        if (details.acceptedCorrection() != null) {
            stale(player);
            return;
        }
        CorrectionDecision decision = CorrectionDecision.from(details.decision());
        if (action.equals("allow")) {
            decision = decision.withAction(MessageAction.ALLOW);
        } else if (action.equals("block")) {
            decision = decision.withAction(MessageAction.BLOCK);
        } else if (action.equals("review")) {
            decision = decision.withReviewPriority(
                    decision.reviewPriority() == ReviewPriority.NONE
                            ? ReviewPriority.NORMAL : decision.reviewPriority()
            );
        } else if (action.equals("label")) {
            String semantic = arguments[2].toUpperCase(Locale.ROOT);
            if (!AiReviewGuiController.SEMANTIC_LABELS.contains(semantic)) {
                send(player, "Unknown Policy-v1 semantic label.", NamedTextColor.RED);
                return;
            }
            decision = decision.withSemanticLabel(semantic);
        } else {
            usage(player);
            return;
        }
        subsystem.correct(
                details.eventId(), reviewer, authority, decision, note,
                correction -> result(player, correction),
                issue -> failed(player, issue)
        );
    }

    private void result(Player player, Correction correction) {
        send(
                player,
                "Central correction " + correction.status()
                        + " · approvals=" + correction.approvals()
                        + " rejections=" + correction.rejections() + '.',
                NamedTextColor.GREEN
        );
        subsystem.refreshQueue(false, null);
    }

    private static void stale(CommandSender sender) {
        send(
                sender,
                "The central review state changed or the proposal is no longer pending; no write was made.",
                NamedTextColor.YELLOW
        );
    }

    private static void failed(CommandSender sender, String issue) {
        send(sender, "No correction was committed: " + issue, NamedTextColor.YELLOW);
    }

    private static void usage(CommandSender sender) {
        send(sender, "Usage: /aireview [list|refresh|view <event-id>]", NamedTextColor.GRAY);
        send(sender, "       /aireview <allow|block|review> <event-id> [CONFIRM]", NamedTextColor.GRAY);
        send(sender, "       /aireview label <event-id> <SEMANTIC_LABEL> [CONFIRM]", NamedTextColor.GRAY);
        send(sender, "       /aireview <approve|reject> <event-id> <proposal-id> [CONFIRM]", NamedTextColor.GRAY);
        send(sender, "       /aireview <adminapprove|adminreject> <event-id> <proposal-id> [CONFIRM]", NamedTextColor.GRAY);
    }

    private static void send(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage(StaffMessageStyle.style(Component.text(text, color)));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] arguments
    ) {
        if (!AiReviewPermissions.queue(sender)) {
            return List.of();
        }
        if (arguments.length == 1) {
            List<String> values = new ArrayList<>(List.of(
                    "list", "refresh", "view", "allow", "block", "review", "label", "approve", "reject"
            ));
            if (subsystem.configuration().adminOverrideEnabled()
                    && sender.hasPermission(AiReviewPermissions.ADMIN)) {
                values.add("adminapprove");
                values.add("adminreject");
            }
            return prefix(values, arguments[0]);
        }
        if (arguments.length == 3 && arguments[0].equalsIgnoreCase("label")) {
            return prefix(AiReviewGuiController.SEMANTIC_LABELS, arguments[2]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
