package net.enthusia.staff.paper.sanction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.CaseState;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.casefile.SanctionReview;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class SanctionChangeGuiCatalogScopeTest {
    private final SanctionChangeGuiCatalog catalog =
            new SanctionChangeGuiCatalog(new DefaultAuthorizationPolicy());
    private final Actor actor = new Actor(UUID.randomUUID(), "Founder", StaffRank.FOUNDER);

    @Test
    void unbanDoesNotOfferCaseWideEndWhenOnlyMuteRemainsActive() {
        CaseReview review = review(List.of(
                sanction(SanctionType.BAN, SanctionStatus.ENDED_EARLY),
                sanction(SanctionType.MUTE, SanctionStatus.ACTIVE)
        ));
        assertTrue(catalog.actions(actor, review, permission -> true, "unban").isEmpty());
        assertEquals(List.of(SanctionChangeAction.END_EARLY),
                catalog.actions(actor, review, permission -> true, "unmute"));
    }

    @Test
    void unwarnOnlyOffersRevokeForMatchingWarning() {
        CaseReview review = review(List.of(
                sanction(SanctionType.BAN, SanctionStatus.ACTIVE),
                sanction(SanctionType.WARNING, SanctionStatus.APPLIED)
        ));
        assertEquals(List.of(SanctionChangeAction.REVOKE),
                catalog.actions(actor, review, permission -> true, "unwarn"));
        assertTrue(catalog.actions(actor, review, permission -> true, "unmute").isEmpty());
    }

    private static SanctionReview sanction(SanctionType type, SanctionStatus status) {
        return new SanctionReview(UUID.randomUUID(), type, status, Instant.now(),
                Optional.empty(), Optional.empty(), 0);
    }

    private static CaseReview review(List<SanctionReview> sanctions) {
        UUID target = UUID.randomUUID();
        return new CaseReview(new CaseId("01HZX3K8M2N4P123"), target, UUID.randomUUID(),
                "Issuer", "MOD", "Test reason", "test.reason", "BAN",
                "Internal note", "test-config", CaseVisibility.PRIVATE, CaseState.OPEN,
                Instant.now(), 0, Optional.empty(), sanctions, Optional.empty());
    }
}
