package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class FreezeNoticePresentationTest {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void noticeExplainsActorReasonAndOnlineTimeoutBehavior() {
        FreezeRecord record = record(Optional.empty(), false);

        List<Component> lines = FreezeNoticePresentation.render(record, "Moderator");
        String plain = lines.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .reduce("", (left, right) -> left + '\n' + right);

        assertTrue(plain.contains("Frozen by: Moderator"));
        assertTrue(plain.contains("Reason: verification"));
        assertTrue(plain.contains("10 minute offline timeout"));
    }

    @Test
    void keepActiveAndPersistedOfflineExpiryAreDistinguished() {
        assertEquals(
                "until staff release (offline timeout disabled)",
                FreezeNoticePresentation.duration(record(Optional.empty(), true))
        );
        assertEquals(
                "until staff release; offline expiry 2026-09-23T12:10:00Z",
                FreezeNoticePresentation.duration(record(Optional.of(NOW.plusSeconds(600)), false))
        );
    }

    private static FreezeRecord record(Optional<Instant> offlineExpiry, boolean keepActive) {
        return new FreezeRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "verification",
                NOW,
                offlineExpiry,
                keepActive,
                1L
        );
    }
}
