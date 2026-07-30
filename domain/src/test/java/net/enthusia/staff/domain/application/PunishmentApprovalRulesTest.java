package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class PunishmentApprovalRulesTest {
    private static final List<SanctionSpec> TEMPORARY = List.of(new SanctionSpec(
            SanctionType.MUTE,
            SanctionLength.temporary(Duration.ofHours(1))
    ));
    private static final List<SanctionSpec> PERMANENT = List.of(new SanctionSpec(
            SanctionType.NETWORK_BAN,
            SanctionLength.permanent()
    ));

    @Test
    void developerActionsAlwaysRequireApproval() {
        assertTrue(PunishmentApprovalRules.requiresApproval(StaffRank.DEVELOPER, TEMPORARY));
    }

    @Test
    void helperActionsOnlyRequireApprovalForPermanentSanctions() {
        assertFalse(PunishmentApprovalRules.requiresApproval(StaffRank.HELPER, TEMPORARY));
        assertTrue(PunishmentApprovalRules.requiresApproval(StaffRank.HELPER, PERMANENT));
    }

    @Test
    void moderatorAndHigherActionsDoNotRequireApproval() {
        assertFalse(PunishmentApprovalRules.requiresApproval(StaffRank.MOD, PERMANENT));
        assertFalse(PunishmentApprovalRules.requiresApproval(StaffRank.ADMIN, PERMANENT));
        assertFalse(PunishmentApprovalRules.requiresApproval(StaffRank.FOUNDER, PERMANENT));
    }
}
