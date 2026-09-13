package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.dv8tion.jda.api.requests.ErrorResponse;
import org.junit.jupiter.api.Test;

class ModerationReadRequestAuthorizerTest {
    @Test
    void missingGuildActorResponsesAreAuthorizationRejections() {
        assertTrue(ModerationReadRequestAuthorizer.missingActor(ErrorResponse.UNKNOWN_MEMBER));
        assertTrue(ModerationReadRequestAuthorizer.missingActor(ErrorResponse.UNKNOWN_USER));
    }

    @Test
    void unrelatedDiscordFailuresRemainSourceFailures() {
        assertFalse(ModerationReadRequestAuthorizer.missingActor(ErrorResponse.MISSING_PERMISSIONS));
    }

    @Test
    void malformedActorSnowflakesAreRejectedBeforeDiscordLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> ModerationReadRequestAuthorizer.snowflake("0", "actor"));
        assertThrows(IllegalArgumentException.class,
                () -> ModerationReadRequestAuthorizer.snowflake("not-a-snowflake", "actor"));
    }
}
