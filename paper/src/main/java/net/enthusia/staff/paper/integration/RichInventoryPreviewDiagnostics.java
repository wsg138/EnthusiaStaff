package net.enthusia.staff.paper.integration;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Rate-limited, privacy-safe visibility into optional inventory-preview loss.
 * No player identities, inventory contents, chat bodies, or exception messages
 * are written. These diagnostics do not alter chat or artifact delivery.
 */
final class RichInventoryPreviewDiagnostics {
    private static final System.Logger LOGGER =
            System.getLogger(RichInventoryPreviewDiagnostics.class.getName());
    private static final long LOG_INTERVAL_MS = 60_000L;
    private static final Pattern INVENTORY_TOKEN =
            Pattern.compile("(?i)\\[(?:inv|inventory)\\]");
    private static final AtomicLong LAST_WARNING_MILLIS = new AtomicLong(Long.MIN_VALUE);

    private RichInventoryPreviewDiagnostics() {
    }

    static boolean markerPresent(String message) {
        return message != null
                && InteractiveChatStagingArtifactProvider.firstUnescapedMatch(
                        INVENTORY_TOKEN, message) >= 0;
    }

    static void ifInventoryMarker(String message, String reason) {
        if (markerPresent(message)) {
            warn(reason);
        }
    }

    static void warn(String reason) {
        if (reserveWarning(LAST_WARNING_MILLIS, System.currentTimeMillis())) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "rich_inventory_preview_unavailable reason={0}", reason);
        }
    }

    static boolean reserveWarning(AtomicLong prior, long nowMillis) {
        for (;;) {
            long previous = prior.get();
            if (previous != Long.MIN_VALUE && nowMillis >= previous
                    && nowMillis - previous < LOG_INTERVAL_MS) {
                return false;
            }
            if (prior.compareAndSet(previous, nowMillis)) {
                return true;
            }
        }
    }
}
