package net.enthusia.staff.domain.auth;

import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.actor;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.discord;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.issue;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.operation;
import static net.enthusia.staff.domain.auth.DiscordAuthorizationTestFixtures.service;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordModerationOperationMatrixTest {
    @Test
    void operationMatrixIsExplicitForEveryRank() {
        DiscordModerationAuthorizationService service = service();
        Map<StaffRank, Set<DiscordModerationOperation>> expected = expectedOperations();
        for (StaffRank rank : StaffRank.values()) {
            for (DiscordModerationOperation operation : DiscordModerationOperation.values()) {
                DiscordAuthorizationRequest request = requestFor(operation);
                assertEquals(
                        expected.get(rank).contains(operation),
                        service.authorize(actor(rank), Optional.empty(), request).permitted(),
                        rank + " / " + operation
                );
            }
        }
    }

    private static DiscordAuthorizationRequest requestFor(DiscordModerationOperation operation) {
        return operation == DiscordModerationOperation.ISSUE_SANCTION
                ? issue(discord(DiscordConsequenceType.WARNING, SanctionLength.instant(), false, false))
                : operation(operation, ModerationPlatform.DISCORD);
    }

    private static Map<StaffRank, Set<DiscordModerationOperation>> expectedOperations() {
        Set<DiscordModerationOperation> reads = EnumSet.of(
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                DiscordModerationOperation.VIEW_HISTORY,
                DiscordModerationOperation.VIEW_NOTES,
                DiscordModerationOperation.VIEW_EVIDENCE
        );
        Map<StaffRank, Set<DiscordModerationOperation>> expected = new EnumMap<>(StaffRank.class);
        expected.put(StaffRank.HELPER, with(reads, DiscordModerationOperation.ISSUE_SANCTION));
        expected.put(StaffRank.MOD, moderatorOperations(reads));
        expected.put(StaffRank.DEVELOPER, moderatorOperations(reads));
        expected.put(StaffRank.ADMIN, EnumSet.allOf(DiscordModerationOperation.class));
        expected.put(StaffRank.FOUNDER, EnumSet.allOf(DiscordModerationOperation.class));
        expected.put(StaffRank.SYSTEM, EnumSet.noneOf(DiscordModerationOperation.class));
        return expected;
    }

    private static Set<DiscordModerationOperation> moderatorOperations(Set<DiscordModerationOperation> reads) {
        return with(
                reads,
                DiscordModerationOperation.ISSUE_SANCTION,
                DiscordModerationOperation.END_SANCTION,
                DiscordModerationOperation.REVOKE_SANCTION,
                DiscordModerationOperation.APPROVE_SANCTION_REQUEST,
                DiscordModerationOperation.REQUEST_OVERTURN
        );
    }

    private static Set<DiscordModerationOperation> with(
            Set<DiscordModerationOperation> base,
            DiscordModerationOperation... additions
    ) {
        EnumSet<DiscordModerationOperation> result = EnumSet.copyOf(base);
        result.addAll(java.util.List.of(additions));
        return result;
    }
}
