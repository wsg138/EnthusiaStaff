package net.enthusia.staff.persistence;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.investigation.InvestigationCase;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.CaseActivity;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.InvestigationCaseDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.PunishmentCaseDraft;

final class JdbcDiscordInvestigationCaseStore {
    private static final String CONFIGURATION_VERSION = "discord-d09-v1";
    private final DataSource dataSource;
    private final SecureIdentifiers identifiers = new SecureIdentifiers(new SecureRandom());

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
            return insertInvestigationCase(connection, draft);
        });
    }

    Optional<InvestigationCase> findCase(CaseId caseId) {
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
        validateCloseRequest(inactivityCutoff, now, limit);
        return JdbcTransactionSupport.execute(dataSource, "Unable to close inactive Discord cases", connection -> {
            List<CaseId> eligible = eligibleForClose(connection, inactivityCutoff, limit);
            for (CaseId caseId : eligible) {
                closeCase(connection, caseId, now);
            }
            return eligible.size();
        });
    }

    private InvestigationCase insertPunishmentCase(Connection connection, PunishmentCaseDraft draft) throws SQLException {
        CaseId caseId = identifiers.newCaseId();
        insertCanonicalPunishmentCase(connection, caseId, draft);
        insertExtension(connection, caseId, draft);
        return requireById(connection, caseId, false).toDomain(false);
    }

    private InvestigationCase insertInvestigationCase(Connection connection, InvestigationCaseDraft draft)
            throws SQLException {
        CaseId caseId = identifiers.newCaseId();
        insertCanonicalInvestigationCase(connection, caseId, draft);
        insertExtension(connection, caseId, draft);
        return requireById(connection, caseId, false).toDomain(false);
    }

    private static void insertCanonicalPunishmentCase(
            Connection connection,
            CaseId caseId,
            PunishmentCaseDraft draft
    ) throws SQLException {
        insertCanonicalCase(
                connection, caseId, draft.operationKey(), draft.subjectId(), draft.issuer(),
                truncate(draft.summary(), 160), "DISCORD_" + draft.consequenceType().name(),
                "DISCORD", draft.summary(), draft.observedAt()
        );
    }

    private static void insertCanonicalInvestigationCase(
            Connection connection,
            CaseId caseId,
            InvestigationCaseDraft draft
    ) throws SQLException {
        insertCanonicalCase(
                connection, caseId, draft.operationKey(), draft.subjectId(), draft.openedBy(),
                "Private Discord investigation", "DISCORD_INVESTIGATION", "DISCORD_INVESTIGATION",
                draft.summary(), draft.openedAt()
        );
    }

    private static void insertCanonicalCase(
            Connection connection,
            CaseId caseId,
            String operationKey,
            ModerationSubjectId subjectId,
            Actor actor,
            String publicReason,
            String exactReason,
            String family,
            String explanation,
            Instant issuedAt
    ) throws SQLException {
        UUID targetId = mainMinecraftTarget(connection, subjectId);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO cases(
                    case_id, idempotency_key, target_id, subject_id, actor_id, actor_name, actor_rank,
                    public_reason, exact_reason_id, sanction_family, internal_explanation,
                    configuration_version, visibility, state, issued_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PRIVATE', 'OPEN', ?)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, operationKey);
            setUuid(statement, 3, targetId);
            statement.setBytes(4, UuidBytes.toBytes(subjectId.value()));
            statement.setBytes(5, UuidBytes.toBytes(actor.id()));
            statement.setString(6, actor.displayName());
            statement.setString(7, actor.rank().name());
            statement.setString(8, publicReason);
            statement.setString(9, truncate(exactReason, 96));
            statement.setString(10, truncate(family, 64));
            statement.setString(11, explanation);
            statement.setString(12, CONFIGURATION_VERSION);
            statement.setTimestamp(13, Timestamp.from(issuedAt));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "authoritative case was not inserted");
        }
    }

    private static UUID mainMinecraftTarget(Connection connection, ModerationSubjectId subjectId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_id FROM moderation_subject_main_accounts WHERE subject_id = ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(subjectId.value()));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? UuidBytes.fromBytes(rows.getBytes("player_id")) : null;
            }
        }
    }

    private static void insertExtension(Connection connection, CaseId caseId, PunishmentCaseDraft draft)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_cases(
                    case_id, operation_key, subject_id, source, punishment_id, last_activity_at,
                    closed_at, punishment_ended_at, source_revision, revision
                ) VALUES (?, ?, ?, 'DISCORD_PUNISHMENT', ?, ?, NULL, ?, ?, 0)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setBytes(4, UuidBytes.toBytes(draft.punishmentId()));
            statement.setTimestamp(5, Timestamp.from(draft.observedAt()));
            setInstant(statement, 6, draft.state().terminal() ? Optional.of(draft.observedAt()) : Optional.empty());
            statement.setLong(7, draft.punishmentRevision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "punishment case extension was not inserted");
        }
    }

    private static void insertExtension(Connection connection, CaseId caseId, InvestigationCaseDraft draft)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_cases(
                    case_id, operation_key, subject_id, source, punishment_id, last_activity_at,
                    closed_at, punishment_ended_at, source_revision, revision
                ) VALUES (?, ?, ?, 'INVESTIGATION', NULL, ?, NULL, NULL, 0, 0)
                """)) {
            statement.setString(1, caseId.value());
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setTimestamp(4, Timestamp.from(draft.openedAt()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "investigation case extension was not inserted");
        }
    }

    private InvestigationCase updatePunishmentObservation(
            Connection connection,
            Current current,
            PunishmentCaseDraft draft
    ) throws SQLException {
        Instant lastActivity = later(current.lastActivityAt(), draft.observedAt());
        Optional<Instant> punishmentEnded = draft.state().terminal()
                ? Optional.of(draft.observedAt()) : current.punishmentEndedAt();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = ?, punishment_ended_at = ?, source_revision = ?, revision = revision + 1
                WHERE case_id = ? AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(lastActivity));
            setInstant(statement, 2, punishmentEnded);
            statement.setLong(3, draft.punishmentRevision());
            statement.setString(4, current.caseId().value());
            statement.setLong(5, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "punishment case revision changed");
        }
        return requireById(connection, current.caseId(), false).toDomain(false);
    }

    private static void updateActivity(
            Connection connection,
            CaseId caseId,
            long revision,
            Instant lastActivity
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = ?, revision = revision + 1
                WHERE case_id = ? AND closed_at IS NULL AND revision = ?
                """)) {
            statement.setTimestamp(1, Timestamp.from(lastActivity));
            statement.setString(2, caseId.value());
            statement.setLong(3, revision);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "case activity revision changed");
        }
    }

    private static List<CaseId> eligibleForClose(Connection connection, Instant cutoff, int limit) throws SQLException {
        List<CaseId> cases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT i.case_id
                FROM discord_investigation_cases i
                JOIN cases c ON c.case_id = i.case_id
                WHERE c.state = 'OPEN' AND i.closed_at IS NULL AND i.last_activity_at <= ?
                  AND (i.source = 'INVESTIGATION' OR i.punishment_ended_at IS NOT NULL)
                ORDER BY i.last_activity_at, i.case_id
                LIMIT ?
                FOR UPDATE
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    cases.add(new CaseId(rows.getString("case_id")));
                }
            }
        }
        return List.copyOf(cases);
    }

    private static void closeCase(Connection connection, CaseId caseId, Instant now) throws SQLException {
        try (PreparedStatement extension = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET closed_at = ?, revision = revision + 1
                WHERE case_id = ? AND closed_at IS NULL
                """);
             PreparedStatement authority = connection.prepareStatement("""
                UPDATE cases SET state = 'CLOSED', revision = revision + 1
                WHERE case_id = ? AND state = 'OPEN'
                """)) {
            extension.setTimestamp(1, Timestamp.from(now));
            extension.setString(2, caseId.value());
            JdbcTransactionSupport.requireSingleUpdate(extension.executeUpdate(), "case lifecycle extension was not closed");
            authority.setString(1, caseId.value());
            JdbcTransactionSupport.requireSingleUpdate(authority.executeUpdate(), "authoritative case was not closed");
        }
    }

    private Current requireById(Connection connection, CaseId caseId, boolean lock) throws SQLException {
        Current current = byId(connection, caseId, lock);
        if (current == null) {
            throw new SQLException("Discord investigation case does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, CaseId caseId, boolean lock) throws SQLException {
        return queryOne(connection, "i.case_id = ?", caseId.value(), lock);
    }

    private Current byPunishment(Connection connection, UUID punishmentId, boolean lock) throws SQLException {
        return queryOne(connection, "i.punishment_id = ?", UuidBytes.toBytes(punishmentId), lock);
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        return queryOne(connection, "i.operation_key = ?", operationKey, lock);
    }

    private Current queryOne(Connection connection, String predicate, Object value, boolean lock) throws SQLException {
        String sql = """
                SELECT i.case_id, i.operation_key, i.subject_id, i.source, i.punishment_id,
                       c.internal_explanation AS summary, c.state, c.actor_id, c.issued_at,
                       i.last_activity_at, i.closed_at, i.punishment_ended_at,
                       i.source_revision, i.revision
                FROM discord_investigation_cases i
                JOIN cases c ON c.case_id = i.case_id
                WHERE %s%s
                """.formatted(predicate, lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof byte[] bytes) {
                statement.setBytes(1, bytes);
            } else {
                statement.setString(1, value.toString());
            }
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    private static Current read(ResultSet rows) throws SQLException {
        byte[] punishmentBytes = rows.getBytes("punishment_id");
        Timestamp closedAt = rows.getTimestamp("closed_at");
        Timestamp punishmentEnded = rows.getTimestamp("punishment_ended_at");
        return new Current(
                new CaseId(rows.getString("case_id")),
                rows.getString("operation_key"),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                InvestigationCase.Source.valueOf(rows.getString("source")),
                punishmentBytes == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(punishmentBytes)),
                rows.getString("summary"),
                "OPEN".equals(rows.getString("state")) ? InvestigationCase.State.OPEN : InvestigationCase.State.CLOSED,
                UuidBytes.fromBytes(rows.getBytes("actor_id")),
                rows.getTimestamp("issued_at").toInstant(),
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
        if (current.source() != InvestigationCase.Source.INVESTIGATION
                || !current.subjectId().equals(draft.subjectId())
                || !current.summary().equals(draft.summary())
                || !current.openedBy().equals(draft.openedBy().id())) {
            throw new SQLException("investigation case operation key was reused for a different request");
        }
    }

    private static void requireOpenRevision(Current current, long expectedRevision) throws SQLException {
        if (current.state() != InvestigationCase.State.OPEN || current.closedAt().isPresent()) {
            throw new SQLException("Discord investigation case is closed");
        }
        if (current.revision() != expectedRevision) {
            throw new SQLException("Discord investigation case revision changed");
        }
    }

    private static void validateCloseRequest(Instant cutoff, Instant now, int limit) {
        if (cutoff == null || now == null || now.isBefore(cutoff) || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("inactive case close request is invalid");
        }
    }

    private static Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private static void setInstant(PreparedStatement statement, int index, Optional<Instant> value)
            throws SQLException {
        if (value.isPresent()) {
            statement.setTimestamp(index, Timestamp.from(value.orElseThrow()));
        } else {
            statement.setNull(index, Types.TIMESTAMP);
        }
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(value));
        }
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private record Current(
            CaseId caseId,
            String operationKey,
            ModerationSubjectId subjectId,
            InvestigationCase.Source source,
            Optional<UUID> punishmentId,
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
                    caseId, subjectId, source, punishmentId, summary, state,
                    openedBy, openedAt, lastActivityAt, closedAt, punishmentEndedAt,
                    sourceRevision, revision, replayed
            );
        }
    }
}
