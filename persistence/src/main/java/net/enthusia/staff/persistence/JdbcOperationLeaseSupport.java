package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

final class JdbcOperationLeaseSupport {
    static final long UNAVAILABLE = 0L;
    private static final long INITIAL_FENCING_TOKEN = 1L;

    private JdbcOperationLeaseSupport() {
    }

    static long acquire(
            Connection connection,
            String resourceKey,
            UUID operationId,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT owner_id, fencing_token, lease_until
                FROM operation_leases
                WHERE resource_key = ?
                FOR UPDATE
                """)) {
            select.setString(1, resourceKey);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    insert(connection, resourceKey, operationId, leaseUntil, now);
                    return INITIAL_FENCING_TOKEN;
                }
                String ownerId = operationId.toString();
                Instant currentExpiry = result.getTimestamp("lease_until").toInstant();
                if (currentExpiry.isAfter(now) && !result.getString("owner_id").equals(ownerId)) {
                    return UNAVAILABLE;
                }
                long nextFence = nextFencingToken(result.getLong("fencing_token"));
                replace(connection, resourceKey, ownerId, nextFence, leaseUntil, now);
                return nextFence;
            }
        }
    }

    private static void insert(
            Connection connection,
            String resourceKey,
            UUID operationId,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO operation_leases(
                    resource_key, owner_id, fencing_token, lease_until, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, resourceKey);
            statement.setString(2, operationId.toString());
            statement.setLong(3, INITIAL_FENCING_TOKEN);
            statement.setTimestamp(4, Timestamp.from(leaseUntil));
            statement.setTimestamp(5, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Operation lease was not created"
            );
        }
    }

    private static void replace(
            Connection connection,
            String resourceKey,
            String ownerId,
            long fencingToken,
            Instant leaseUntil,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE operation_leases
                SET owner_id = ?, fencing_token = ?, lease_until = ?, updated_at = ?
                WHERE resource_key = ?
                """)) {
            statement.setString(1, ownerId);
            statement.setLong(2, fencingToken);
            statement.setTimestamp(3, Timestamp.from(leaseUntil));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.setString(5, resourceKey);
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "Operation lease disappeared during acquisition"
            );
        }
    }

    private static long nextFencingToken(long currentToken) throws SQLException {
        if (currentToken == Long.MAX_VALUE) {
            throw new SQLException("Operation lease fencing token is exhausted");
        }
        return currentToken + 1L;
    }
}
