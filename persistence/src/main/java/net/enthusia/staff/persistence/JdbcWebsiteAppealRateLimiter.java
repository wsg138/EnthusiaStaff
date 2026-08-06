package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class JdbcWebsiteAppealRateLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int LIMIT = 3;

    private final DataSource dataSource;
    private final PunishmentCodeProtector codeProtector;

    JdbcWebsiteAppealRateLimiter(DataSource dataSource, PunishmentCodeProtector codeProtector) {
        if (dataSource == null || codeProtector == null) {
            throw new IllegalArgumentException("Website appeal rate limiter dependencies are required");
        }
        this.dataSource = dataSource;
        this.codeProtector = codeProtector;
    }

    void enforce(String accountId, String idempotencyKey, Instant now) {
        if (accountId == null || accountId.isBlank() || accountId.length() > 128
                || idempotencyKey == null || idempotencyKey.length() < 8
                || idempotencyKey.length() > 128 || now == null) {
            throw invalid();
        }
        byte[] accountToken;
        try {
            accountToken = codeProtector.accountToken(accountId);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        JdbcTransactionSupport.execute(
                dataSource,
                "Unable to enforce the website appeal rate limit",
                connection -> {
                    enforce(connection, accountToken, idempotencyKey, now);
                    return null;
                }
        );
    }

    private static void enforce(
            Connection connection,
            byte[] accountToken,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        ensureBucket(connection, accountToken, now);
        Bucket bucket = lockBucket(connection, accountToken);
        if (hasReplayKey(connection, accountToken, idempotencyKey)) {
            return;
        }
        if (!bucket.windowStartedAt().plus(WINDOW).isAfter(now)) {
            resetBucket(connection, accountToken, now);
            bucket = new Bucket(now, 0);
        }
        if (bucket.submissionCount() >= LIMIT) {
            throw new WebsiteModerationException(
                    WebsiteModerationException.Kind.RATE_LIMITED,
                    "APPEAL_RATE_LIMITED",
                    "Too many appeal submissions were made for this account"
            );
        }
        incrementBucket(connection, accountToken, now);
        insertReplayKey(connection, accountToken, idempotencyKey, now);
    }

    private static void ensureBucket(Connection connection, byte[] accountToken, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT IGNORE INTO website_appeal_rate_buckets(
                    account_token, window_started_at, submission_count, updated_at
                ) VALUES (?, ?, 0, ?)
                """)) {
            statement.setBytes(1, accountToken);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    private static Bucket lockBucket(Connection connection, byte[] accountToken)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT window_started_at, submission_count
                FROM website_appeal_rate_buckets
                WHERE account_token = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, accountToken);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Website appeal rate bucket is unavailable");
                }
                return new Bucket(
                        result.getTimestamp("window_started_at").toInstant(),
                        result.getInt("submission_count")
                );
            }
        }
    }

    private static boolean hasReplayKey(
            Connection connection,
            byte[] accountToken,
            String idempotencyKey
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM website_appeal_rate_keys
                WHERE account_token = ? AND idempotency_key = ?
                """)) {
            statement.setBytes(1, accountToken);
            statement.setString(2, idempotencyKey);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void resetBucket(Connection connection, byte[] accountToken, Instant now)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("""
                DELETE FROM website_appeal_rate_keys WHERE account_token = ?
                """);
             PreparedStatement update = connection.prepareStatement("""
                UPDATE website_appeal_rate_buckets
                SET window_started_at = ?, submission_count = 0, updated_at = ?
                WHERE account_token = ?
                """)) {
            delete.setBytes(1, accountToken);
            delete.executeUpdate();
            update.setTimestamp(1, Timestamp.from(now));
            update.setTimestamp(2, Timestamp.from(now));
            update.setBytes(3, accountToken);
            JdbcTransactionSupport.requireSingleUpdate(
                    update.executeUpdate(),
                    "Website appeal rate bucket was not reset"
            );
        }
    }

    private static void incrementBucket(Connection connection, byte[] accountToken, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE website_appeal_rate_buckets
                SET submission_count = submission_count + 1, updated_at = ?
                WHERE account_token = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, accountToken);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal rate bucket was not incremented"
            );
        }
    }

    private static void insertReplayKey(
            Connection connection,
            byte[] accountToken,
            String idempotencyKey,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO website_appeal_rate_keys(
                    account_token, idempotency_key, created_at
                ) VALUES (?, ?, ?)
                """)) {
            statement.setBytes(1, accountToken);
            statement.setString(2, idempotencyKey);
            statement.setTimestamp(3, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Website appeal rate replay key was not inserted"
            );
        }
    }

    private static WebsiteModerationException invalid() {
        return new WebsiteModerationException(
                WebsiteModerationException.Kind.INVALID,
                "INVALID_APPEAL_RATE_LIMIT",
                "The appeal rate-limit request is invalid"
        );
    }

    private record Bucket(Instant windowStartedAt, int submissionCount) {
    }
}
