package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperCrossPlatformPunishmentServiceTest {
    private static final UUID DRAFT =
            UUID.fromString("11111111-2222-4333-8444-555555555555");

    @Test
    void recoveryIdentifiersAreStableAcrossRetries() {
        assertEquals(
                PaperCrossPlatformPunishmentService.punishmentId(DRAFT, PaperPunishmentScope.BOTH),
                PaperCrossPlatformPunishmentService.punishmentId(DRAFT, PaperPunishmentScope.BOTH)
        );
        assertEquals(
                PaperCrossPlatformPunishmentService.caseId(DRAFT),
                PaperCrossPlatformPunishmentService.caseId(DRAFT)
        );
    }

    @Test
    void discordOnlyAndBothCannotCollide() {
        assertNotEquals(
                PaperCrossPlatformPunishmentService.punishmentId(DRAFT, PaperPunishmentScope.DISCORD),
                PaperCrossPlatformPunishmentService.punishmentId(DRAFT, PaperPunishmentScope.BOTH)
        );
    }

    @Test
    void deterministicCaseIdUsesCanonicalCaseFormat() {
        String value = PaperCrossPlatformPunishmentService.caseId(DRAFT).value();
        assertEquals(16, value.length());
        org.junit.jupiter.api.Assertions.assertTrue(value.matches("[0-9A-HJKMNP-TV-Z]{16}"));
    }
}
