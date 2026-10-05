package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class JdbcWebsiteAppealLifecycleSupport {
    private JdbcWebsiteAppealLifecycleSupport() {
    }

    static void validateEdit(
            UUID appealId,
            long expectedVersion,
            String accountId,
            String reason,
            String idempotencyKey,
            Instant now
    ) {
        if (appealId == null || expectedVersion < 1 || !validLength(accountId, 1, 128)
                || !validLength(reason, 10, 1_000) || !validIdempotencyKey(idempotencyKey)
                || now == null) {
            throw invalid("INVALID_APPEAL_EDIT", "The appeal edit request is invalid");
        }
    }

    static void validateReviewerMutation(
            UUID appealId,
            long expectedVersion,
            UUID reviewerAccountId,
            String reviewerRank,
            String idempotencyKey,
            Instant now,
            boolean reopen
    ) {
        boolean validRank = reopen
                ? List.of("ADMIN", "FOUNDER").contains(reviewerRank)
                : List.of("MOD", "ADMIN", "FOUNDER").contains(reviewerRank);
        if (appealId == null || expectedVersion < 1 || reviewerAccountId == null
                || !validRank || !validIdempotencyKey(idempotencyKey) || now == null) {
            throw invalid("INVALID_APPEAL_REVIEW_ACTION", "The appeal review action is invalid");
        }
    }

    static boolean validLength(String value, int minimum, int maximum) {
        return value != null && !value.isBlank()
                && value.length() >= minimum && value.length() <= maximum;
    }

    static WebsiteModerationException invalid(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INVALID, code, message);
    }

    static WebsiteModerationException notFound(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.NOT_FOUND, code, message);
    }

    static WebsiteModerationException conflict(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.CONFLICT, code, message);
    }

    static ModerationPersistenceException persistence(String message, Exception exception) {
        return exception instanceof ModerationPersistenceException persistenceException
                ? persistenceException
                : new ModerationPersistenceException(message, exception);
    }

    private static boolean validIdempotencyKey(String value) {
        return value != null && value.length() >= 8 && value.length() <= 128
                && value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }
}
