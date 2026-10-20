package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Reference-only StaffBot delivery ledger. There is intentionally no activation
 * method, Discord client, or sender in this class: only owner-approved future
 * code may move a new intent from INERT to ARMED.
 */
public final class JdbcStaffBotReportIntentStore {
    private static final int MAX_BATCH = 10;
    private static final int MAX_OWNER_LENGTH = 128;
    private final DataSource dataSource;

    public record ClaimedIntent(UUID intentId, UUID reportId, String eventType, int attemptCount) {
        public ClaimedIntent {
            Objects.requireNonNull(intentId, "intentId");
            Objects.requireNonNull(reportId, "reportId");
            Objects.requireNonNull(eventType, "eventType");
            if (attemptCount < 1) {
                throw new IllegalArgumentException("Claim count must be positive");
            }
        }
    }

    public JdbcStaffBotReportIntentStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Only explicitly ARMED entries can be leased. Expired LEASED rows are
     * never reclaimed automatically: Discord may already have accepted them.
     */
    public List<ClaimedIntent> claimArmed(String owner, int limit, Duration lease, Instant now) {
        validateClaim(owner, limit, lease, now);
        Instant until = now.plus(lease);
        return JdbcTransactionSupport.execute(
                dataSource, "Unable to lease private StaffBot report notifications",
                Connection.TRANSACTION_READ_COMMITTED,
                connection -> {
                    List<ClaimedIntent> chosen = selectArmed(connection, limit, now);
                    leaseSelected(connection, chosen, owner, until);
                    return List.copyOf(chosen);
                });
    }

    private static void validateClaim(String owner, int limit, Duration lease, Instant now) {
        validOwner(owner);
        Objects.requireNonNull(now, "now");
        if (limit < 1 || limit > MAX_BATCH || lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Invalid StaffBot claim arguments");
        }
    }

    private static List<ClaimedIntent> selectArmed(
            Connection connection, int limit, Instant now
    ) throws SQLException {
        List<ClaimedIntent> chosen = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT intent_id, report_id, event_type, attempts
                FROM staffbot_report_notification_intents
                WHERE state = 'ARMED' AND available_at <= ?
                ORDER BY available_at, created_at, intent_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """)) {
            query.setTimestamp(1, Timestamp.from(now));
            query.setInt(2, limit);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    chosen.add(readClaimedIntent(rows));
                }
            }
        }
        return chosen;
    }

    private static ClaimedIntent readClaimedIntent(ResultSet row) throws SQLException {
        return new ClaimedIntent(
                UuidBytes.fromBytes(row.getBytes("intent_id")),
                UuidBytes.fromBytes(row.getBytes("report_id")),
                row.getString("event_type"),
                row.getInt("attempts") + 1);
    }

    private static void leaseSelected(
            Connection connection, List<ClaimedIntent> selected, String owner, Instant until
    ) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE staffbot_report_notification_intents
                SET state = 'LEASED', lease_owner = ?, lease_expires_at = ?,
                    attempts = attempts + 1
                WHERE intent_id = ? AND state = 'ARMED'
                """)) {
            for (ClaimedIntent intent : selected) {
                update.setString(1, owner);
                update.setTimestamp(2, Timestamp.from(until));
                update.setBytes(3, UuidBytes.toBytes(intent.intentId()));
                JdbcTransactionSupport.requireSingleUpdate(update.executeUpdate(),
                        "StaffBot intent claim changed concurrently");
            }
        }
    }

    /**
     * Acknowledgements must match a current unexpired lease and its exact owner.
     * Discord's ambiguous timeouts must not call this method.
     */
    public boolean delivered(UUID intentId, String owner, Instant now) {
        Objects.requireNonNull(intentId, "intentId");
        validOwner(owner);
        Objects.requireNonNull(now, "now");
        return JdbcTransactionSupport.execute(dataSource,
                "Unable to mark private StaffBot report notification delivered",
                connection -> {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE staffbot_report_notification_intents
                            SET state = 'DELIVERED', delivered_at = ?,
                                lease_owner = NULL, lease_expires_at = NULL
                            WHERE intent_id = ? AND state = 'LEASED'
                              AND lease_owner = ? AND lease_expires_at > ?
                            """)) {
                        update.setTimestamp(1, Timestamp.from(now));
                        update.setBytes(2, UuidBytes.toBytes(intentId));
                        update.setString(3, owner);
                        update.setTimestamp(4, Timestamp.from(now));
                        return JdbcTransactionSupport.updatedOne(update.executeUpdate());
                    }
                });
    }

    /** Quarantine ambiguous/expired leases instead of duplicating Discord sends. */
    public int quarantineExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return JdbcTransactionSupport.execute(dataSource,
                "Unable to quarantine ambiguous StaffBot report notifications",
                connection -> {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE staffbot_report_notification_intents
                            SET state = 'REVIEW_REQUIRED',
                                lease_owner = NULL, lease_expires_at = NULL
                            WHERE state = 'LEASED' AND lease_expires_at <= ?
                            """)) {
                        update.setTimestamp(1, Timestamp.from(now));
                        return update.executeUpdate();
                    }
                });
    }

    /** Explicit quarantine after an uncertain send outcome; never retry blindly. */
    public boolean reviewRequired(UUID intentId, String owner) {
        Objects.requireNonNull(intentId, "intentId");
        validOwner(owner);
        return JdbcTransactionSupport.execute(dataSource,
                "Unable to quarantine private StaffBot report notification",
                connection -> {
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE staffbot_report_notification_intents
                            SET state = 'REVIEW_REQUIRED',
                                lease_owner = NULL, lease_expires_at = NULL
                            WHERE intent_id = ? AND state = 'LEASED' AND lease_owner = ?
                            """)) {
                        update.setBytes(1, UuidBytes.toBytes(intentId));
                        update.setString(2, owner);
                        return JdbcTransactionSupport.updatedOne(update.executeUpdate());
                    }
                });
    }

    private static void validOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > MAX_OWNER_LENGTH) {
            throw new IllegalArgumentException("A bounded StaffBot lease owner is required");
        }
    }
}
