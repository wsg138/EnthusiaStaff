package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class PunishmentRequestRecipientPolicyTest {
    private final PunishmentRequestRecipientPolicy policy = new PunishmentRequestRecipientPolicy();
    private final UUID requesterId = UUID.randomUUID();

    @Test
    void excludesRequesterAndDeveloperFromReviewerAlerts() {
        assertFalse(policy.mayReceiveReviewerAlert(
                new Actor(requesterId, "Requester", StaffRank.MOD),
                requesterId,
                StaffRank.MOD
        ));
        assertFalse(policy.mayReceiveReviewerAlert(
                new Actor(UUID.randomUUID(), "Developer", StaffRank.DEVELOPER),
                requesterId,
                StaffRank.MOD
        ));
    }

    @Test
    void enforcesReasonMinimumRank() {
        Actor mod = new Actor(UUID.randomUUID(), "Mod", StaffRank.MOD);
        Actor admin = new Actor(UUID.randomUUID(), "Admin", StaffRank.ADMIN);
        Actor founder = new Actor(UUID.randomUUID(), "Founder", StaffRank.FOUNDER);

        assertTrue(policy.mayReceiveReviewerAlert(mod, requesterId, StaffRank.MOD));
        assertFalse(policy.mayReceiveReviewerAlert(mod, requesterId, StaffRank.ADMIN));
        assertTrue(policy.mayReceiveReviewerAlert(admin, requesterId, StaffRank.ADMIN));
        assertFalse(policy.mayReceiveReviewerAlert(admin, requesterId, StaffRank.FOUNDER));
        assertTrue(policy.mayReceiveReviewerAlert(founder, requesterId, StaffRank.FOUNDER));
    }

    @Test
    void limitsOperationalAlertsToAdminAndFounder() {
        assertFalse(policy.mayReceiveOperationalAlert(
                new Actor(UUID.randomUUID(), "Mod", StaffRank.MOD)
        ));
        assertTrue(policy.mayReceiveOperationalAlert(
                new Actor(UUID.randomUUID(), "Admin", StaffRank.ADMIN)
        ));
        assertTrue(policy.mayReceiveOperationalAlert(
                new Actor(UUID.randomUUID(), "Founder", StaffRank.FOUNDER)
        ));
    }
}
