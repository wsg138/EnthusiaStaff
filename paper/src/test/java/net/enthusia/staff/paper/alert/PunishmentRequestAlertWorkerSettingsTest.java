package net.enthusia.staff.paper.alert;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PunishmentRequestAlertWorkerSettingsTest {
    @Test
    void safeDefaultsRemainDisabledUnlessExplicitlyEnabled() {
        assertFalse(PunishmentRequestAlertWorkerSettings.safeDefaults(false).enabled());
    }

    @Test
    void exponentialBackoffCapsWithoutOverflow() {
        PunishmentRequestAlertWorkerSettings settings = PunishmentRequestAlertWorkerSettings.safeDefaults(true);

        assertEquals(Duration.ofSeconds(5), settings.retryDelay(1));
        assertEquals(Duration.ofSeconds(10), settings.retryDelay(2));
        assertEquals(Duration.ofSeconds(20), settings.retryDelay(3));
        assertEquals(Duration.ofMinutes(5), settings.retryDelay(100));
    }

    @Test
    void rejectsInvalidBounds() {
        PunishmentRequestAlertWorkerSettings defaults = PunishmentRequestAlertWorkerSettings.safeDefaults(true);

        assertThrows(IllegalArgumentException.class, () -> new PunishmentRequestAlertWorkerSettings(
                true,
                defaults.pollInterval(),
                0,
                defaults.directBatch(),
                defaults.reviewerBatch(),
                defaults.operationalBatch(),
                defaults.totalClaimLimit(),
                defaults.presentationLimit(),
                defaults.leaseDuration(),
                defaults.maximumAttempts(),
                defaults.retryBase(),
                defaults.retryMaximum(),
                defaults.joinDelay(),
                defaults.requestExpirationInterval(),
                defaults.intentExpirationInterval(),
                defaults.leaseReclaimInterval(),
                defaults.retentionInterval(),
                defaults.requestExpirationBatch(),
                defaults.intentExpirationBatch(),
                defaults.leaseReclaimBatch(),
                defaults.retentionBatch(),
                defaults.retentionDuration()
        ));
    }
}
