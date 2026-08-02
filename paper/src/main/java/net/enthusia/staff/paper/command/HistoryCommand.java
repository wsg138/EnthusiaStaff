package net.enthusia.staff.paper.command;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class HistoryCommand implements CommandExecutor, TabCompleter {
    public static final String VIEW_PERMISSION = "enthusiastaff.history.view";
    public static final String SENSITIVE_PERMISSION = "enthusiastaff.history.view-sensitive";

    private final JavaPlugin plugin;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<ModerationHistoryStore> histories;
    private final Supplier<ModerationFeatureSettings> settings;
    private final ExecutorService workers;
    private final CommandResponseDispatcher responses;

    public HistoryCommand(
            JavaPlugin plugin,
            Supplier<PlayerDirectory> players,
            Supplier<ModerationHistoryStore> histories,
            Supplier<ModerationFeatureSettings> settings,
            ExecutorService workers
    ) {
        if (plugin == null || players == null || histories == null || settings == null || workers == null) {
            throw new IllegalArgumentException("history command dependencies must be present");
        }
        this.plugin = plugin;
        this.players = players;
        this.histories = histories;
        this.settings = settings;
        this.workers = workers;
        this.responses = new CommandResponseDispatcher(plugin);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!EstaffCommand.requirePermission(
                sender,
                VIEW_PERMISSION,
                "You do not have permission to view punishment history."
        )) {
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " <player> [page]"));
            return true;
        }
        int page;
        try {
            page = args.length == 2 ? Integer.parseInt(args[1]) : 1;
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Page must be a positive whole number."));
            return true;
        }
        if (page < 1) {
            sender.sendMessage(Component.text("Page must be at least 1."));
            return true;
        }
        String input = args[0];
        boolean sensitive = sender instanceof ConsoleCommandSender
                || sender.hasPermission(SENSITIVE_PERMISSION);
        submit(sender, () -> load(sender, input, page, sensitive));
        return true;
    }

    private void load(CommandSender sender, String input, int page, boolean sensitive) {
        PlayerDirectory directory = players.get();
        ModerationHistoryStore history = histories.get();
        if (directory == null || history == null) {
            responses.send(sender, Component.text("Punishment history is unavailable while storage is offline."));
            return;
        }
        PlayerResolution resolution;
        try {
            resolution = directory.resolve(input);
        } catch (RuntimeException exception) {
            failure(sender, "player resolution", exception);
            return;
        }
        if (resolution instanceof PlayerResolution.Missing) {
            responses.send(sender, Component.text("No known player matches '" + input + "'."));
            return;
        }
        if (resolution instanceof PlayerResolution.Ambiguous ambiguous) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text(
                    "The username '" + input + "' matches multiple historical identities. Use an exact UUID:"
            ));
            for (PlayerIdentity match : ambiguous.matches()) {
                lines.add(Component.text(
                        "- " + match.currentUsername().orElse("unknown") + " | "
                                + match.playerId() + " | " + match.platform()
                ));
            }
            responses.send(sender, lines);
            return;
        }
        PlayerResolution.Resolved resolved = (PlayerResolution.Resolved) resolution;
        PlayerIdentity identity = resolved.identity();
        ModerationFeatureSettings active = settings.get();
        HistoryQueryOptions options = new HistoryQueryOptions(
                active.includeRequestEvents(),
                active.includeAppealEvents(),
                sensitive
        );
        ModerationHistoryPage result;
        try {
            result = history.page(identity.playerId(), page, active.historyPageSize(), options);
        } catch (IllegalArgumentException exception) {
            responses.send(sender, Component.text("Invalid history page: " + sanitized(exception.getMessage())));
            return;
        } catch (RuntimeException exception) {
            failure(sender, "history query", exception);
            return;
        }
        responses.send(sender, render(identity, resolved.matchKind(), result, active.historyTimezone(), sensitive));
    }

    private static List<Component> render(
            PlayerIdentity identity,
            PlayerResolution.MatchKind matchKind,
            ModerationHistoryPage page,
            ZoneId timezone,
            boolean sensitive
    ) {
        List<Component> lines = new ArrayList<>();
        String currentName = identity.currentUsername().orElse("unknown");
        lines.add(Component.text(
                "History for " + currentName + " (" + identity.playerId() + ", "
                        + identity.platform() + ", matched by " + human(matchKind.name()) + ")"
        ));
        if (page.entries().isEmpty()) {
            lines.add(Component.text("No moderation history is recorded for this player."));
            return List.copyOf(lines);
        }
        lines.add(Component.text(
                "Page " + page.page() + "/" + page.totalPages() + " — "
                        + page.totalEntries() + " timeline entries"
        ));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z")
                .withZone(timezone);
        for (ModerationHistoryEntry entry : page.entries()) {
            lines.add(Component.text(formatEntry(entry, formatter, sensitive)));
        }
        if (page.page() < page.totalPages()) {
            lines.add(Component.text(
                    "Next page: /history " + identity.playerId() + " " + (page.page() + 1)
            ));
        }
        return List.copyOf(lines);
    }

    private static String formatEntry(
            ModerationHistoryEntry entry,
            DateTimeFormatter formatter,
            boolean sensitive
    ) {
        StringBuilder line = new StringBuilder();
        line.append(formatter.format(entry.occurredAt()))
                .append(" | ")
                .append(human(entry.eventType().name()));
        entry.caseId().ifPresent(value -> line.append(" | case ").append(value.value()));
        entry.sanctionId().ifPresent(value -> line.append(" | sanction ").append(value));
        entry.punishmentRequestId().ifPresent(value -> line.append(" | request ").append(value));
        entry.appealId().ifPresent(value -> line.append(" | appeal ").append(value));
        entry.punishmentType().ifPresent(value -> line.append(" | ").append(human(value)));
        line.append(" | ").append(human(entry.status()));
        if (entry.resultingExpiration().isPresent()) {
            line.append(" | expires ").append(formatter.format(entry.resultingExpiration().orElseThrow()));
        } else if (entry.sanctionId().isPresent()) {
            line.append(" | permanent/no expiration");
        }
        if (!entry.publicReason().isBlank()) {
            line.append(" | reason: ").append(entry.publicReason());
        }
        if (sensitive) {
            entry.actorName().ifPresentOrElse(
                    value -> line.append(" | actor: ").append(value),
                    () -> entry.actorId().ifPresent(value -> line.append(" | actor: ").append(value))
            );
            entry.sensitiveReason().ifPresent(value -> line.append(" | internal: ").append(value));
        }
        return line.toString();
    }

    private static String human(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private void submit(CommandSender sender, Runnable task) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException exception) {
            responses.send(sender, Component.text("History lookup is busy; try again shortly."));
        }
    }

    private void failure(CommandSender sender, String operation, RuntimeException exception) {
        plugin.getLogger().log(Level.WARNING, "Sanitized " + operation + " failed", exception);
        responses.send(sender, Component.text("Punishment history could not be loaded; see the server log."));
    }

    private static String sanitized(String message) {
        if (message == null || message.isBlank()) {
            return "the requested page is unavailable";
        }
        return message.lines().findFirst().orElse("the requested page is unavailable");
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1 || !sender.hasPermission(VIEW_PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return plugin.getServer().getOnlinePlayers().stream()
                .map(player -> player.getName())
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(20)
                .toList();
    }
}
