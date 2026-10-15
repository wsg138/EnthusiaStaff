package net.enthusia.staff.discordbot.modcase;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.modcase.CaseNotice;
import org.junit.jupiter.api.Test;

class DiscordCaseControlsTest {
    private static final Instant TIME = Instant.parse("2026-10-14T16:00:00Z");

    private static CaseNotice notice() {
        return new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.DISCORD, "guild:10/channel:20",
                4, CaseNotice.Priority.HIGH, CaseNotice.State.OPEN, List.of("event:57"),
                TIME, TIME.plusSeconds(90), null);
    }

    @Test
    void signedClickBindsActionOriginCaseRevisionAndExactScope() {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 123);
        DiscordCaseControls signer = new DiscordCaseControls(key);
        CaseNotice snapshot = notice();
        String signed = signer.encode(snapshot, CaseNotice.Action.CLAIM);
        assertTrue(signed.length() <= 100);
        assertEquals(CaseNotice.Action.CLAIM, signer.parse(signed, snapshot.scope()).action());
        assertEquals(snapshot.caseId(), signer.parse(signed, snapshot.scope()).caseId());
        assertEquals(snapshot.revision(), signer.parse(signed, snapshot.scope()).revision());
        assertThrows(IllegalArgumentException.class,
                () -> signer.parse(signed, "guild:10/channel:other"));
        assertThrows(IllegalArgumentException.class,
                () -> signer.parse(signed.replace("CLAIM", "RESOLVE"), snapshot.scope()));
        assertThrows(IllegalArgumentException.class,
                () -> signer.parse(signed.replace(":4:", ":5:"), snapshot.scope()));
        assertThrows(IllegalArgumentException.class,
                () -> new DiscordCaseControls(new byte[8]));
    }

    @Test
    void cardExcludesRawContentAndMentions() {
        CaseNotice snapshot = notice();
        String card = DiscordCaseCard.content(snapshot);
        assertTrue(card.contains(snapshot.caseId().toString()));
        assertFalse(card.contains("event:57"));
        assertFalse(card.contains("@everyone"));
        assertFalse(card.contains("<@"));
        assertFalse(card.contains(snapshot.scope()));
    }

    @Test
    void initialCaseRevisionZeroCanBeClaimedWithoutRenumbering() {
        CaseNotice initial = new CaseNotice(UUID.randomUUID(), CaseNotice.Origin.MINECRAFT,
                "smp", 0, CaseNotice.Priority.HIGH, CaseNotice.State.OPEN,
                List.of("event:initial"), TIME, TIME.plusSeconds(90), null);
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 49);
        DiscordCaseControls signer = new DiscordCaseControls(key);
        String control = signer.encode(initial, CaseNotice.Action.CLAIM);
        DiscordCaseControls.Parsed parsed = signer.parse(control, initial.scope());
        assertEquals(0, parsed.revision());
        assertEquals(initial.caseId(), parsed.caseId());
        assertEquals(CaseNotice.Action.CLAIM, parsed.action());
        assertThrows(IllegalArgumentException.class,
                () -> signer.parse(control, "other-server"));
    }

}
