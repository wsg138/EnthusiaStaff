package net.enthusia.staff.paper.freeze;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class FreezeNoticePresentation {
    private static final Duration OFFLINE_TIMEOUT = Duration.ofMinutes(10);

    private FreezeNoticePresentation() {
    }

    public static List<Component> render(FreezeRecord record, String actorName) {
        if (record == null) {
            return List.of(Component.text(
                    "You are frozen while staff verifies the durable freeze record.",
                    NamedTextColor.RED
            ));
        }
        String displayedActor = actorName == null || actorName.isBlank()
                ? record.frozenBy().toString()
                : actorName;
        return List.of(
                Component.text("You are frozen by network staff.", NamedTextColor.RED),
                Component.text("Frozen by: " + displayedActor, NamedTextColor.GRAY),
                Component.text("Reason: " + record.reason(), NamedTextColor.GRAY),
                Component.text("Duration: " + duration(record), NamedTextColor.GRAY)
        );
    }

    static String duration(FreezeRecord record) {
        if (record.keepActive()) {
            return "until staff release (offline timeout disabled)";
        }
        Instant offlineExpiry = record.offlineExpiresAt().orElse(null);
        if (offlineExpiry != null) {
            return "until staff release; offline expiry " + offlineExpiry;
        }
        return "until staff release; disconnecting starts a "
                + OFFLINE_TIMEOUT.toMinutes() + " minute offline timeout";
    }
}
