package net.enthusia.staff.discordbot;

import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentState;

/** Durable retry boundary for one-shot Discord kick effects. */
final class DiscordKickRetryPolicy {
    static final String PRE_EFFECT_RETRY = "KICK_PRE_EFFECT_RETRY";
    static final String RESULT_AMBIGUOUS = "KICK_RESULT_AMBIGUOUS";
    static final String VERIFY_RETRY = "KICK_VERIFY_RETRY";
    private static final int FIRST_ATTEMPT = 1;

    private DiscordKickRetryPolicy() {
    }

    static boolean isKick(DiscordPunishment punishment) {
        return punishment.intent().type() == DiscordConsequenceType.KICK;
    }

    static boolean mayDispatch(DiscordPunishment punishment, int attemptCount) {
        if (!isKick(punishment) || attemptCount < FIRST_ATTEMPT) {
            throw new IllegalArgumentException("kick retry policy requires a kick and positive attempt count");
        }
        if (attemptCount == FIRST_ATTEMPT) {
            return true;
        }
        return punishment.state() == DiscordPunishmentState.RETRY_APPLY
                && punishment.lastErrorCode().filter(PRE_EFFECT_RETRY::equals).isPresent();
    }

    static boolean verificationOnly(DiscordPunishment punishment, int attemptCount) {
        return isKick(punishment) && !mayDispatch(punishment, attemptCount);
    }

    static boolean retryAmbiguityOnce(
            DiscordPunishment punishment,
            DiscordPunishmentGateway.EffectException failure
    ) {
        if (!isKick(punishment) || !RESULT_AMBIGUOUS.equals(failure.errorCode())) {
            return false;
        }
        if (punishment.lastErrorCode().isEmpty()) {
            return true;
        }
        return punishment.lastErrorCode().filter(RESULT_AMBIGUOUS::equals).isPresent()
                && retryableVerificationCause(failure);
    }

    static String durableFailureCode(
            DiscordPunishment punishment,
            DiscordPunishmentGateway.EffectException failure
    ) {
        if (isKick(punishment)
                && RESULT_AMBIGUOUS.equals(failure.errorCode())
                && retryableVerificationCause(failure)
                && punishment.lastErrorCode().filter(RESULT_AMBIGUOUS::equals).isPresent()) {
            return VERIFY_RETRY;
        }
        if (isKick(punishment) && failure.retryable()) {
            return PRE_EFFECT_RETRY;
        }
        return failure.errorCode();
    }

    private static boolean retryableVerificationCause(DiscordPunishmentGateway.EffectException failure) {
        return failure.getCause() instanceof DiscordPunishmentGateway.EffectException cause && cause.retryable();
    }
}
