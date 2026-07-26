package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportCommand implements CommandExecutor, TabCompleter {
    private static final Set<SanctionType> REPORT_RESTRICTIONS = Set.of(SanctionType.REPORT_RESTRICTION);

    private final JavaPlugin plugin;
    private final Clock clock;
    private final String serverId;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<ReportStore> reports;
    private final Supplier<SanctionLookup> sanctions;
    private final ReasonPolicyRepository policies;
    private final ChatContextBuffer chat;
    private final ClientEvidenceCollector clientEvidence;
    private final ExecutorService workers;

    public ReportCommand(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<ReportStore> reports,
            Supplier<SanctionLookup> sanctions,
            ReasonPolicyRepository policies,
            ChatContextBuffer chat,
            ClientEvidenceCollector clientEvidence,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.serverId = serverId;
        this.mode = mode;
        this.players = players;
        this.reports = reports;
        this.sanctions = sanctions;
        this.policies = policies;
        this.chat = chat;
        this.clientEvidence = clientEvidence;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage(Component.text("Only a player can submit a player report."));
            return true;
        }
        if (arguments.length < 3) {
            sender.sendMessage(Component.text("Usage: /report <player|uuid> <reason-id> <description>"));
            return true;
        }
        ReasonPolicy policy = policies.find(arguments[1]).orElse(null);
        if (policy == null || !policy.reportable()) {
            sender.sendMessage(Component.text("That reason is not available for player reports."));
            return true;
        }
        if (mode.get() != OperationalMode.ACTIVE) {
            sender.sendMessage(Component.text("Reports are temporarily unavailable while moderation is " + mode.get() + '.'));
            return true;
        }
        String description = String.join(" ", Arrays.copyOfRange(arguments, 2, arguments.length)).trim();
        if (description.length() > 4_000) {
            sender.sendMessage(Component.text("The report description exceeds 4000 characters."));
            return true;
        }
        String reporterCoordinates = coordinates(reporter.getLocation());
        Player onlineTarget = plugin.getServer().getPlayerExact(arguments[0]);
        String targetCoordinates = onlineTarget == null ? null : coordinates(onlineTarget.getLocation());
        Optional<ClientEvidenceSnapshot> targetClientEvidence = onlineTarget == null
                ? Optional.empty()
                : Optional.of(clientEvidence.capture(onlineTarget));
        Instant now = clock.instant();
        String world = reporter.getWorld().getKey().asString();
        List<CreateReportRequest.ChatContextMessage> context = chat.snapshot(now);
        UUID reporterId = reporter.getUniqueId();
        submit(sender, () -> submitReport(
                sender,
                reporterId,
                arguments[0],
                policy.id(),
                description,
                world,
                reporterCoordinates,
                targetCoordinates,
                now,
                context,
                targetClientEvidence
        ));
        return true;
    }

    private void submitReport(
            CommandSender sender,
            UUID reporterId,
            String targetName,
            String reasonId,
            String description,
            String world,
            String reporterCoordinates,
            String targetCoordinates,
            Instant now,
            List<CreateReportRequest.ChatContextMessage> context,
            Optional<ClientEvidenceSnapshot> targetClientEvidence
    ) {
        PlayerDirectory directory = players.get();
        ReportStore store = reports.get();
        SanctionLookup lookup = sanctions.get();
        if (directory == null || store == null || lookup == null) {
            send(sender, "Report storage is not ready; no report was created.");
            return;
        }
        if (!lookup.activeFor(reporterId, REPORT_RESTRICTIONS, now).isEmpty()) {
            send(sender, "You cannot submit reports while a report restriction is active.");
            return;
        }
        PlayerIdentity target = directory.find(targetName).orElse(null);
        if (target == null) {
            send(sender, "That player has not previously joined the authoritative directory.");
            return;
        }
        if (target.playerId().equals(reporterId)) {
            send(sender, "You cannot report yourself.");
            return;
        }
        ReportSubmissionResult result = store.submit(new CreateReportRequest(
                new IdempotencyKey("report:" + UUID.randomUUID()),
                reporterId,
                target.playerId(),
                reasonId,
                description,
                serverId,
                Optional.of(world),
                Optional.of(reporterCoordinates),
                Optional.ofNullable(targetCoordinates),
                now,
                context,
                chat.privateSnapshot(reporterId, target.playerId(), now),
                targetClientEvidence.filter(snapshot -> snapshot.playerId().equals(target.playerId()))
        ));
        if (result instanceof ReportSubmissionResult.Accepted accepted) {
            send(sender, accepted.merged()
                    ? "Your information was merged into report " + accepted.reportId() + "."
                    : "Report submitted as " + accepted.reportId() + ". The reported player was not notified.");
        } else {
            ReportSubmissionResult.Rejected rejected = (ReportSubmissionResult.Rejected) result;
            send(sender, rejected.code() + ": " + rejected.message());
        }
    }

    private void submit(CommandSender sender, Runnable action) {
        try {
            workers.execute(action);
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The report queue is full; no report was created."));
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }

    private static String coordinates(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (arguments.length == 2) {
            String prefix = arguments[1].toLowerCase(java.util.Locale.ROOT);
            return policies.all().stream()
                    .filter(ReasonPolicy::reportable)
                    .map(ReasonPolicy::id)
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .limit(50)
                    .toList();
        }
        return List.of();
    }
}
