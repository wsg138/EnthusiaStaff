package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

final class FreezeQueryHandler {
    private static final int MAX_LIST_RESULTS = 25;
    private static final int LIST_QUERY_LIMIT = MAX_LIST_RESULTS + 1;
    private static final int MAX_REASON_DISPLAY = 80;
    private static final DateTimeFormatter TIMESTAMP = ModerationTimestampFormatter.inZone(ZoneId.of("UTC"));

    private final Clock clock;
    private final Supplier<PlayerDirectory> players;
    private final Supplier<FreezeStore> freezes;
    private final BiConsumer<CommandSender, List<Component>> responses;

    FreezeQueryHandler(
            Clock clock,
            Supplier<PlayerDirectory> players,
            Supplier<FreezeStore> freezes,
            BiConsumer<CommandSender, List<Component>> responses
    ) {
        this.clock = clock;
        this.players = players;
        this.freezes = freezes;
        this.responses = responses;
    }

    void status(CommandSender sender, String targetInput) {
        PlayerDirectory directory = players.get();
        FreezeStore store = freezes.get();
        if (!storageReady(sender, directory, store)) {
            return;
        }
        PlayerResolution resolution = directory.resolve(targetInput);
        if (resolution instanceof PlayerResolution.Missing) {
            respond(sender, "No known player matches '" + targetInput + "'.");
            return;
        }
        if (resolution instanceof PlayerResolution.Ambiguous ambiguous) {
            respond(sender, ambiguousPlayerLines(targetInput, ambiguous));
            return;
        }
        PlayerIdentity target = ((PlayerResolution.Resolved) resolution).identity();
        FreezeRecord record = store.readActive(target.playerId(), clock.instant()).orElse(null);
        if (record == null) {
            respond(sender, identityLabel(target) + " is not currently frozen.");
            return;
        }
        respond(sender, renderStatus(directory, target, record));
    }

    void list(CommandSender sender) {
        PlayerDirectory directory = players.get();
        FreezeStore store = freezes.get();
        if (!storageReady(sender, directory, store)) {
            return;
        }
        List<FreezeRecord> loaded = store.listActive(clock.instant(), LIST_QUERY_LIMIT);
        if (loaded.isEmpty()) {
            respond(sender, "No active player freezes were found.");
            return;
        }
        boolean truncated = loaded.size() > MAX_LIST_RESULTS;
        List<FreezeRecord> visible = loaded.subList(0, Math.min(loaded.size(), MAX_LIST_RESULTS));
        List<Component> lines = new ArrayList<>(visible.size() + 2);
        lines.add(Component.text("Active player freezes (oldest first):"));
        for (FreezeRecord record : visible) {
            lines.add(Component.text("- " + knownIdentityLabel(directory, record.playerId())
                    + " | " + TIMESTAMP.format(record.frozenAt())
                    + " | " + handling(record)
                    + " | " + shorten(record.reason())));
        }
        if (truncated) {
            lines.add(Component.text("Showing the first " + MAX_LIST_RESULTS + " active freezes."));
        }
        respond(sender, lines);
    }

    private boolean storageReady(CommandSender sender, PlayerDirectory directory, FreezeStore store) {
        if (directory != null && store != null) {
            return true;
        }
        respond(sender, "Freeze storage is not ready.");
        return false;
    }

    private static List<Component> ambiguousPlayerLines(
            String input,
            PlayerResolution.Ambiguous ambiguous
    ) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("The name '" + input + "' matches more than one player. Use an exact UUID:"));
        for (PlayerIdentity match : ambiguous.matches()) {
            lines.add(Component.text("- " + identityLabel(match)));
        }
        if (ambiguous.truncated()) {
            lines.add(Component.text("Additional matches exist; use an exact UUID."));
        }
        return List.copyOf(lines);
    }

    private static List<Component> renderStatus(
            PlayerDirectory directory,
            PlayerIdentity target,
            FreezeRecord record
    ) {
        return List.of(
                Component.text("Freeze status for " + identityLabel(target)),
                Component.text("Applied by " + knownIdentityLabel(directory, record.frozenBy())
                        + " at " + TIMESTAMP.format(record.frozenAt())),
                Component.text("Reason: " + record.reason()),
                Component.text("Current handling: " + handling(record))
        );
    }

    private static String handling(FreezeRecord record) {
        if (record.keepActive()) {
            return "held until staff release";
        }
        return record.offlineExpiresAt()
                .map(expiry -> "offline timeout at " + TIMESTAMP.format(expiry))
                .orElse("offline timeout starts when the player disconnects");
    }

    private static String shorten(String reason) {
        if (reason.length() <= MAX_REASON_DISPLAY) {
            return reason;
        }
        return reason.substring(0, MAX_REASON_DISPLAY - 1) + '\u2026';
    }

    private static String knownIdentityLabel(PlayerDirectory directory, UUID playerId) {
        return directory.find(playerId.toString())
                .map(FreezeQueryHandler::identityLabel)
                .orElse(playerId.toString());
    }

    private static String identityLabel(PlayerIdentity identity) {
        return identity.currentUsername()
                .map(name -> name + " (" + identity.playerId() + ')')
                .orElse(identity.playerId().toString());
    }

    private void respond(CommandSender sender, String message) {
        respond(sender, List.of(Component.text(message)));
    }

    private void respond(CommandSender sender, List<Component> messages) {
        responses.accept(sender, List.copyOf(messages));
    }
}
