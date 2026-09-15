package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.investigation.InvestigationNote;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.NoteDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.NoteEdit;

final class JdbcDiscordInvestigationNoteStore {
    private final DataSource dataSource;

    JdbcDiscordInvestigationNoteStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    InvestigationNote create(NoteDraft draft) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to create Discord private note", connection -> {
            Current replay = byOperation(connection, draft.operationKey(), true);
            if (replay != null) {
                requireCreateReplay(replay, draft);
                return replay.toDomain(true);
            }
            insertNote(connection, draft);
            insertVersion(connection, draft.noteId(), 0, draft.operationKey(), draft.text(), draft.actorId(), draft.now());
            touchCaseScope(connection, draft.scope(), draft.now());
            return requireById(connection, draft.noteId(), false).toDomain(false);
        });
    }

    InvestigationNote edit(NoteEdit edit) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to edit Discord private note", connection -> {
            Current current = requireById(connection, edit.noteId(), true);
            if (versionOperationExists(connection, edit.operationKey())) {
                return current.toDomain(true);
            }
            if (current.revision() != edit.expectedRevision()) {
                throw new SQLException("Discord private note revision changed");
            }
            long nextRevision = current.revision() + 1;
            updateNote(connection, current, edit, nextRevision);
            insertVersion(
                    connection, edit.noteId(), nextRevision, edit.operationKey(), edit.text(), edit.actorId(), edit.now());
            touchCaseScope(connection, current.scope(), edit.now());
            return requireById(connection, edit.noteId(), false).toDomain(false);
        });
    }

    List<InvestigationNote.Version> history(UUID noteId, int limit) {
        if (noteId == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("note history query is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord private note history", connection -> {
            List<InvestigationNote.Version> versions = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT revision, note_text, changed_by, changed_at
                    FROM discord_private_note_versions
                    WHERE note_id = ?
                    ORDER BY revision DESC
                    LIMIT ?
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(noteId));
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        versions.add(new InvestigationNote.Version(
                                rows.getLong("revision"),
                                rows.getString("note_text"),
                                UuidBytes.fromBytes(rows.getBytes("changed_by")),
                                rows.getTimestamp("changed_at").toInstant()
                        ));
                    }
                }
            }
            return List.copyOf(versions);
        });
    }

    private static void insertNote(Connection connection, NoteDraft draft) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_private_notes(
                    note_id, operation_key, subject_id, scope_type, scope_value, visibility,
                    current_text, created_by, created_at, updated_by, updated_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(draft.noteId()));
            statement.setString(2, draft.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(draft.subjectId().value()));
            statement.setString(4, draft.scope().type().name());
            statement.setString(5, draft.scope().value());
            statement.setString(6, draft.visibility().name());
            statement.setString(7, draft.text());
            statement.setBytes(8, UuidBytes.toBytes(draft.actorId()));
            statement.setTimestamp(9, Timestamp.from(draft.now()));
            statement.setBytes(10, UuidBytes.toBytes(draft.actorId()));
            statement.setTimestamp(11, Timestamp.from(draft.now()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord private note was not inserted");
        }
    }

    private static void updateNote(
            Connection connection,
            Current current,
            NoteEdit edit,
            long nextRevision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_private_notes
                SET current_text = ?, updated_by = ?, updated_at = ?, revision = ?
                WHERE note_id = ? AND revision = ?
                """)) {
            statement.setString(1, edit.text());
            statement.setBytes(2, UuidBytes.toBytes(edit.actorId()));
            statement.setTimestamp(3, Timestamp.from(edit.now()));
            statement.setLong(4, nextRevision);
            statement.setBytes(5, UuidBytes.toBytes(edit.noteId()));
            statement.setLong(6, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord private note revision changed");
        }
    }

    private static void insertVersion(
            Connection connection,
            UUID noteId,
            long revision,
            String operationKey,
            String text,
            UUID actorId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_private_note_versions(
                    note_id, revision, operation_key, note_text, changed_by, changed_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(noteId));
            statement.setLong(2, revision);
            statement.setString(3, operationKey);
            statement.setString(4, text);
            statement.setBytes(5, UuidBytes.toBytes(actorId));
            statement.setTimestamp(6, Timestamp.from(now));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "Discord note version was not inserted");
        }
    }

    private static void touchCaseScope(
            Connection connection,
            InvestigationNote.Scope scope,
            Instant now
    ) throws SQLException {
        if (scope.type() != InvestigationNote.ScopeType.CASE) {
            return;
        }
        UUID caseId;
        try {
            caseId = UUID.fromString(scope.value());
        } catch (IllegalArgumentException exception) {
            throw new SQLException("case-scoped note has an invalid case identifier", exception);
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = GREATEST(last_activity_at, ?), revision = revision + 1
                WHERE case_id = ? AND state = 'OPEN'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(caseId));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "case-scoped note case is not open");
        }
    }

    private Current requireById(Connection connection, UUID noteId, boolean lock) throws SQLException {
        Current current = byId(connection, noteId, lock);
        if (current == null) {
            throw new SQLException("Discord private note does not exist");
        }
        return current;
    }

    private Current byId(Connection connection, UUID noteId, boolean lock) throws SQLException {
        return queryOne(connection, "note_id = ?", statement -> statement.setBytes(1, UuidBytes.toBytes(noteId)), lock);
    }

    private Current byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        return queryOne(connection, "operation_key = ?", statement -> statement.setString(1, operationKey), lock);
    }

    private Current queryOne(Connection connection, String predicate, Binder binder, boolean lock) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        String sql = """
                SELECT note_id, subject_id, scope_type, scope_value, visibility, current_text,
                       created_by, created_at, updated_by, updated_at, revision
                FROM discord_private_notes
                WHERE """ + predicate + suffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    private static Current read(ResultSet rows) throws SQLException {
        return new Current(
                UuidBytes.fromBytes(rows.getBytes("note_id")),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                new InvestigationNote.Scope(
                        InvestigationNote.ScopeType.valueOf(rows.getString("scope_type")),
                        rows.getString("scope_value")
                ),
                InvestigationNote.Visibility.valueOf(rows.getString("visibility")),
                rows.getString("current_text"),
                UuidBytes.fromBytes(rows.getBytes("created_by")),
                rows.getTimestamp("created_at").toInstant(),
                UuidBytes.fromBytes(rows.getBytes("updated_by")),
                rows.getTimestamp("updated_at").toInstant(),
                rows.getLong("revision")
        );
    }

    private static boolean versionOperationExists(Connection connection, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM discord_private_note_versions WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void requireCreateReplay(Current current, NoteDraft draft) throws SQLException {
        if (!current.noteId().equals(draft.noteId()) || !current.subjectId().equals(draft.subjectId())
                || !current.scope().equals(draft.scope()) || current.visibility() != draft.visibility()
                || !current.text().equals(draft.text()) || !current.createdBy().equals(draft.actorId())) {
            throw new SQLException("Discord private note operation key was reused for a different request");
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record Current(
            UUID noteId,
            ModerationSubjectId subjectId,
            InvestigationNote.Scope scope,
            InvestigationNote.Visibility visibility,
            String text,
            UUID createdBy,
            Instant createdAt,
            UUID updatedBy,
            Instant updatedAt,
            long revision
    ) {
        InvestigationNote toDomain(boolean replayed) {
            return new InvestigationNote(
                    noteId, subjectId, scope, visibility, text, createdBy, createdAt,
                    updatedBy, updatedAt, revision, replayed
            );
        }
    }
}
