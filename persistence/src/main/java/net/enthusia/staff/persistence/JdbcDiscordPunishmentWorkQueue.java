package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkLease;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkSchedule;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.WorkType;

final class JdbcDiscordPunishmentWorkQueue {
    private static final int MAX_BATCH = 100;
    private static final String PREFIX = "D07_";

    List<WorkLease> claimDue(
            Connection connection,
            Instant now,
            int limit,
            String leaseOwner,
            Instant leaseUntil
    ) throws SQLException {
        validateClaim(now, limit, leaseOwner, leaseUntil);
        List<Candidate> candidates = dueRows(connection, now, limit);
        List<WorkLease> claimed = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            claimed.add(claim(connection, candidate, now, leaseOwner, leaseUntil));
        }
        return List.copyOf(claimed);
    }

    void finishAndSchedule(
            Connection connection,
            WorkLease supplied,
            List<WorkSchedule> schedules,
            Instant now
    ) throws SQLException {
        validateSchedules(schedules);
        WorkLease current = claimedRow(connection, supplied.workId());
        requireLease(current, supplied);
        WorkSchedule sameType = schedules.stream()
                .filter(schedule -> schedule.type() == supplied.type())
                .findFirst()
                .orElse(null);
        if (sameType == null) {
            completeCurrent(connection, current, now);
        } else {
            rescheduleCurrent(connection, current, sameType.dueAt(), now);
        }
        for (WorkSchedule schedule : schedules) {
            if (schedule.type() != supplied.type()) {
                upsert(connection, supplied.punishmentId(), schedule, now);
            }
        }
    }

    void upsert(Connection connection, UUID punishmentId, WorkSchedule schedule, Instant now) throws SQLException {
        if (punishmentId == null || schedule == null || now == null) {
            throw new IllegalArgumentException("D07 work schedule fields must be present");
        }
        WorkRow current = workByResource(connection, schedule.type(), punishmentId, true);
        if (current == null) {
            insert(connection, punishmentId, schedule, now);
            return;
        }
        if ("CLAIMED".equals(current.state())) {
            return;
        }
        Instant dueAt = current.dueAt().isBefore(schedule.dueAt()) ? current.dueAt() : schedule.dueAt();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_maintenance_work
                SET due_at = ?, state = 'PENDING', lease_owner = NULL, lease_until = NULL,
                    last_error_code = NULL, revision = revision + 1, updated_at = ?
                WHERE work_id = ? AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(dueAt));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(current.workId()));
            statement.setLong(4, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 work reschedule lost revision race");
        }
    }

    private List<Candidate> dueRows(Connection connection, Instant now, int limit) throws SQLException {
        List<Candidate> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT work_id, work_type, resource_key, due_at, attempt_count, revision
                FROM discord_maintenance_work
                WHERE work_type IN ('D07_APPLY', 'D07_REMOVE', 'D07_RECONCILE')
                  AND due_at <= ?
                  AND (state = 'PENDING' OR (state = 'CLAIMED' AND lease_until <= ?))
                ORDER BY due_at, work_id
                LIMIT ?
                FOR UPDATE
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(readCandidate(rows));
                }
            }
        }
        return result;
    }

    private WorkLease claim(
            Connection connection,
            Candidate candidate,
            Instant now,
            String leaseOwner,
            Instant leaseUntil
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_maintenance_work
                SET state = 'CLAIMED', lease_owner = ?, lease_until = ?,
                    attempt_count = attempt_count + 1, revision = revision + 1, updated_at = ?
                WHERE work_id = ? AND revision = ?
                """)) {
            statement.setString(1, leaseOwner);
            statement.setTimestamp(2, Timestamp.from(leaseUntil));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(candidate.workId()));
            statement.setLong(5, candidate.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 work claim lost revision race");
        }
        return new WorkLease(
                candidate.workId(), candidate.type(), candidate.punishmentId(), candidate.dueAt(),
                leaseOwner, leaseUntil, candidate.attemptCount() + 1, candidate.revision() + 1
        );
    }

    private WorkLease claimedRow(Connection connection, UUID workId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT work_id, work_type, resource_key, due_at, lease_owner, lease_until,
                       attempt_count, revision
                FROM discord_maintenance_work
                WHERE work_id = ? AND state = 'CLAIMED'
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(workId));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readClaimed(rows) : null;
            }
        }
    }

    private static void requireLease(WorkLease current, WorkLease supplied) throws SQLException {
        if (current == null
                || current.revision() != supplied.revision()
                || !current.leaseOwner().equals(supplied.leaseOwner())
                || current.type() != supplied.type()
                || !current.punishmentId().equals(supplied.punishmentId())) {
            throw new SQLException("D07 work lease changed before settlement");
        }
    }

    private void completeCurrent(Connection connection, WorkLease current, Instant now) throws SQLException {
        updateClaimedState(connection, current, "COMPLETE", current.dueAt(), now);
    }

    private void rescheduleCurrent(
            Connection connection,
            WorkLease current,
            Instant dueAt,
            Instant now
    ) throws SQLException {
        updateClaimedState(connection, current, "PENDING", dueAt, now);
    }

    private void updateClaimedState(
            Connection connection,
            WorkLease current,
            String state,
            Instant dueAt,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_maintenance_work
                SET due_at = ?, state = ?, lease_owner = NULL, lease_until = NULL,
                    last_error_code = NULL, revision = revision + 1, updated_at = ?
                WHERE work_id = ? AND revision = ? AND state = 'CLAIMED' AND lease_owner = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(dueAt));
            statement.setString(2, state);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setBytes(4, UuidBytes.toBytes(current.workId()));
            statement.setLong(5, current.revision());
            statement.setString(6, current.leaseOwner());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 work settlement lost lease");
        }
    }

    private WorkRow workByResource(
            Connection connection,
            WorkType type,
            UUID punishmentId,
            boolean lock
    ) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT work_id, due_at, state, revision
                FROM discord_maintenance_work
                WHERE work_type = ? AND resource_key = ?
                """ + suffix)) {
            statement.setString(1, databaseType(type));
            statement.setString(2, punishmentId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new WorkRow(
                        UuidBytes.fromBytes(rows.getBytes("work_id")),
                        rows.getTimestamp("due_at").toInstant(),
                        rows.getString("state"),
                        rows.getLong("revision")
                );
            }
        }
    }

    private void insert(
            Connection connection,
            UUID punishmentId,
            WorkSchedule schedule,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_maintenance_work(
                    work_id, work_type, resource_key, due_at, state,
                    lease_owner, lease_until, attempt_count, last_error_code,
                    revision, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', NULL, NULL, 0, NULL, 0, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(UUID.randomUUID()));
            statement.setString(2, databaseType(schedule.type()));
            statement.setString(3, punishmentId.toString());
            statement.setTimestamp(4, Timestamp.from(schedule.dueAt()));
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "D07 work was not inserted");
        }
    }

    private static Candidate readCandidate(ResultSet rows) throws SQLException {
        return new Candidate(
                UuidBytes.fromBytes(rows.getBytes("work_id")),
                domainType(rows.getString("work_type")),
                UUID.fromString(rows.getString("resource_key")),
                rows.getTimestamp("due_at").toInstant(),
                rows.getInt("attempt_count"),
                rows.getLong("revision")
        );
    }

    private static WorkLease readClaimed(ResultSet rows) throws SQLException {
        return new WorkLease(
                UuidBytes.fromBytes(rows.getBytes("work_id")),
                domainType(rows.getString("work_type")),
                UUID.fromString(rows.getString("resource_key")),
                rows.getTimestamp("due_at").toInstant(),
                rows.getString("lease_owner"),
                rows.getTimestamp("lease_until").toInstant(),
                rows.getInt("attempt_count"),
                rows.getLong("revision")
        );
    }

    private static void validateClaim(Instant now, int limit, String owner, Instant leaseUntil) {
        requireLeaseInterval(now, leaseUntil);
        requireClaimLimit(limit);
        requireLeaseOwner(owner);
    }

    private static void requireLeaseInterval(Instant now, Instant leaseUntil) {
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("D07 lease interval is invalid");
        }
    }

    private static void requireClaimLimit(int limit) {
        if (limit < 1 || limit > MAX_BATCH) {
            throw new IllegalArgumentException("D07 work claim limit must be between 1 and 100");
        }
    }

    private static void requireLeaseOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 96) {
            throw new IllegalArgumentException("D07 lease owner is invalid");
        }
    }

    private static void validateSchedules(List<WorkSchedule> schedules) {
        if (schedules == null) {
            throw new IllegalArgumentException("work schedules must be present");
        }
        EnumSet<WorkType> seen = EnumSet.noneOf(WorkType.class);
        for (WorkSchedule schedule : schedules) {
            if (schedule == null || !seen.add(schedule.type())) {
                throw new IllegalArgumentException("work schedules must contain unique types");
            }
        }
    }

    private static String databaseType(WorkType type) {
        return PREFIX + type.name();
    }

    private static WorkType domainType(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("not a D07 work type");
        }
        return WorkType.valueOf(value.substring(PREFIX.length()));
    }

    private record Candidate(
            UUID workId,
            WorkType type,
            UUID punishmentId,
            Instant dueAt,
            int attemptCount,
            long revision
    ) {
    }

    private record WorkRow(UUID workId, Instant dueAt, String state, long revision) {
    }
}
