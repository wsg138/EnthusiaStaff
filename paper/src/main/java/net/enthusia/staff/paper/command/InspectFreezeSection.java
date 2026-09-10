package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

final class InspectFreezeSection {
    private static final DateTimeFormatter TIMESTAMP =
            ModerationTimestampFormatter.inZone(ZoneId.of("UTC"));

    private final Clock clock;
    private final Supplier<FreezeStore> freezes;
    private final Logger logger;

    InspectFreezeSection(Clock clock, Supplier<FreezeStore> freezes, Logger logger) {
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.freezes = java.util.Objects.requireNonNull(freezes, "freezes");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    List<Component> render(UUID playerId, boolean canManage) {
        try {
            FreezeStore store = freezes.get();
            if (store == null) {
                return unavailable();
            }
            FreezeRecord record = store.readActive(playerId, clock.instant()).orElse(null);
            return record == null ? inactive(playerId, canManage) : active(record, canManage);
        } catch (RuntimeException exception) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "Inspector freeze lookup failed for " + playerId, exception);
            }
            return unavailable();
        }
    }

    private static List<Component> inactive(UUID playerId, boolean canManage) {
        Component status = Component.text("Freeze: not active", NamedTextColor.GREEN);
        return List.of(withAction(status, playerId, false, canManage));
    }

    private static List<Component> active(FreezeRecord record, boolean canManage) {
        Component status = Component.text("Freeze: active", NamedTextColor.RED)
                .append(Component.text(
                        " | applied " + TIMESTAMP.format(record.frozenAt())
                                + " by " + record.frozenBy()
                                + " | " + handling(record),
                        NamedTextColor.GRAY
                ));
        return List.of(
                withAction(status, record.playerId(), true, canManage),
                Component.text("Freeze reason: " + record.reason(), NamedTextColor.GRAY)
        );
    }

    private static Component withAction(
            Component status,
            UUID playerId,
            boolean active,
            boolean canManage
    ) {
        if (!canManage) {
            return status;
        }
        String label = active ? "[Unfreeze]" : "[Freeze]";
        String command = (active ? "/unfreeze " : "/freeze ") + playerId + ' ';
        NamedTextColor color = active ? NamedTextColor.RED : NamedTextColor.GOLD;
        return status.append(Component.space())
                .append(Component.text(label, color).clickEvent(ClickEvent.suggestCommand(command)));
    }

    private static String handling(FreezeRecord record) {
        if (record.keepActive()) {
            return "held until staff release";
        }
        return record.offlineExpiresAt()
                .map(expiry -> "offline timeout at " + TIMESTAMP.format(expiry))
                .orElse("offline timeout starts on disconnect");
    }

    private static List<Component> unavailable() {
        return List.of(Component.text("Freeze: unavailable", NamedTextColor.YELLOW));
    }
}
