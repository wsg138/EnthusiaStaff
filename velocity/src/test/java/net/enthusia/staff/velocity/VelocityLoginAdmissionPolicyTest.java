package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.OperationalMode;
import org.junit.jupiter.api.Test;

class VelocityLoginAdmissionPolicyTest {
    @Test
    void maintenanceStillBlocksLoginsAfterRestartBeforeAnyActiveAuthorityWasObserved() {
        assertTrue(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.MAINTENANCE, false, true));
    }

    @Test
    void maintenanceFenceCannotBeDisabledByLegacyFailureConfiguration() {
        assertTrue(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.MAINTENANCE, false, false));
    }

    @Test
    void startupCannotAdmitPlayersBeforeStorageAndAuthorityHaveBeenVerified() {
        assertTrue(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.BOOTSTRAP, false, true));
    }

    @Test
    void shadowMigrationLeavesLegacyLoginAuthorityInPlace() {
        assertFalse(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.SHADOW_MIGRATION, false, true));
    }

    @Test
    void lostActiveAuthorityRetainsTheExistingFailClosedPolicy() {
        assertTrue(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.DEGRADED, true, true));
        assertTrue(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.READ_ONLY_FAILURE, true, true));
        assertFalse(VelocityLoginAdmissionPolicy.blocksInactiveLogin(OperationalMode.DEGRADED, true, false));
    }
}
