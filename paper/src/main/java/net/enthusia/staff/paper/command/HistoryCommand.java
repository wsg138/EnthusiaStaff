package net.enthusia.staff.paper.command;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerNames;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.ModerationHistoryStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.paper.config.ModerationFeatureSettings;
import net.enthusia.staff.paper.presentation.StaffMessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private static final String DETAIL_SEPARATOR = " • ";

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
            sender.sendMessage(StaffMessageStyle.usage("Usage: /" + label + " <player|uuid> [page]"));
            return true;
        }
        int page;
        try {
            page = args.length == 2 ? Integer.parseInt(args[1]) : 1;
        } catch (NumberFormatException exception) {
            sender.sendMessage(StaffMessageStyle.warning("Page must be a positive whole number."));
            return true;
        }
        if (page < 1) {
            sender.sendMessage(StaffMessageStyle.warning("Page must be at least 1."));
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
            responses.send(sender, StaffMessageStyle.error(
                    "Punishment history is unavailable while storage is offline."
            ));
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
            responses.send(sender, StaffMessageStyle.warning("No known player matches '" + input + "'."));
            return;
        }
        if (resolution instanceof PlayerResolution.Ambiguous ambiguous) {
            responses.send(sender, ambiguousIdentityLines(input, ambiguous));
            return;
        }
        PlayerResolution.Resolved resolved = (PlayerResolution.Resolved) resolution;
        PlayerIdentity identity = resolved.identity();
        ModerationFeatureSettings active = settings.get();
        if (active == null) {
            responses.send(sender, StaffMessageStyle.error(
                    "Punishment history is unavailable because validated settings are not active."
            ));
            return;
        }
        queryHistory(sender, page, sensitive, history, resolved, identity, active);
    }

    private void queryHistory(
            CommandSender sender,
            int page,
            boolean sensitive,
            ModerationHistoryStore history,
            PlayerResolution.Resolved resolved,
            PlayerIdentity identity,
            ModerationFeatureSettings active
    ) {
        HistoryQueryOptions options = new HistoryQueryOptions(
                active.includeRequestEvents(),
                active.includeAppealEvents(),
                sensitive
        );
        try {
            ModerationHistoryPage result = history.page(identity.playerId(), page, active.historyPageSize(), options);
            responses.send(sender, render(
                    identity,
                    resolved.matchKind(),
                    result,
                    active.historyTimezone(),
                    sensitive,
                    new PlayerNames(players.get())
            ));
        } catch (IllegalArgumentException exception) {
            responses.send(sender, StaffMessageStyle.warning("Invalid history page: " + sanitized(exception.getMessage())));
        } catch (RuntimeException exception) {
            failure(sender, "history query", exception);
        }
    }

    private static List<Component> ambiguousIdentityLines(String input, PlayerResolution.Ambiguous ambiguous) {
        List<Component> lines = new ArrayList<>();
        lines.add(StaffMessageStyle.header("EnthusiaStaff • Player Match"));
        lines.add(Component.text("Multiple historical identities match ", NamedTextColor.GRAY)
                .append(StaffMessageStyle.player(input))
                .append(Component.text(". Use an exact UUID:", NamedTextColor.GRAY)));
        for (PlayerIdentity match : ambiguous.matches()) {
            lines.add(Component.text("  • ", NamedTextColor.DARK_GRAY)
                    .append(StaffMessageStyle.player(match.currentUsername().orElse("unknown")))
                    .append(Component.text(DETAIL_SEPARATOR, NamedTextColor.DARK_GRAY))
                    .append(StaffMessageStyle.id(match.playerId()))
                    .append(Component.text(DETAIL_SEPARATOR + match.platform(), NamedTextColor.GRAY)));
        }
        if (ambiguous.truncated()) {
            lines.add(Component.text("  … Additional matches exist; use an exact UUID.", NamedTextColor.DARK_GRAY));
        }
        return List.copyOf(lines);
    }

    static List<Component> render(
            PlayerIdentity identity,
            PlayerResolution.MatchKind matchKind,
            ModerationHistoryPage page,
            ZoneId timezone,
            boolean sensitive,
            Function<java.util.UUID, String> names
    ) {
        List<Component> lines = new ArrayList<>();
        String currentName = PlayerNames.label(identity);
        lines.add(StaffMessageStyle.header("EnthusiaStaff • History"));
        lines.add(subjectLine(identity, currentName, matchKind));
        if (page.entries().isEmpty()) {
            lines.add(StaffMessageStyle.info("No moderation history is recorded for this player."));
            return List.copyOf(lines);
        }
        lines.add(pageLine(page));
        lines.add(StaffMessageStyle.section("Timeline"));
        DateTimeFormatter formatter = ModerationTimestampFormatter.inZone(timezone);
        page.entries().forEach(entry -> lines.add(formatEntry(entry, formatter, sensitive, names)));
        if (page.page() < page.totalPages()) {
            lines.add(Component.text("Next page  ", NamedTextColor.DARK_GRAY)
                    .append(StaffMessageStyle.command(
                            "/history " + identity.playerId() + " " + (page.page() + 1)
                    )));
        }
        return List.copyOf(lines);
    }

    private static Component subjectLine(
            PlayerIdentity identity,
            String currentName,
            PlayerResolution.MatchKind matchKind
    ) {
        return Component.text("  Player  ", NamedTextColor.GRAY)
                .append(StaffMessageStyle.player(currentName))
                .append(Component.text(DETAIL_SEPARATOR + identity.platform(), NamedTextColor.GRAY))
                .append(Component.text(DETAIL_SEPARATOR + "matched by " + human(matchKind.name()), NamedTextColor.DARK_GRAY));
    }

    private static Component pageLine(ModerationHistoryPage page) {
        return Component.text("  Page  ", NamedTextColor.GRAY)
                .append(Component.text(page.page() + "/" + page.totalPages(), NamedTextColor.WHITE))
                .append(Component.text(DETAIL_SEPARATOR, NamedTextColor.DARK_GRAY))
                .append(Component.text(page.totalEntries() + " timeline entries", NamedTextColor.GRAY));
    }

    private static Component formatEntry(
            ModerationHistoryEntry entry,
            DateTimeFormatter formatter,
            boolean sensitive,
            Function<java.util.UUID, String> names
    ) {
        Component line = Component.text("  • ", NamedTextColor.DARK_GRAY)
                .append(Component.text(formatter.format(entry.occurredAt()), NamedTextColor.DARK_GRAY))
                .append(Component.text(DETAIL_SEPARATOR + human(entry.eventType().name()), NamedTextColor.WHITE));
        line = appendIds(line, entry);
        line = line.append(Component.text(DETAIL_SEPARATOR + human(entry.status()), statusColor(entry.status())));
        line = appendExpiration(line, entry, formatter);
        if (!entry.publicReason().isBlank()) {
            line = line.append(Component.text(DETAIL_SEPARATOR + "reason: " + entry.publicReason(), NamedTextColor.GRAY));
        }
        return sensitive ? appendSensitive(line, entry, names) : line;
    }

    private static Component appendIds(Component line, ModerationHistoryEntry entry) {
        Component result = line;
        if (entry.caseId().isPresent()) {
            result = appendId(result, "case", entry.caseId().orElseThrow().value());
        }
        if (entry.sanctionId().isPresent()) {
            result = appendId(result, "sanction", entry.sanctionId().orElseThrow());
        }
        if (entry.punishmentRequestId().isPresent()) {
            result = appendId(result, "request", entry.punishmentRequestId().orElseThrow());
        }
        if (entry.appealId().isPresent()) {
            result = appendId(result, "appeal", entry.appealId().orElseThrow());
        }
        if (entry.punishmentType().isPresent()) {
            result = result.append(Component.text(
                    DETAIL_SEPARATOR + human(entry.punishmentType().orElseThrow()),
                    NamedTextColor.GRAY
            ));
        }
        return result;
    }

    private static Component appendId(Component line, String label, Object value) {
        return line.append(Component.text(DETAIL_SEPARATOR + label + ' ', NamedTextColor.DARK_GRAY))
                .append(StaffMessageStyle.id(value));
    }

    private static Component appendExpiration(
            Component line,
            ModerationHistoryEntry entry,
            DateTimeFormatter formatter
    ) {
        if (!entry.originalExpiration().equals(entry.resultingExpiration())) {
            return line.append(Component.text(DETAIL_SEPARATOR + "expiration ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(expiration(entry.originalExpiration(), formatter), NamedTextColor.GRAY))
                    .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(expiration(entry.resultingExpiration(), formatter), NamedTextColor.GRAY));
        }
        if (entry.sanctionId().isPresent()) {
            return line.append(Component.text(
                    DETAIL_SEPARATOR + "expiration " + expiration(entry.resultingExpiration(), formatter),
                    NamedTextColor.GRAY
            ));
        }
        return line;
    }

    private static Component appendSensitive(Component line, ModerationHistoryEntry entry,
            Function<java.util.UUID, String> names) {
        Component result = line;
        if (entry.actorName().filter(value -> !value.isBlank()).isPresent()) {
            result = result.append(Component.text(DETAIL_SEPARATOR + "actor ", NamedTextColor.DARK_GRAY))
                    .append(StaffMessageStyle.player(entry.actorName().orElseThrow()));
        } else if (entry.actorId().isPresent()) {
            result = appendId(result, "actor", names.apply(entry.actorId().orElseThrow()));
        }
        if (entry.sensitiveReason().isPresent()) {
            result = result.append(Component.text(
                    DETAIL_SEPARATOR + "internal: " + entry.sensitiveReason().orElseThrow(),
                    NamedTextColor.DARK_GRAY
            ));
        }
        return result;
    }

    private static NamedTextColor statusColor(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (normalized.contains("active") || normalized.contains("complete") || normalized.contains("approved")) {
            return NamedTextColor.GREEN;
        }
        if (normalized.contains("fail") || normalized.contains("reject") || normalized.contains("overturn")) {
            return NamedTextColor.RED;
        }
        return NamedTextColor.GOLD;
    }

    private static String expiration(
            java.util.Optional<java.time.Instant> value,
            DateTimeFormatter formatter
    ) {
        return value.map(formatter::format).orElse("permanent/no expiration");
    }

    private static String human(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isEmpty()
                ? "Unknown"
                : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private void submit(CommandSender sender, Runnable task) {
        try {
            workers.execute(task);
        } catch (RejectedExecutionException exception) {
            responses.send(sender, StaffMessageStyle.warning("History lookup is busy; try again shortly."));
        }
    }

    private void failure(CommandSender sender, String operation, RuntimeException exception) {
        plugin.getLogger().log(Level.WARNING, "Sanitized " + operation + " failed", exception);
        responses.send(sender, StaffMessageStyle.error(
                "Punishment history could not be loaded; see the server log."
        ));
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
