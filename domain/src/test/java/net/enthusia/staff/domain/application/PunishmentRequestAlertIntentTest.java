package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import org.junit.jupiter.api.Test;

class PunishmentRequestAlertIntentTest {
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-07-31T03:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(3_600);

    @Test
    void rejectsInvalidAudienceCombinations() {
        assertThrows(IllegalArgumentException.class, () -> intent(
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT, null, null, null, 0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED));
        assertThrows(IllegalArgumentException.class, () -> intent(
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT, RECIPIENT, REQUESTER, null, 0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED));
        assertThrows(IllegalArgumentException.class, () -> intent(
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, null, null, StaffRank.MOD, 0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED));
        assertThrows(IllegalArgumentException.class, () -> intent(
                PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS, null, null, StaffRank.ADMIN, 0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED));
    }

    @Test
    void rejectsNonFutureExpiry() {
        assertThrows(IllegalArgumentException.class, () -> new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "temporary", REQUEST, 0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                RECIPIENT, null, null, CaseVisibility.PRIVATE, 1, CREATED_AT, CREATED_AT
        ));
    }

    @Test
    void deterministicKeyChangesOnlyForLogicalIdentity() {
        PunishmentRequestAlertIntent first = intent(
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, null, REQUESTER, StaffRank.MOD, 2,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED);
        PunishmentRequestAlertIntent retry = intent(
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, null, REQUESTER, StaffRank.MOD, 2,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED);
        assertEquals(PunishmentRequestAlertIntentKey.forIntent(first), PunishmentRequestAlertIntentKey.forIntent(retry));
        assertNotEquals(PunishmentRequestAlertIntentKey.forIntent(first), PunishmentRequestAlertIntentKey.forIntent(
                intent(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, null, REQUESTER, StaffRank.MOD, 3,
                        PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED)));
        assertNotEquals(PunishmentRequestAlertIntentKey.forIntent(first), PunishmentRequestAlertIntentKey.forIntent(
                intent(PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, null, REQUESTER, StaffRank.MOD, 2,
                        PunishmentRequestLifecycleEventType.REQUEST_APPROVED)));
        assertNotEquals(PunishmentRequestAlertIntentKey.forIntent(first), PunishmentRequestAlertIntentKey.forIntent(
                intent(PunishmentRequestAlertAudience.DIRECT_RECIPIENT, RECIPIENT, null, null, 2,
                        PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED)));
    }

    @Test
    void claimRequiresDeliveryIdentityToMatchIntent() {
        PunishmentRequestAlertIntent intent = intent(
                PunishmentRequestAlertAudience.DIRECT_RECIPIENT, RECIPIENT, null, null, 0,
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED);
        assertThrows(IllegalArgumentException.class, () -> new PunishmentRequestAlertClaim(
                new PunishmentRequestAlertDeliveryId(UUID.randomUUID(), RECIPIENT),
                intent,
                1,
                CREATED_AT.plusSeconds(30)
        ));
    }

    private static PunishmentRequestAlertIntent intent(
            PunishmentRequestAlertAudience audience,
            UUID recipient,
            UUID excluded,
            StaffRank minimumRank,
            long revision,
            PunishmentRequestLifecycleEventType eventType
    ) {
        return new PunishmentRequestAlertIntent(
                UUID.randomUUID(), "temporary", REQUEST, revision, eventType, audience,
                recipient, excluded, minimumRank, CaseVisibility.PRIVATE, 1, CREATED_AT, EXPIRES_AT
        );
    }
}
