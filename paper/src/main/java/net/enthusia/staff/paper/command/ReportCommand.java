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
import java.util.logging.Level;
import java.util.logging.Logger;
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
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.report.ChatContextBuffer;
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
    private static final int REQUIRED_ARGUMENTS = 3;
    private static final int TARGET_ARGUMENT = 0;
    private static final int REASON_ARGUMENT = 1;
    private static final int DESCRIPTION_ARGUMENT = 2;
    private static final int MAX_DESCRIPTION_LENGTH = 4_000;
    private static final int REASON_COMPLETION_ARGUMENTS = 2;
    private static final int COMPLETION_LIMIT = 50;

    private final Dependencies dependencies;
    private final ExecutorService workers;

    public ReportCommand(Dependencies dependencies, ExecutorService workers) {
        if (dependencies == null || workers == null) {
            throw new IllegalArgumentException("report command dependencies are required");
        }
        this.dependencies = dependencies;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player reporter)) {
            sender.sendMessage(Component.text("Only a player can submit a player report."));
            return true;
        }
        SubmissionContext submission = prepareSubmission(sender, reporter, arguments);
        if (submission != null) {
            submit(sender, () -> submitReport(sender, submission));
        }
        return true;
    }

    private SubmissionContext prepareSubmission(CommandSender sender, Player reporter, String[] arguments) {
        if (arguments.length < REQUIRED_ARGUMENTS) {
            sender.sendMessage(Component.text("Usage: /report <player|uuid> <reason-id> <description>"));
            return null;
        }
        ReasonPolicy policy = dependencies.policies().find(arguments[REASON_ARGUMENT]).orElse(null);
        if (policy == null || !policy.reportable()) {
            sender.sendMessage(Component.text("That reason is not available for player reports."));
            return null;
        }
        OperationalMode currentMode = dependencies.mode().get();
        if (currentMode != OperationalMode.ACTIVE) {
            sender.sendMessage(Component.text(
                    "Reports are temporarily unavailable while moderation is " + currentMode + '.'
            ));
            return null;
        }
        String description = description(arguments);
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            sender.sendMessage(Component.text("The report description exceeds 4000 characters."));
            return null;
        }
        return captureSubmission(reporter, arguments[TARGET_ARGUMENT], policy.id(), description);
    }

    private SubmissionContext captureSubmission(
            Player reporter,
            String targetName,
            String reasonId,
            String description
    ) {
        Player onlineTarget = dependencies.plugin().getServer().getPlayerExact(targetName);
        Optional<ClientEvidenceSnapshot> targetClientEvidence = onlineTarget == null
                ? Optional.empty()
                : Optional.of(dependencies.clientEvidence().capture(onlineTarget));
        Instant now = dependencies.clock().instant();
        return new SubmissionContext(
                reporter.getUniqueId(),
                targetName,
                reasonId,
                description,
                reporter.getWorld().getKey().asString(),
                coordinates(reporter.getLocation()),
                onlineTarget == null ? null : coordinates(onlineTarget.getLocation()),
                now,
                dependencies.chat().snapshot(now),
                targetClientEvidence
        );
    }

    private void submitReport(CommandSender sender, SubmissionContext submission) {
        StorageAccess storage = storageAccess();
        if (storage == null) {
            send(sender, "Report storage is not ready; no report was created.");
            return;
        }
        if (!storage.sanctions().activeFor(
                submission.reporterId(),
                REPORT_RESTRICTIONS,
                submission.createdAt()
        ).isEmpty()) {
            send(sender, "You cannot submit reports while a report restriction is active.");
            return;
        }
        PlayerIdentity target = storage.players().find(submission.targetName()).orElse(null);
        if (target == null) {
            send(sender, "That player has not previously joined the authoritative directory.");
            return;
        }
        if (target.playerId().equals(submission.reporterId())) {
            send(sender, "You cannot report yourself.");
            return;
        }
        respond(sender, storage.reports().submit(createRequest(submission, target)));
    }

    private StorageAccess storageAccess() {
        PlayerDirectory players = dependencies.players().get();
        ReportStore reports = dependencies.reports().get();
        SanctionLookup sanctions = dependencies.sanctions().get();
        return players == null || reports == null || sanctions == null
                ? null
                : new StorageAccess(players, reports, sanctions);
    }

    private CreateReportRequest createRequest(SubmissionContext submission, PlayerIdentity target) {
        return new CreateReportRequest(
                new IdempotencyKey("report:" + UUID.randomUUID()),
                submission.reporterId(),
                target.playerId(),
                submission.reasonId(),
                submission.description(),
                dependencies.serverId(),
                Optional.of(submission.world()),
                Optional.of(submission.reporterCoordinates()),
                Optional.ofNullable(submission.targetCoordinates()),
                submission.createdAt(),
                submission.publicChatContext(),
                dependencies.chat().privateSnapshot(
                        submission.reporterId(),
                        target.playerId(),
                        submission.createdAt()
                ),
                submission.targetClientEvidence().filter(snapshot -> snapshot.playerId().equals(target.playerId()))
        );
    }

    private void respond(CommandSender sender, ReportSubmissionResult result) {
        if (result instanceof ReportSubmissionResult.Accepted accepted) {
            send(sender, accepted.merged()
                    ? "Your information was merged into report " + accepted.reportId() + "."
                    : "Report submitted as " + accepted.reportId() + ". The reported player was not notified.");
            return;
        }
        ReportSubmissionResult.Rejected rejected = (ReportSubmissionResult.Rejected) result;
        send(sender, rejected.code() + ": " + rejected.message());
    }

    private void submit(CommandSender sender, Runnable action) {
        try {
            workers.execute(() -> execute(sender, action));
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The report queue is full; no report was created."));
        }
    }

    private void execute(CommandSender sender, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            Logger logger = dependencies.plugin().getLogger();
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "Player report submission failed", exception);
            }
            send(sender, "Report submission failed; inspect the sanitized server log.");
        }
    }

    private void send(CommandSender sender, String message) {
        JavaPlugin plugin = dependencies.plugin();
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin,
                () -> sender.sendMessage(Component.text(message))
        );
    }

    private static String description(String[] arguments) {
        return String.join(" ", Arrays.copyOfRange(arguments, DESCRIPTION_ARGUMENT, arguments.length)).trim();
    }

    private static String coordinates(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        if (arguments.length == REASON_COMPLETION_ARGUMENTS) {
            String prefix = arguments[REASON_ARGUMENT].toLowerCase(java.util.Locale.ROOT);
            return dependencies.policies().all().stream()
                    .filter(ReasonPolicy::reportable)
                    .map(ReasonPolicy::id)
                    .filter(id -> id.startsWith(prefix))
                    .sorted()
                    .limit(COMPLETION_LIMIT)
                    .toList();
        }
        return List.of();
    }

    public record Dependencies(
            JavaPlugin plugin,
            Clock clock,
            String serverId,
            Supplier<OperationalMode> mode,
            Supplier<PlayerDirectory> players,
            Supplier<ReportStore> reports,
            Supplier<SanctionLookup> sanctions,
            ReasonPolicyRepository policies,
            ChatContextBuffer chat,
            ClientEvidenceCollector clientEvidence
    ) {
    }

    private record StorageAccess(
            PlayerDirectory players,
            ReportStore reports,
            SanctionLookup sanctions
    ) {
    }

    private record SubmissionContext(
            UUID reporterId,
            String targetName,
            String reasonId,
            String description,
            String world,
            String reporterCoordinates,
            String targetCoordinates,
            Instant createdAt,
            List<CreateReportRequest.ChatContextMessage> publicChatContext,
            Optional<ClientEvidenceSnapshot> targetClientEvidence
    ) {
    }
}
