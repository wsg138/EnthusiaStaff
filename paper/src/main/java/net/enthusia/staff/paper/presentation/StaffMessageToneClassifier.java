package net.enthusia.staff.paper.presentation;

import java.util.Locale;
import net.enthusia.staff.domain.OperationalMode;

/** Classifies Staff presentation tones without coupling callers to keyword rules. */
final class StaffMessageToneClassifier {
    private StaffMessageToneClassifier() {
    }

    static StaffMessageStyle.Tone toneFor(String text) {
        String normalized = normalize(text);
        if (containsAny(normalized, "do not have permission", "permission denied", "not authorized",
                "not permitted", "only the founder", "failed", "failure", "could not", "unable to",
                "cannot", "not ready", "storage is offline", "not online", "must be online",
                "queue is full", "blocking")) {
            return StaffMessageStyle.Tone.ERROR;
        }
        if (containsAny(normalized, "disabled", "shadow migration", "gated", "requires", "required",
                "confirm", "scheduled", "pending", "busy", "already being", "try again",
                "only available", "unavailable")) {
            return StaffMessageStyle.Tone.WARNING;
        }
        if (containsAny(normalized, "success", "healthy", "connected", "enabled", "completed", "restored",
                "applied", "saved", "submitted", "created", "finished")) {
            return StaffMessageStyle.Tone.SUCCESS;
        }
        return StaffMessageStyle.Tone.MUTED;
    }

    static StaffMessageStyle.Tone issueTone(String key, String detail, OperationalMode mode) {
        String normalizedKey = normalize(key);
        String normalizedDetail = normalize(detail);
        if (mode == OperationalMode.SHADOW_MIGRATION && migrationWarning(normalizedKey, normalizedDetail)) {
            return StaffMessageStyle.Tone.WARNING;
        }
        if (hardFailure(normalizedKey)) {
            return StaffMessageStyle.Tone.ERROR;
        }
        return StaffMessageStyle.Tone.WARNING;
    }

    private static boolean migrationWarning(String key, String detail) {
        return containsAny(key, "cutover", "migration")
                || containsAny(detail, "disabled", "gated", "not configured");
    }

    private static boolean hardFailure(String key) {
        return containsAny(key, "mariadb", "channel", "configuration", "operational-state");
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
