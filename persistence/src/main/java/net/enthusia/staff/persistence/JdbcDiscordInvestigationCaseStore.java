package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.CaseActivity;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.InvestigationCaseDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.PunishmentCaseDraft;

final class JdbcDiscordInvestigationCaseStore {
    private final DataSource dataSource;

    JdbcDiscordInvestigationCaseStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    InvestigationCase ensurePunishmentCase(PunishmentCaseDraft draft) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to reconcile Discord punishment case", connection -> {
            Current current = byPunishment(connection, draft.punishmentId(), true);
            if (current == null) {
                return insertPunishmentCase(connection, draft);
            }
            requireSubject(current, draft.subjectId());
            if (draft.punishmentRevision() <= current.sourceRevision()) {
                return current.toDomain(true);
            }
            return updatePunishmentObservation(connection, current, draft);
        });
    }

    InvestigationCase createInvestigationCase(InvestigationCaseDraft draft) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to create Discord investigation case", connection -> {
            Current replay = byOperation(connection, draft.operationKey(), true);
            if (replay != null) {
                requireInvestigationReplay(replay, draft);
                return replay.toDomain(true);
            }
            insertInvestigationCase(connection, draft);
            return requireById(connection, draft.caseId(), false).toDomain(false);
        });
    }

    Optional<InvestigationCase> findCase(UUID caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord investigation case", connection ->
                Optional.ofNullable(byId(connection, caseId, false)).map(current -> current.toDomain(false)));
    }

    InvestigationCase touchCase(CaseActivity activity) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to touch Discord investigation case", connection -> {
            Current current = requireById(connection, activity.caseId(), true);
            requireOpenRevision(current, activity.expectedRevision());
            Instant lastActivity = later(current.lastActivityAt(), activity.occurredAt());
            updateActivity(connection, current.caseId(), current.revision(), lastActivity);
            return requireById(connection, current.caseId(), false).toDomain(false);
        });
    }

    int closeInactiveCases(Instant inactivityCutoff, Instant now, int limit) {
        if (inactivityCutoff == null || now == null || now.isBefore(inactivityCutoff) || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("inactive case close request is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to close inactive Discord cases", connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_investigation_cases
                    SET state = 'CLOSED', closed_at = ?, revision = revision + 1
                    WHERE state = 'OPEN' AND last_activity_at <= ?
                      AND (source = 'INVESTIGATION' OR punishment_ended_at IS NOT NULL)
                    ORDER BY last_activity_at, case_id
                    LIMIT ?
                    """)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(inactivityCutoff));
                statement.setInt(3, limit);
                return statement.executeUpdate();
            }
        });
    }

    private InvestigationCase insertPunishmentCase(Connection connection, PunishmentCaseDraft draft) throws SQLException {
        UUID caseId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_cases(
                    case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                    summary, state, opened_by, opened_at, last_activity_at, closed_at,
                    punishment_ended_at, source_revision, revision
                ) VALUES (?, ?, ?, 'DISCORD_PUNISHMENT', ?, NULL, ?, 'OPEN', ?, ?, ?, NULL, ?, ?, 0)
                """)) {
            bindPunishmentInsert(statement, caseId, draft);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "punishment case was not inserted");
        }
        return requireById(connection, caseId, false).toDomain(false);
    }

    private static void bindPunishmentInsert(
            PreparedStatement statement,
            UUID caseId,
            PunishmentCaseDraft draft
    ) throws SQLException {
        statement.setBytes(1, UuidBytes.toBytes(caseId));
        statement.setString(2, draft.operationKey());
        statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
        statement.setBytes(4, UuidBytes.toBytes(draft.punishmentId()));
        statement.setString(5, truncate(draft.summary(), 512));
        statement.setBytes(6, UuidBytes.toBytes(draft.issuerId()));
        statement.setTimestamp(7, Timestamp.from(draft.observedAt()));
        statement.setTimestamp(8, Timestamp.from(draft.observedAt()));
        setInstant(statement, 9, draft.state().terminal() ? draft.observedAt() : null);
        statement.setLong(10, draft.punishmentRevision());
    }

    private void insertInvestigationCase(Connection connection, InvestigationCaseDraft draft) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_cases(
                    case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                    summary, state, opened_by, opened_at, last_activity_at, closed_at,
                    punishment_ended_at, source_revision, revision
                ) VALUES (?, ?, ?, 'INVESTIGATION', NULL, ?, ?, 'OPEN', ?, ?, ?, NULL, NULL, 0, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(draft.caseId()));
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            if (draft.legacyCaseId().isPresent()) {
                statement.setString(4, draft.legacyCaseId().orElseThrow());
            } else {
                statement.setNull(4, Types.CHAR);
            }
            statement.setString(5, truncate(draft.summary(), 512));
            statement.setBytes(6, UuidBytes.toBytes(draft.openedBy()));
            statement.setTimestamp(7, Timestamp.from(draft.openedAt()));
            statement.setTimestamp(8, Timestamp.from(draft.openedAt()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "investigation case was not inserted");
        }
    }

    private InvestigationCase updatePunishmentObservation(
            Connection connection,
            Current current,
            PunishmentCaseDraft draft
    ) throws SQLException {
        Instant lastActivity = later(current.lastActivityAt(), draft.observedAt());
        Instant punishmentEnded = draft.state().terminal()
                ? draft.observedAt()
                : current.punishmentEndedAt().orElse(null);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = ?, punishment_ended_at = ?, source_revision = ?, revision = revision + 1
                WHERE case_id = ? AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(lastActivity));
            setInstant(statement, 2, punishmentEnded);
            statement.setLong(3, draft.punishmentRevision());
            statement.setBytes(4, UuidBytes.toBytes(current.caseId()));
            statement.setLong(5, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "punishment case revision changed");
        }
        return requireById(connection, current.caseId(), false).toDomain(false);
    }

    private static void updateActivity(
            Connection connection,
            UUID caseId,
            long revision,
            Instant lastActivity
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = ?, revision = revision + 1
                WHERE case_id = ? AND state = 'OPEN' AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(lastActivity));
            statement.setBytes(2, UuidBytes.toBytes(caseId));
            statement.setLong(3, revision);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "case activity revision changed");
        }
    }

    private Current requireById(Connection connection, UUID caseId, boolean lock) throws SQLException {
        Current current = byId(connection, caseId, lock);
        if (current == null) {
            throw new SQLException("Discord investigation case does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, UUID caseId, boolean lock) throws SQLException {
        if (lock) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                           summary, state, opened_by, opened_at, last_activity_at, closed_at,
                           punishment_ended_at, source_revision, revision
                    FROM discord_investigation_cases
                    WHERE case_id = ?
                    FOR UPDATE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(caseId));
                return readOne(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                       summary, state, opened_by, opened_at, last_activity_at, closed_at,
                       punishment_ended_at, source_revision, revision
                FROM discord_investigation_cases
                WHERE case_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(caseId));
            return readOne(statement);
        }
    }

    private Current byPunishment(Connection connection, UUID punishmentId, boolean lock) throws SQLException {
        if (lock) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                           summary, state, opened_by, opened_at, last_activity_at, closed_at,
                           punishment_ended_at, source_revision, revision
                    FROM discord_investigation_cases
                    WHERE punishment_id = ?
                    FOR UPDATE
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(punishmentId));
                return readOne(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                       summary, state, opened_by, opened_at, last_activity_at, closed_at,
                       punishment_ended_at, source_revision, revision
                FROM discord_investigation_cases
                WHERE punishment_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(punishmentId));
            return readOne(statement);
        }
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        if (lock) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                           summary, state, opened_by, opened_at, last_activity_at, closed_at,
                           punishment_ended_at, source_revision, revision
                    FROM discord_investigation_cases
                    WHERE operation_key = ?
                    FOR UPDATE
                    """)) {
                statement.setString(1, operationKey);
                return readOne(statement);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, operation_key, subject_id, source, punishment_id, legacy_case_id,
                       summary, state, opened_by, opened_at, last_activity_at, closed_at,
                       punishment_ended_at, source_revision, revision
                FROM discord_investigation_cases
                WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            return readOne(statement);
        }
    }

    private static Current readOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            return rows.next() ? read(rows) : null;
        }
    }

    private static Current read(ResultSet rows) throws SQLException {
        byte[] punishmentBytes = rows.getBytes("punishment_id");
        Timestamp closedAt = rows.getTimestamp("closed_at");
        Timestamp punishmentEnded = rows.getTimestamp("punishment_ended_at");
        return new Current(
                UuidBytes.fromBytes(rows.getBytes("case_id")),
                rows.getString("operation_key"),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                InvestigationCase.Source.valueOf(rows.getString("source")),
                punishmentBytes == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(punishmentBytes)),
                Optional.ofNullable(rows.getString("legacy_case_id")),
                rows.getString("summary"),
                InvestigationCase.State.valueOf(rows.getString("state")),
                UuidBytes.fromBytes(rows.getBytes("opened_by")),
                rows.getTimestamp("opened_at").toInstant(),
                rows.getTimestamp("last_activity_at").toInstant(),
                closedAt == null ? Optional.empty() : Optional.of(closedAt.toInstant()),
                punishmentEnded == null ? Optional.empty() : Optional.of(punishmentEnded.toInstant()),
                rows.getLong("source_revision"),
                rows.getLong("revision")
        );
    }

    private static void requireSubject(Current current, ModerationSubjectId subjectId) throws SQLException {
        if (!current.subjectId().equals(subjectId)) {
            throw new SQLException("punishment case subject does not match persisted case");
        }
    }

    private static void requireInvestigationReplay(Current current, InvestigationCaseDraft draft) throws SQLException {
        if (!current.caseId().equals(draft.caseId()) || current.source() != InvestigationCase.Source.INVESTIGATION
                || !current.subjectId().equals(draft.subjectId()) || !current.summary().equals(truncate(draft.summary(), 512))) {
            throw new SQLException("investigation case operation key was reused for a different request");
        }
    }

    private static void requireOpenRevision(Current current, long expectedRevision) throws SQLException {
        if (current.state() != InvestigationCase.State.OPEN) {
            throw new SQLException("Discord investigation case is closed");
        }
        if (current.revision() != expectedRevision) {
            throw new SQLException("Discord investigation case revision changed");
        }
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private record Current(
            UUID caseId,
            String operationKey,
            ModerationSubjectId subjectId,
            InvestigationCase.Source source,
            Optional<UUID> punishmentId,
            Optional<String> legacyCaseId,
            String summary,
            InvestigationCase.State state,
            UUID openedBy,
            Instant openedAt,
            Instant lastActivityAt,
            Optional<Instant> closedAt,
            Optional<Instant> punishmentEndedAt,
            long sourceRevision,
            long revision
    ) {
        InvestigationCase toDomain(boolean replayed) {
            return new InvestigationCase(
                    caseId, subjectId, source, punishmentId, legacyCaseId, summary, state,
                    openedBy, openedAt, lastActivityAt, closedAt, punishmentEndedAt,
                    sourceRevision, revision, replayed
            );
        }
    }
}
