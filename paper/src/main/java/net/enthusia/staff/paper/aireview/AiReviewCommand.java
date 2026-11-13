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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AiReviewCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final AiReviewSubsystem subsystem;
    private final AiReviewGuiController gui;
    private final Clock clock = Clock.systemUTC();

    AiReviewCommand(JavaPlugin plugin, AiReviewSubsystem subsystem, AiReviewGuiController gui) {
        this.plugin = plugin;
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
        if (sender instanceof Player player && !subsystem.activeDuty(player)) {
            send(sender, "AI review requires active staff mode.", NamedTextColor.RED);
            return true;
        }
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
            case "history" -> {
                history(sender, arguments);
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

    private void history(CommandSender sender, String[] arguments) {
        if (arguments.length > 2) {
            usage(sender);
            return;
        }
        String cursor = arguments.length == 2 ? arguments[1] : null;
        if (cursor != null && (cursor.isBlank() || cursor.length() > 64)) {
            send(sender, "Invalid history cursor.", NamedTextColor.RED);
            return;
        }
        send(sender, "Loading the central AI decision history…", NamedTextColor.GRAY);
        subsystem.loadDecisions(
                10, cursor,
                page -> showHistory(sender, page),
                issue -> send(sender, "AI history unavailable: " + issue, NamedTextColor.YELLOW)
        );
    }

    private void showHistory(
            CommandSender sender,
            AiReviewModels.DecisionHistoryPage page
    ) {
        if (!AiReviewPermissions.queue(sender)
                || sender instanceof Player player && !subsystem.activeDuty(player)) {
            return;
        }
        sendHistoryLine(sender, "AI decisions · " + page.items().size()
                + " finalized records (not just flags)", NamedTextColor.GOLD);
        page.items().forEach(item -> sendHistoryLine(
                sender,
                AiReviewPresentation.bounded(item.eventId(), 64) + " "
                        + item.messageAction() + " "
                        + AiReviewPresentation.bounded(item.semanticLabel(), 35)
                        + (item.degraded() ? " [fail-open]" : "")
                        + (item.corrected() ? " [corrected]" : ""),
                item.messageAction() == MessageAction.BLOCK
                        ? NamedTextColor.YELLOW : NamedTextColor.GRAY
        ));
        if (page.nextCursor() != null) {
            sendHistoryLine(sender, "Next: /aireview history " + page.nextCursor(), NamedTextColor.GRAY);
        }
        sendHistoryLine(sender, "Inspect: /aireview view <event-id> (authorized in-game only).",
                NamedTextColor.GRAY);
    }

    private void sendHistoryLine(CommandSender sender, String line, NamedTextColor color) {
        if (sender instanceof Player player) {
            // Check access on the same player scheduler task as delivery.
            onPlayer(player, () -> {
                if (player.isOnline() && AiReviewPermissions.queue(player)
                        && subsystem.activeDuty(player)) {
                    player.sendMessage(StaffMessageStyle.style(Component.text(line, color)));
                }
            });
            return;
        }
        // Console still receives only minimized summaries on the global scheduler.
        send(sender, line, color);
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
        if (!(sender instanceof Player player)) {
            send(
                    sender,
                    "AI review message/context detail is available only to an authorized in-game reviewer.",
                    NamedTextColor.RED
            );
            return;
        }
        gui.openEvent(player, arguments[1], 0);
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
        boolean adminRequested = action.startsWith("admin");
        CorrectionAuthority authority;
        try {
            authority = AiReviewPermissions.authority(player, subsystem.configuration(), adminRequested);
        } catch (SecurityException exception) {
            send(sender, "AI review ADMIN override is not authorized.", NamedTextColor.RED);
            return;
        }
        String eventId = arguments[1];
        subsystem.loadEvent(
                eventId,
                details -> onPlayer(player, () -> performTextWrite(
                        player, action, arguments, details, authority, confirmed
                )),
                issue -> send(sender, "No change was made: " + issue, NamedTextColor.YELLOW)
        );
    }

    private void performTextWrite(
            Player player,
            String action,
            String[] arguments,
            EventDetails details,
            CorrectionAuthority authority,
            boolean confirmed
    ) {
        if (!subsystem.activeDuty(player) || !AiReviewPermissions.correct(player)) {
            send(player, "Active staff mode is required; no write was made.", NamedTextColor.RED);
            return;
        }
        if (authority == CorrectionAuthority.ADMIN
                && !AiReviewPermissions.admin(player, subsystem.configuration())) {
            send(player, "AI review ADMIN authority changed; no write was made.", NamedTextColor.RED);
            return;
        }
        TextWritePlan plan = textWritePlan(player, action, arguments, details);
        if (plan == null) {
            return;
        }
        if (!confirmed) {
            previewTextWrite(player, plan);
            return;
        }
        executeTextWrite(player, action, details, authority, plan);
    }

    private TextWritePlan textWritePlan(
            Player player,
            String action,
            String[] arguments,
            EventDetails details
    ) {
        if (action.endsWith("approve") || action.endsWith("reject")) {
            return proposalPlan(player, action, arguments[2], details);
        }
        if (details.acceptedCorrection() != null) {
            stale(player);
            return null;
        }
        CorrectionDecision decision = convenienceDecision(player, action, arguments, details);
        return decision == null ? null : new TextWritePlan(
                TextWriteKind.CORRECT,
                decision,
                null,
                "Propose " + action + " correction"
        );
    }

    private TextWritePlan proposalPlan(
            Player player,
            String action,
            String proposalId,
            EventDetails details
    ) {
        Correction pending = details.pendingCorrection(proposalId);
        if (pending == null) {
            stale(player);
            return null;
        }
        boolean reject = action.endsWith("reject");
        return new TextWritePlan(
                reject ? TextWriteKind.REJECT : TextWriteKind.CORRECT,
                pending.corrected(),
                pending.proposalId(),
                (reject ? "Reject" : "Approve") + " pending correction " + pending.proposalId()
        );
    }

    private CorrectionDecision convenienceDecision(
            Player player,
            String action,
            String[] arguments,
            EventDetails details
    ) {
        CorrectionDecision current = CorrectionDecision.from(details.decision());
        return switch (action) {
            case "allow" -> current.withAction(MessageAction.ALLOW);
            case "block" -> current.withAction(MessageAction.BLOCK);
            case "review" -> current.withReviewPriority(
                    current.reviewPriority() == ReviewPriority.NONE
                            ? ReviewPriority.NORMAL : current.reviewPriority()
            );
            case "label" -> labelDecision(player, current, arguments[2]);
            default -> {
                usage(player);
                yield null;
            }
        };
    }

    private CorrectionDecision labelDecision(
            Player player,
            CorrectionDecision current,
            String requestedLabel
    ) {
        String semantic = requestedLabel.toUpperCase(Locale.ROOT);
        if (!AiReviewGuiController.SEMANTIC_LABELS.contains(semantic)) {
            send(player, "Unknown Policy-v1 semantic label.", NamedTextColor.RED);
            return null;
        }
        return current.withSemanticLabel(semantic);
    }

    private void previewTextWrite(Player player, TextWritePlan plan) {
        onPlayer(player, () -> {
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Review only: " + plan.description() + ". No change was made.",
                    NamedTextColor.YELLOW
            )));
            AiReviewPresentation.correctionDecisionLines(plan.decision(), subsystem.configuration())
                    .forEach(line -> player.sendMessage(StaffMessageStyle.style(
                            Component.text(line, NamedTextColor.GRAY)
                    )));
            player.sendMessage(StaffMessageStyle.style(Component.text(
                    "Append the exact word CONFIRM to the command to commit this central review vote.",
                    NamedTextColor.YELLOW
            )));
        });
    }

    private void executeTextWrite(
            Player player,
            String action,
            EventDetails details,
            CorrectionAuthority authority,
            TextWritePlan plan
    ) {
        if (!subsystem.activeDuty(player) || !AiReviewPermissions.correct(player)) {
            send(player, "Active staff mode is required; no write was made.", NamedTextColor.RED);
            return;
        }
        String reviewer = player.getUniqueId().toString();
        String note = "EnthusiaStaff text review: " + action;
        if (plan.kind() == TextWriteKind.REJECT) {
            subsystem.reject(
                    plan.proposalId(), reviewer, authority, note,
                    correction -> result(player, correction),
                    issue -> writeFailure(player, details.eventId(), issue)
            );
            return;
        }
        subsystem.correct(
                details.eventId(), reviewer, authority, plan.decision(), note,
                correction -> result(player, correction),
                issue -> writeFailure(player, details.eventId(), issue)
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

    private void stale(CommandSender sender) {
        send(
                sender,
                "The central review state changed or the proposal is no longer pending; no write was made.",
                NamedTextColor.YELLOW
        );
    }

    private void writeFailure(Player player, String eventId, String issue) {
        send(player, "No correction was committed: " + issue, NamedTextColor.YELLOW);
        if (!"central review conflict".equals(issue)) {
            return;
        }
        subsystem.loadEvent(
                eventId,
                details -> onPlayer(player, () -> {
                    if (!subsystem.activeDuty(player) || !AiReviewPermissions.detail(player)) {
                        return;
                    }
                    send(player, "Central state refreshed after the conflict:", NamedTextColor.GRAY);
                    AiReviewPresentation.detailLines(details, subsystem.configuration()).stream()
                            .limit(12)
                            .forEach(line -> send(player, line, NamedTextColor.GRAY));
                }),
                refreshIssue -> send(
                        player,
                        "Central state refresh failed: " + refreshIssue,
                        NamedTextColor.YELLOW
                )
        );
    }

    private void usage(CommandSender sender) {
        send(sender, "Usage: /aireview [list|refresh|view <event-id>|history [cursor]]", NamedTextColor.GRAY);
        send(sender, "       /aireview <allow|block|review> <event-id> [CONFIRM]", NamedTextColor.GRAY);
        send(sender, "       /aireview label <event-id> <SEMANTIC_LABEL> [CONFIRM]", NamedTextColor.GRAY);
        send(sender, "       /aireview <approve|reject> <event-id> <proposal-id> [CONFIRM]", NamedTextColor.GRAY);
        send(sender, "       /aireview <adminapprove|adminreject> <event-id> <proposal-id> [CONFIRM]", NamedTextColor.GRAY);
    }

    private void send(CommandSender sender, String text, NamedTextColor color) {
        Runnable delivery = () -> sender.sendMessage(
                StaffMessageStyle.style(Component.text(text, color))
        );
        if (sender instanceof Player player) {
            onPlayer(player, delivery);
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, delivery);
    }

    private void onPlayer(Player player, Runnable operation) {
        player.getScheduler().execute(plugin, operation, null, 1L);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] arguments
    ) {
        if ((sender instanceof Player player && !subsystem.activeDuty(player))
                || !AiReviewPermissions.queue(sender)
                || !subsystem.enabled()) {
            return List.of();
        }
        if (arguments.length == 1) {
            List<String> values = new ArrayList<>(List.of(
                    "list", "refresh", "view", "history", "allow", "block", "review", "label", "approve", "reject"
            ));
            if (AiReviewPermissions.admin(sender, subsystem.configuration())) {
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

    private enum TextWriteKind {
        CORRECT,
        REJECT
    }

    private record TextWritePlan(
            TextWriteKind kind,
            CorrectionDecision decision,
            String proposalId,
            String description
    ) {
    }

    private static List<String> prefix(List<String> values, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
