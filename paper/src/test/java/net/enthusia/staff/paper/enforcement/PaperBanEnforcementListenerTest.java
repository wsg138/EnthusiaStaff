package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PaperBanEnforcementListenerTest {
    private static final UUID TARGET = UUID.fromString("71000000-0000-0000-0000-000000000001");

    @Test
    void activeBanProducesBoundedPublicLoginMessage() {
        ActiveSanction sanction = new ActiveSanction(
                UUID.fromString("71000000-0000-0000-0000-000000000002"),
                new CaseId("PTA38TKYAGSY6M8F"),
                TARGET,
                SanctionType.NETWORK_BAN,
                "Repeated harassment",
                Instant.parse("2026-09-23T12:00:00Z"),
                Optional.of(Instant.parse("2026-10-14T12:00:00Z")),
                Optional.empty()
        );

        PaperBanEnforcementListener.LoginDecision decision =
                PaperBanEnforcementListener.LoginDecision.banned(sanction);

        assertEquals(PaperBanEnforcementListener.Status.BANNED, decision.status());
        assertTrue(decision.message().contains("PTA38TKYAGSY6M8F"));
        assertTrue(decision.message().contains("Repeated harassment"));
        assertTrue(decision.message().contains("2026-10-14T12:00:00Z"));
    }

    @Test
    void unavailableAuthorityUsesGenericFailClosedMessage() {
        PaperBanEnforcementListener.LoginDecision decision =
                PaperBanEnforcementListener.LoginDecision.unverified();

        assertEquals(PaperBanEnforcementListener.Status.UNVERIFIED, decision.status());
        assertEquals("Moderation status could not be verified. Please retry shortly.", decision.message());
    }
}
