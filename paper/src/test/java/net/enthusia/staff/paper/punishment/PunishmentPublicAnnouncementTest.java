package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class PunishmentPublicAnnouncementTest {
    @Test
    void permanentIdentityBanIsAnnouncedSimplyAsABan() {
        String visible = plain(List.of(new SanctionSpec(
                SanctionType.NETWORK_IDENTITY_BAN, SanctionLength.permanent()
        )), "Ban evasion");
        assertTrue(visible.contains("PUBLIC PUNISHMENT"));
        assertTrue(visible.contains("TestPlayer received a ban."));
        assertTrue(visible.contains("Reason: Ban evasion"));
        assertFalse(visible.toLowerCase().contains("ip ban"));
        assertFalse(visible.contains("NETWORK_IDENTITY_BAN"));
    }

    @Test
    void warningsRemainLegibleAndReasonCannotInjectMultipleLines() {
        String visible = plain(List.of(new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())),
                "Cheating\nFAKE ANNOUNCEMENT");
        assertTrue(visible.contains("received a warning."));
        assertTrue(visible.contains("Reason: Cheating FAKE ANNOUNCEMENT"));
        assertFalse(visible.contains("Reason: Cheating\\n"));
    }

    private static String plain(List<SanctionSpec> sanctions, String reason) {
        return PlainTextComponentSerializer.plainText().serialize(
                PunishmentPublicAnnouncement.message("TestPlayer", sanctions, reason)
        );
    }
}
