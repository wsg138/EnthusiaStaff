package net.enthusia.staff.domain.investigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class InvestigationEvidenceTest {
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final ModerationSubjectId SUBJECT = new ModerationSubjectId(
            UUID.fromString("11111111-2222-3333-4444-555555555555"));
    private static final UUID CASE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID ACTOR_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final String FOCUS_MESSAGE_ID = "100";
    private static final String MESSAGE_CONTEXT = "MESSAGE_CONTEXT";

    @Test
    void messageCaptureKeepsActorActionAndFiveByFiveBounds() {
        InvestigationEvidence.Capture capture = new InvestigationEvidence.Capture(
                UUID.randomUUID(), "d09:evidence:123", SUBJECT, CASE_ID, message(FOCUS_MESSAGE_ID),
                messages("90", 5), messages("110", 5), ACTOR_ID, MESSAGE_CONTEXT, NOW
        );

        assertEquals(ACTOR_ID, capture.capturedBy());
        assertEquals(MESSAGE_CONTEXT, capture.action());
        assertEquals(5, capture.before().size());
        assertEquals(5, capture.after().size());
    }

    @Test
    void captureRejectsUnboundedOrDuplicateContextAndMissingActor() {
        assertThrows(IllegalArgumentException.class, () -> new InvestigationEvidence.Capture(
                UUID.randomUUID(), "d09:evidence:too-many", SUBJECT, CASE_ID, message(FOCUS_MESSAGE_ID),
                messages("80", 6), List.of(), ACTOR_ID, MESSAGE_CONTEXT, NOW
        ));
        assertThrows(IllegalArgumentException.class, () -> new InvestigationEvidence.Capture(
                UUID.randomUUID(), "d09:evidence:duplicate", SUBJECT, CASE_ID, message(FOCUS_MESSAGE_ID),
                List.of(message(FOCUS_MESSAGE_ID)), List.of(), ACTOR_ID, MESSAGE_CONTEXT, NOW
        ));
        assertThrows(IllegalArgumentException.class, () -> new InvestigationEvidence.Capture(
                UUID.randomUUID(), "d09:evidence:no-actor", SUBJECT, CASE_ID, message(FOCUS_MESSAGE_ID),
                List.of(), List.of(), null, MESSAGE_CONTEXT, NOW
        ));
    }

    @Test
    void contextBatchRejectsEmptyAndMoreThanTenMessages() {
        assertThrows(IllegalArgumentException.class, () -> new InvestigationEvidence.ContextBatch(
                UUID.randomUUID(), "d09:context:empty", List.of(), NOW
        ));
        assertThrows(IllegalArgumentException.class, () -> new InvestigationEvidence.ContextBatch(
                UUID.randomUUID(), "d09:context:large", messages("200", 11), NOW
        ));
    }

    private static List<InvestigationEvidence.Message> messages(String first, int count) {
        long start = Long.parseLong(first);
        return java.util.stream.LongStream.range(start, start + count)
                .mapToObj(value -> message(Long.toString(value)))
                .toList();
    }

    private static InvestigationEvidence.Message message(String id) {
        return new InvestigationEvidence.Message(
                "1410303324745371709", "1541286004298752091", id,
                new DiscordUserId("223456789012345678"), NOW.minusSeconds(60), Optional.empty(),
                "https://discord.com/channels/1410303324745371709/1541286004298752091/" + id,
                "private evidence", List.of()
        );
    }
}
