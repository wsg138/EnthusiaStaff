package net.enthusia.staff.domain.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class PunishmentMatchKeyTest {
    private static final UUID TARGET = UUID.fromString("51000000-0000-0000-0000-000000000001");

    @Test
    void sanctionOrderDoesNotChangeTheMatchKey() {
        SanctionSpec ban = new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(7))
        );
        SanctionSpec mute = new SanctionSpec(
                SanctionType.MUTE,
                SanctionLength.temporary(Duration.ofDays(30))
        );

        assertEquals(
                PunishmentMatchKey.of(TARGET, "chat.abuse", List.of(ban, mute)),
                PunishmentMatchKey.of(TARGET, "chat.abuse", List.of(mute, ban))
        );
    }

    @Test
    void targetReasonTypeAndDurationRemainPartOfTheMatch() {
        SanctionSpec sevenDays = new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(7))
        );
        SanctionSpec thirtyDays = new SanctionSpec(
                SanctionType.NETWORK_BAN,
                SanctionLength.temporary(Duration.ofDays(30))
        );

        PunishmentMatchKey original = PunishmentMatchKey.of(TARGET, "chat.abuse", List.of(sevenDays));
        assertNotEquals(original, PunishmentMatchKey.of(UUID.randomUUID(), "chat.abuse", List.of(sevenDays)));
        assertNotEquals(original, PunishmentMatchKey.of(TARGET, "chat.spam", List.of(sevenDays)));
        assertNotEquals(original, PunishmentMatchKey.of(TARGET, "chat.abuse", List.of(thirtyDays)));
        assertNotEquals(original, PunishmentMatchKey.of(
                TARGET,
                "chat.abuse",
                List.of(new SanctionSpec(SanctionType.MUTE, SanctionLength.temporary(Duration.ofDays(7))))
        ));
    }
}
