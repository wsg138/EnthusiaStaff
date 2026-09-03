package net.enthusia.staff.domain.moderation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class AccountLinkAuditTest {
    @Test
    void rejectsActorNamesThatCannotFitAuditStorage() {
        Actor actor = new Actor(UUID.randomUUID(), "x".repeat(65), StaffRank.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> new AccountLinkAudit(
                "d04-audit-name-limit",
                actor,
                AccountLinkAuditAction.FORCE_LINK,
                Optional.of(new DiscordUserId("100000000000000001")),
                Optional.of(UUID.randomUUID()),
                "test audit",
                Instant.parse("2026-08-23T21:00:00Z")
        ));
    }
}
