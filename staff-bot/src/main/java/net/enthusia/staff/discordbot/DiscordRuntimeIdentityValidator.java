package net.enthusia.staff.discordbot;

import java.util.Objects;

/** Fail-closed validation for the bot token's actual application, guild, and staging-channel scope. */
public final class DiscordRuntimeIdentityValidator {
    public static final String READY = "identity_valid";
    public static final String APPLICATION_MISMATCH = "application_id_mismatch";
    public static final String APPLICATION_PUBLIC = "application_is_public";
    public static final String GUILD_SCOPE_MISMATCH = "guild_scope_mismatch";
    public static final String STAGING_CHANNEL_MISSING = "staging_channel_missing";
    public static final String STAGING_CHANNEL_INACCESSIBLE = "staging_channel_inaccessible";

    private DiscordRuntimeIdentityValidator() {
    }

    public static ValidationResult validate(StaffBotEnvironment environment, DiscordRuntimeIdentity identity) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(identity, "identity");
        if (identity.applicationId() != environment.applicationId()) {
            return ValidationResult.failure(APPLICATION_MISMATCH);
        }
        if (identity.botPublic()) {
            return ValidationResult.failure(APPLICATION_PUBLIC);
        }
        if (identity.guildIds().size() != 1 || !identity.guildIds().contains(environment.guildId())) {
            return ValidationResult.failure(GUILD_SCOPE_MISMATCH);
        }
        if (environment == StaffBotEnvironment.STAGING) {
            if (!identity.stagingChannelPresent()) {
                return ValidationResult.failure(STAGING_CHANNEL_MISSING);
            }
            if (!identity.stagingChannelOperational()) {
                return ValidationResult.failure(STAGING_CHANNEL_INACCESSIBLE);
            }
        }
        return ValidationResult.success();
    }

    public record ValidationResult(boolean valid, String reason) {
        public ValidationResult {
            Objects.requireNonNull(reason, "reason");
        }

        public static ValidationResult success() {
            return new ValidationResult(true, READY);
        }

        public static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
