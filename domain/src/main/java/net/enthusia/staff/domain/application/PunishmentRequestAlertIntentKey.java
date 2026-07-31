package net.enthusia.staff.domain.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PunishmentRequestAlertIntentKey {
    private static final String PREFIX = "pra:v1:";

    private PunishmentRequestAlertIntentKey() {
    }

    public static String forIntent(PunishmentRequestAlertIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("alert intent must be present");
        }
        String canonical = String.join("|",
                Integer.toString(intent.schemaVersion()),
                intent.requestId().toString().toLowerCase(java.util.Locale.ROOT),
                Long.toString(intent.requestRevision()),
                intent.eventType().name(),
                intent.audience().name(),
                value(intent.recipientId()),
                value(intent.excludedRecipientId()),
                intent.minimumRank() == null ? "-" : intent.minimumRank().name(),
                intent.visibility().name()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString().toLowerCase(java.util.Locale.ROOT);
    }
}
