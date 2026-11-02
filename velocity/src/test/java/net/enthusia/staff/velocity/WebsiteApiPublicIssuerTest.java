package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import org.junit.jupiter.api.Test;

class WebsiteApiPublicIssuerTest {
    private static final Instant ISSUED = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-10-02T12:00:00Z");
    private static final CaseId CASE = new CaseId("0123456789ABCDEF");

    private static PublicPunishment punishment(Optional<String> issuer, Optional<Instant> ended) {
        return new PublicPunishment("PlayerOne", "BAN", "Rule", "Public reason", ISSUED,
                Optional.empty(), OptionalLong.empty(), PublicPunishmentState.REVOKED,
                CASE, false, issuer, ended);
    }

    @Test
    void onlyPublicIssuerAndEndTimeAreAdded() {
        var response = WebsiteApiResponses.publicPunishment(punishment(Optional.of("StaffName"), Optional.of(ENDED)));
        assertEquals("StaffName", response.get("issuedBy"));
        assertEquals(ENDED.toString(), response.get("endedAt"));
        assertFalse(response.containsKey("actorId"));
        assertFalse(response.containsKey("staffNotes"));
        var absent = WebsiteApiResponses.publicPunishment(punishment(Optional.empty(), Optional.empty()));
        assertFalse(absent.containsKey("issuedBy"));
        assertFalse(absent.containsKey("endedAt"));
    }

    @Test
    void malformedIssuerNamesCannotEnterPublicProjection() {
        assertThrows(IllegalArgumentException.class, () -> punishment(Optional.of("Bad Name"), Optional.empty()));
    }
}
