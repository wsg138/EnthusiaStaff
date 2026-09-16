package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import org.junit.jupiter.api.Test;

class StaffModerationInvestigationNoteRendererTest {
    @Test
    void investigationNotesAreEscapedAndRenderedSeparatelyFromLegacyNotes() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        ModerationSubjectId subject = new ModerationSubjectId(UUID.randomUUID());
        UUID actor = UUID.randomUUID();
        InvestigationNote note = new InvestigationNote(
                UUID.randomUUID(), subject,
                new InvestigationNote.Scope(InvestigationNote.ScopeType.SUBJECT, subject.value().toString()),
                InvestigationNote.Visibility.STAFF, "private @everyone `note`", actor, now, actor, now, 0, false
        );
        StaffModerationReadService.Target target = new StaffModerationReadService.Target(
                StaffModerationReadService.TargetKind.DISCORD,
                Optional.of(new DiscordUserId("123456789012345678")), Optional.empty(), Optional.empty()
        );
        StaffModerationReadService.Snapshot snapshot = new StaffModerationReadService.Snapshot(
                target, List.of(), List.of(), List.of(), 0, Map.of(), List.of(), List.of(), 0
        );

        String rendered = StaffModerationTextRenderer.notes(snapshot, List.of(note));
        assertTrue(rendered.contains("private ＠everyone 'note'"));
        assertTrue(rendered.contains("STAFF / SUBJECT"));
        assertFalse(rendered.contains("@everyone"));
    }
}
