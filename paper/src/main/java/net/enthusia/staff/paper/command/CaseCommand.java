package net.enthusia.staff.paper.command;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.SanctionReview;
import net.enthusia.staff.domain.history.CaseHistoryDetail;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.paper.auth.PaperActorResolver;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseCommand implements CommandExecutor {
    private static final String RESTORE_PERMISSION = "enthusiastaff.case.restoreitems";

    private final JavaPlugin plugin;
    private final Supplier<CaseLookup> cases;
    private final Supplier<ConfiscationCoordinator> confiscation;
    private final Supplier<ModerationHistoryStore> histories;
    private final Supplier<ModerationFeatureSettings> settings;
    private final AuthorizationPolicy authorization;
    private final ExecutorService workers;
    private final CommandResponseDispatcher responses;

    public CaseCommand(
            JavaPlugin plugin,
            Supplier<CaseLookup> cases,
            Supplier<ConfiscationCoordinator> confiscation,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        this(plugin, cases, confiscation, null, null, authorization, workers);
    }

    public CaseCommand(
            JavaPlugin plugin,
            Supplier<CaseLookup> cases,
            Supplier<ConfiscationCoordinator> confiscation,
            Supplier<ModerationHistoryStore> histories,
            Supplier<ModerationFeatureSettings> settings,
            AuthorizationPolicy authorization,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.cases = java.util.Objects.requireNonNull(cases, "cases");
        this.confiscation = java.util.Objects.requireNonNull(confiscation, "confiscation");
        this.histories = histories;
        this.settings = settings;
        this.authorization = java.util.Objects.requireNonNull(authorization, "authorization");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
        this.responses = new CommandResponseDispatcher(plugin);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length > 0 && arguments[0].equalsIgnoreCase("restoreitems")) {
            return restore(sender, label, arguments);
        }
        return detail(sender, label, arguments);
    }

    private boolean detail(CommandSender sender, String label, String[] arguments) {
        if (!EstaffCommand.requirePermission(
                sender,
                HistoryCommand.VIEW_PERMISSION,
                "You do not have permission to view case history."
        )) {
            return true;
        }
        String rawCaseId;
        if (arguments.length == 1) {
            rawCaseId = arguments[0];
        } else if (arguments.length == 2 && arguments[0].equalsIgnoreCase("view")) {
            rawCaseId = arguments[1];
        } else {
            sender.sendMessage(Component.text("Usage: /" + label + " [view] <case-id>"));
            return true;
        }
        CaseId caseId;
        try {
            caseId = new CaseId(rawCaseId);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Invalid case ID: " + sanitized(exception.getMessage())));
            return true;
        }
        boolean sensitive = sender instanceof ConsoleCommandSender
                || sender.hasPermission(HistoryCommand.SENSITIVE_PERMISSION);
        submit(sender, () -> loadDetail(sender, caseId, sensitive));
        return true;
    }

    private void loadDetail(CommandSender sender, CaseId caseId, boolean sensitive) {
        if (histories == null || settings == null) {
            responses.send(sender, Component.text("Case history is unavailable in this runtime."));
            return;
        }
        ModerationHistoryStore store = histories.get();
        if (store == null) {
            responses.send(sender, Component.text("Case history is unavailable while storage is offline."));
            return;
        }
        ModerationFeatureSettings active = settings.get();
        try {
            Optional<CaseHistoryDetail> loaded = store.caseDetail(
                    caseId,
                    new HistoryQueryOptions(
                            active.includeRequestEvents(),
                            active.includeAppealEvents(),
                            sensitive
                    )
            );
            if (loaded.isEmpty()) {
                responses.send(sender, Component.text("That case does not exist."));
                return;
            }
            responses.send(sender, render(loaded.orElseThrow(), active, sensitive));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Sanitized case history query failed for " + caseId, exception);
            responses.send(sender, Component.text("Case history could not be loaded; see the server log."));
        }
    }

    private static List<Component> render(
            CaseHistoryDetail detail,
            ModerationFeatureSettings settings,
            boolean sensitive
    ) {
        CaseReview review = detail.caseReview();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")
                .withZone(settings.historyTimezone());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(
                "Case " + review.caseId().value() + " | subject " + review.targetId()
                        + " | " + human(review.sanctionFamily()) + " | " + human(review.state().name())
        ));
        lines.add(Component.text(
                "Created " + formatter.format(review.issuedAt()) + " | public reason: " + review.publicReason()
        ));
        if (sensitive) {
            lines.add(Component.text(
                    "Actor: " + review.actorName() + " (" + review.actorRank() + ", " + review.actorId() + ")"
            ));
            if (!review.internalExplanation().isBlank()) {
                lines.add(Component.text("Internal explanation: " + review.internalExplanation()));
            }
        }
        if (review.sanctions().isEmpty()) {
            lines.add(Component.text("No sanctions are attached to this case."));
        } else {
            lines.add(Component.text("Sanctions:"));
            for (SanctionReview sanction : review.sanctions()) {
                lines.add(Component.text(
                        "- " + sanction.sanctionId() + " | " + human(sanction.type().name())
                                + " | " + human(effectiveStatus(sanction).name())
                                + " | issued " + formatter.format(sanction.issuedAt())
                                + " | original expiration " + expiration(
                                        originalExpiration(detail.timeline(), sanction),
                                        formatter
                                )
                                + " | current expiration " + expiration(sanction.expirationAt(), formatter)
                                + sanction.endedAt()
                                        .map(value -> " | ended " + formatter.format(value))
                                        .orElse("")
                ));
            }
        }
        lines.add(Component.text("Timeline:"));
        for (ModerationHistoryEntry entry : detail.timeline()) {
            StringBuilder line = new StringBuilder("- ")
                    .append(formatter.format(entry.occurredAt()))
                    .append(" | ")
                    .append(human(entry.eventType().name()))
                    .append(" | ")
                    .append(human(entry.status()));
            entry.sanctionId().ifPresent(value -> line.append(" | sanction ").append(value));
            entry.punishmentRequestId().ifPresent(value -> line.append(" | request ").append(value));
            entry.appealId().ifPresent(value -> line.append(" | appeal ").append(value));
            if (!entry.originalExpiration().equals(entry.resultingExpiration())) {
                line.append(" | expiration ")
                        .append(expiration(entry.originalExpiration(), formatter))
                        .append(" -> ")
                        .append(expiration(entry.resultingExpiration(), formatter));
            }
            if (!entry.publicReason().isBlank()) {
                line.append(" | reason: ").append(entry.publicReason());
            }
            if (sensitive) {
                entry.actorName().ifPresent(value -> line.append(" | actor: ").append(value));
                entry.sensitiveReason().ifPresent(value -> line.append(" | internal: ").append(value));
            }
            lines.add(Component.text(line.toString()));
        }
        return List.copyOf(lines);
    }

    private static Optional<java.time.Instant> originalExpiration(
            List<ModerationHistoryEntry> timeline,
            SanctionReview sanction
    ) {
        return timeline.stream()
                .filter(entry -> entry.eventType() == net.enthusia.staff.domain.history.HistoryEventType.SANCTION_CREATED)
                .filter(entry -> entry.sanctionId().filter(sanction.sanctionId()::equals).isPresent())
                .map(ModerationHistoryEntry::originalExpiration)
                .findFirst()
                .orElse(sanction.expirationAt());
    }

    private static String expiration(
            Optional<java.time.Instant> value,
            DateTimeFormatter formatter
    ) {
        return value.map(formatter::format).orElse("permanent/no expiration");
    }

    private static net.enthusia.staff.domain.sanction.SanctionStatus effectiveStatus(SanctionReview sanction) {
        if (sanction.active() && sanction.expirationAt().isPresent()
                && !sanction.expirationAt().orElseThrow().isAfter(java.time.Instant.now())) {
            return net.enthusia.staff.domain.sanction.SanctionStatus.EXPIRED;
        }
        return sanction.status();
    }

    private boolean restore(CommandSender sender, String label, String[] arguments) {
        if (!CommandPermissionGate.require(
                sender,
                RESTORE_PERMISSION,
                "You do not have permission to restore confiscated assets."
        )) {
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Confiscated-item restoration requires an in-game staff actor.");
            return true;
        }
        Actor actor = PaperActorResolver.resolve(viewer).orElse(null);
        if (actor == null || !authorization.permits(actor, ModerationAction.RESTORE_ASSETS)) {
            viewer.sendMessage(Component.text("Only the Founder may restore confiscated assets."));
            return true;
        }
        if (arguments.length != 2) {
            viewer.sendMessage(Component.text("Usage: /" + label + " restoreitems <case-id>"));
            return true;
        }
        CaseId caseId;
        try {
            caseId = new CaseId(arguments[1]);
        } catch (IllegalArgumentException exception) {
            viewer.sendMessage(Component.text("Invalid case ID: " + sanitized(exception.getMessage())));
            return true;
        }
        submit(viewer, () -> resolveAndRestore(viewer, caseId));
        return true;
    }

    private void resolveAndRestore(Player viewer, CaseId caseId) {
        CaseLookup loadedCases = cases.get();
        ConfiscationCoordinator coordinator = confiscation.get();
        if (loadedCases == null) {
            message(viewer, "Case storage is not ready; nothing changed.");
            return;
        }
        if (coordinator == null) {
            message(viewer, "Confiscated-item restoration integration is unavailable.");
            return;
        }
        try {
            UUID targetId = loadedCases.target(caseId).orElse(null);
            if (targetId == null) {
                message(viewer, "That case does not exist.");
                return;
            }
            viewer.getScheduler().execute(plugin, () -> {
                Player target = plugin.getServer().getPlayer(targetId);
                if (target == null) {
                    viewer.sendMessage(Component.text(
                            "Confiscated-item restoration requires the case target on this backend."
                    ));
                    return;
                }
                coordinator.restore(viewer, target, caseId);
            }, null, 1L);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Case restoration lookup failed", exception);
            message(viewer, "Case restoration lookup failed; nothing changed.");
        }
    }

    private void submit(CommandSender sender, Runnable task) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text("The moderation work queue is full; nothing changed."));
        }
    }

    private void message(Player viewer, String body) {
        viewer.getScheduler().execute(
                plugin,
                () -> viewer.sendMessage(Component.text(body)),
                null,
                1L
        );
    }

    private static String human(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String sanitized(String message) {
        return message == null || message.isBlank()
                ? "invalid value"
                : message.lines().findFirst().orElse("invalid value");
    }
}
