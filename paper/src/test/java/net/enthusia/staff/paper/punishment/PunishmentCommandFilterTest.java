package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.escalation.AltInheritanceMode;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentCommandFilterTest {
    @Test
    void banFilterRecognizesLocalAndNetworkBans() {
        assertTrue(PunishmentCommandFilter.matches("ban", List.of(temporary(SanctionType.BAN))));
        assertTrue(PunishmentCommandFilter.matches("ban", List.of(temporary(SanctionType.NETWORK_BAN))));
        assertFalse(PunishmentCommandFilter.matches("ban", List.of(temporary(SanctionType.MUTE))));
    }

    @Test
    void ipBanFilterIsLimitedToNetworkIdentityBan() {
        assertTrue(PunishmentCommandFilter.matches(
                "ipban", List.of(temporary(SanctionType.NETWORK_IDENTITY_BAN))
        ));
        assertFalse(PunishmentCommandFilter.matches(
                "ipban", List.of(temporary(SanctionType.NETWORK_BAN))
        ));
    }

    @Test
    void centralPunishFlowIncludesEveryConfiguredSanctionType() {
        assertTrue(PunishmentCommandFilter.includes("punish", policy(SanctionType.WARNING)));
        assertTrue(PunishmentCommandFilter.includes("punish", policy(SanctionType.NETWORK_BAN)));
    }

    private static ReasonPolicy policy(SanctionType type) {
        return new ReasonPolicy(
                "test.reason",
                "test",
                "Test reason",
                10,
                true,
                List.of(new PunishmentStep(0, "Test step", List.of(spec(type)))),
                List.of(),
                true,
                true,
                false,
                StaffRank.MOD,
                false,
                AltInheritanceMode.ACTIVE_SANCTIONS
        );
    }

    private static SanctionSpec spec(SanctionType type) {
        return switch (type) {
            case WARNING, KICK, CONTENT_REMOVAL, STALL_OWNERSHIP_REMOVAL,
                    INVENTORY_CONFISCATION, ENDER_CHEST_CONFISCATION, ECONOMY_CONFISCATION ->
                    new SanctionSpec(type, SanctionLength.instant());
            default -> temporary(type);
        };
    }

    private static SanctionSpec temporary(SanctionType type) {
        return new SanctionSpec(type, SanctionLength.temporary(Duration.ofDays(1)));
    }
}
