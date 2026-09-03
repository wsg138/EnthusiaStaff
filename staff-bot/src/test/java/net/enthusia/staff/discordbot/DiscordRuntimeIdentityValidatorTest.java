package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DiscordRuntimeIdentityValidatorTest {
    @Test
    void acceptsExactPrivateStagingIdentity() {
        DiscordRuntimeIdentity identity = new DiscordRuntimeIdentity(
                StaffBotEnvironment.STAGING.applicationId(),
                false,
                Set.of(StaffBotEnvironment.STAGING.guildId()),
                true,
                true);

        DiscordRuntimeIdentityValidator.ValidationResult result =
                DiscordRuntimeIdentityValidator.validate(StaffBotEnvironment.STAGING, identity);

        assertTrue(result.valid());
        assertEquals(DiscordRuntimeIdentityValidator.READY, result.reason());
    }

    @Test
    void rejectsWrongApplicationPublicBotAndExtraGuild() {
        assertEquals(
                DiscordRuntimeIdentityValidator.APPLICATION_MISMATCH,
                DiscordRuntimeIdentityValidator.validate(
                                StaffBotEnvironment.STAGING,
                                new DiscordRuntimeIdentity(
                                        1L,
                                        false,
                                        Set.of(StaffBotEnvironment.STAGING.guildId()),
                                        true,
                                        true))
                        .reason());
        assertEquals(
                DiscordRuntimeIdentityValidator.APPLICATION_PUBLIC,
                DiscordRuntimeIdentityValidator.validate(
                                StaffBotEnvironment.STAGING,
                                new DiscordRuntimeIdentity(
                                        StaffBotEnvironment.STAGING.applicationId(),
                                        true,
                                        Set.of(StaffBotEnvironment.STAGING.guildId()),
                                        true,
                                        true))
                        .reason());
        assertEquals(
                DiscordRuntimeIdentityValidator.GUILD_SCOPE_MISMATCH,
                DiscordRuntimeIdentityValidator.validate(
                                StaffBotEnvironment.STAGING,
                                new DiscordRuntimeIdentity(
                                        StaffBotEnvironment.STAGING.applicationId(),
                                        false,
                                        Set.of(StaffBotEnvironment.STAGING.guildId(), 2L),
                                        true,
                                        true))
                        .reason());
    }

    @Test
    void stagingRequiresVisibleOperationalTestChannel() {
        assertEquals(
                DiscordRuntimeIdentityValidator.STAGING_CHANNEL_MISSING,
                DiscordRuntimeIdentityValidator.validate(
                                StaffBotEnvironment.STAGING,
                                new DiscordRuntimeIdentity(
                                        StaffBotEnvironment.STAGING.applicationId(),
                                        false,
                                        Set.of(StaffBotEnvironment.STAGING.guildId()),
                                        false,
                                        false))
                        .reason());
        assertEquals(
                DiscordRuntimeIdentityValidator.STAGING_CHANNEL_INACCESSIBLE,
                DiscordRuntimeIdentityValidator.validate(
                                StaffBotEnvironment.STAGING,
                                new DiscordRuntimeIdentity(
                                        StaffBotEnvironment.STAGING.applicationId(),
                                        false,
                                        Set.of(StaffBotEnvironment.STAGING.guildId()),
                                        true,
                                        false))
                        .reason());
    }

    @Test
    void productionDoesNotDependOnStagingTestChannel() {
        DiscordRuntimeIdentity identity = new DiscordRuntimeIdentity(
                StaffBotEnvironment.PRODUCTION.applicationId(),
                false,
                Set.of(StaffBotEnvironment.PRODUCTION.guildId()),
                false,
                false);

        assertTrue(DiscordRuntimeIdentityValidator.validate(StaffBotEnvironment.PRODUCTION, identity).valid());
    }
}
