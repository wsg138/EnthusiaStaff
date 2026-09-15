package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;

final class JdbcDiscordInvestigationEvidenceStore {
    private static final Duration INVESTIGATION_RETENTION_WINDOW = Duration.ofDays(60);
    private static final Duration POST_END_RETENTION = Duration.ofDays(30);
    private static final String PURGED_METADATA = "{\"kind\":\"D09_MESSAGE\",\"purged\":true}";

    private final DataSource dataSource;
    private final DiscordInvestigationJsonCodec codec = new DiscordInvestigationJsonCodec();

    JdbcDiscordInvestigationEvidenceStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    InvestigationEvidence.Stored capture(InvestigationEvidence.Capture capture) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to capture Discord investigation evidence", connection -> {
            EvidenceCurrent replay = byOperation(connection, capture.operationKey(), true);
            if (replay != null) {
                requireCaptureReplay(replay, capture);
                return replay.toStored(true);
            }
            requireOpenCaseSubject(connection, capture.caseId(), capture.subjectId());
            insertParent(connection, capture);
            insertEvidence(connection, capture);
            insertVersion(connection, capture.evidenceId(), 0, capture.operationKey(), capture.focus(), capture.capturedAt());
            insertContext(connection, capture.evidenceId(), capture.before(), capture.capturedAt());
            insertContext(connection, capture.evidenceId(), capture.after(), capture.capturedAt());
            touchCase(connection, capture.caseId(), capture.capturedAt());
            return requireById(connection, capture.evidenceId(), false).toStored(false);
        });
    }

    InvestigationEvidence.Stored recordEdit(InvestigationEvidence.Edit edit) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to record Discord evidence edit", connection -> {
            UUID replayEvidence = versionEvidenceForOperation(connection, edit.operationKey());
            EvidenceCurrent current = requireById(connection, edit.evidenceId(), true);
            if (replayEvidence != null) {
                requireSameEvidence(replayEvidence, edit.evidenceId());
                return current.toStored(true);
            }
            requireMessageIdentity(current, edit.message());
            long nextRevision = current.revision() + 1;
            updateEvidence(connection, current, edit, nextRevision);
            updateParentRevision(connection, current.evidenceId(), edit.recordedAt());
            insertVersion(connection, current.evidenceId(), nextRevision, edit.operationKey(), edit.message(), edit.recordedAt());
            touchCase(connection, current.caseId(), edit.recordedAt());
            return requireById(connection, current.evidenceId(), false).toStored(false);
        });
    }

    int captureMoreContext(InvestigationEvidence.ContextBatch batch) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to capture additional Discord context", connection -> {
            UUID replayEvidence = contextOperationEvidence(connection, batch.operationKey());
            if (replayEvidence != null) {
                requireSameEvidence(replayEvidence, batch.evidenceId());
                return 0;
            }
            EvidenceCurrent current = requireById(connection, batch.evidenceId(), true);
            requireContextLocation(current, batch.messages());
            insertContextOperation(connection, batch);
            upsertContext(connection, batch.evidenceId(), batch.messages(), batch.capturedAt());
            updateParentRevision(connection, current.evidenceId(), batch.capturedAt());
            touchCase(connection, current.caseId(), batch.capturedAt());
            return batch.messages().size();
        });
    }

    Optional<InvestigationEvidence.Stored> findByMessage(String guildId, String channelId, String messageId) {
        if (blank(guildId) || blank(channelId) || blank(messageId)) {
            throw new IllegalArgumentException("evidence message lookup identifiers must be present");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord evidence", connection ->
                Optional.ofNullable(byMessage(connection, guildId, channelId, messageId)).map(value -> value.toStored(false)));
    }

    int purgeEligible(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 500) {
            throw new IllegalArgumentException("evidence purge request is invalid");
        }
        return JdbcTransactionSupport.execute(dataSource, "Unable to purge expired Discord evidence", connection -> {
            List<UUID> eligible = eligibleForPurge(connection, now.minus(POST_END_RETENTION), limit);
            for (UUID evidenceId : eligible) {
                purgeOne(connection, evidenceId, now);
            }
            return eligible.size();
        });
    }

    private void insertParent(Connection connection, InvestigationEvidence.Capture capture) throws SQLException {
        InvestigationEvidence.Message focus = capture.focus();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_evidence_metadata(
                    evidence_id, operation_key, subject_id, case_id, guild_id, channel_id,
                    message_id, author_user_id, captured_at, retain_until, metadata_json,
                    purge_state, revision
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(capture.evidenceId()));
            statement.setString(2, capture.operationKey());
            statement.setBytes(3, UuidBytes.toBytes(capture.subjectId().value()));
            statement.setBigDecimal(4, snowflake(focus.guildId()));
            statement.setBigDecimal(5, snowflake(focus.channelId()));
            statement.setBigDecimal(6, snowflake(focus.messageId()));
            statement.setBigDecimal(7, snowflake(focus.authorUserId().value()));
            statement.setTimestamp(8, Timestamp.from(capture.capturedAt()));
            statement.setTimestamp(9, Timestamp.from(capture.capturedAt().plus(INVESTIGATION_RETENTION_WINDOW)));
            statement.setString(10, codec.evidenceMetadata(capture));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence metadata was not inserted");
        }
    }

    private void insertEvidence(Connection connection, InvestigationEvidence.Capture capture) throws SQLException {
        InvestigationEvidence.Message focus = capture.focus();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_evidence(
                    evidence_id, investigation_case_id, message_link, message_created_at,
                    last_observed_at, edited_at, message_content, attachment_metadata_json, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(capture.evidenceId()));
            statement.setBytes(2, UuidBytes.toBytes(capture.caseId()));
            statement.setString(3, focus.jumpUrl());
            statement.setTimestamp(4, Timestamp.from(focus.createdAt()));
            statement.setTimestamp(5, Timestamp.from(capture.capturedAt()));
            setInstant(statement, 6, focus.editedAt().orElse(null));
            statement.setString(7, focus.content());
            statement.setString(8, codec.attachments(focus.attachments()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "investigation evidence was not inserted");
        }
    }

    private void updateEvidence(
            Connection connection,
            EvidenceCurrent current,
            InvestigationEvidence.Edit edit,
            long nextRevision
    ) throws SQLException {
        InvestigationEvidence.Message message = edit.message();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_evidence
                SET message_link = ?, last_observed_at = ?, edited_at = ?, message_content = ?,
                    attachment_metadata_json = ?, revision = ?
                WHERE evidence_id = ? AND revision = ?
                """)) {
            statement.setString(1, message.jumpUrl());
            statement.setTimestamp(2, Timestamp.from(edit.recordedAt()));
            setInstant(statement, 3, message.editedAt().orElse(null));
            statement.setString(4, message.content());
            statement.setString(5, codec.attachments(message.attachments()));
            statement.setLong(6, nextRevision);
            statement.setBytes(7, UuidBytes.toBytes(current.evidenceId()));
            statement.setLong(8, current.revision());
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence revision changed");
        }
    }

    private void insertVersion(
            Connection connection,
            UUID evidenceId,
            long revision,
            String operationKey,
            InvestigationEvidence.Message message,
            Instant recordedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_evidence_versions(
                    evidence_id, revision, operation_key, message_content,
                    attachment_metadata_json, edited_at, recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(evidenceId));
            statement.setLong(2, revision);
            statement.setString(3, operationKey);
            statement.setString(4, message.content());
            statement.setString(5, codec.attachments(message.attachments()));
            setInstant(statement, 6, message.editedAt().orElse(null));
            statement.setTimestamp(7, Timestamp.from(recordedAt));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence version was not inserted");
        }
    }

    private void insertContext(
            Connection connection,
            UUID evidenceId,
            List<InvestigationEvidence.Message> messages,
            Instant capturedAt
    ) throws SQLException {
        for (InvestigationEvidence.Message message : messages) {
            insertContextMessage(connection, evidenceId, message, capturedAt);
        }
    }

    private void insertContextMessage(
            Connection connection,
            UUID evidenceId,
            InvestigationEvidence.Message message,
            Instant capturedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_context(
                    evidence_id, message_id, guild_id, channel_id, author_user_id,
                    message_created_at, edited_at, message_link, message_content,
                    attachment_metadata_json, captured_at, last_observed_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
            bindContext(statement, evidenceId, message, capturedAt);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence context was not inserted");
        }
    }

    private void upsertContext(
            Connection connection,
            UUID evidenceId,
            List<InvestigationEvidence.Message> messages,
            Instant capturedAt
    ) throws SQLException {
        for (InvestigationEvidence.Message message : messages) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO discord_investigation_context(
                        evidence_id, message_id, guild_id, channel_id, author_user_id,
                        message_created_at, edited_at, message_link, message_content,
                        attachment_metadata_json, captured_at, last_observed_at, revision
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    ON DUPLICATE KEY UPDATE
                        author_user_id = VALUES(author_user_id), edited_at = VALUES(edited_at),
                        message_link = VALUES(message_link), message_content = VALUES(message_content),
                        attachment_metadata_json = VALUES(attachment_metadata_json),
                        last_observed_at = VALUES(last_observed_at), revision = revision + 1
                    """)) {
                bindContext(statement, evidenceId, message, capturedAt);
                statement.executeUpdate();
            }
        }
    }

    private void bindContext(
            PreparedStatement statement,
            UUID evidenceId,
            InvestigationEvidence.Message message,
            Instant capturedAt
    ) throws SQLException {
        statement.setBytes(1, UuidBytes.toBytes(evidenceId));
        statement.setBigDecimal(2, snowflake(message.messageId()));
        statement.setBigDecimal(3, snowflake(message.guildId()));
        statement.setBigDecimal(4, snowflake(message.channelId()));
        statement.setBigDecimal(5, snowflake(message.authorUserId().value()));
        statement.setTimestamp(6, Timestamp.from(message.createdAt()));
        setInstant(statement, 7, message.editedAt().orElse(null));
        statement.setString(8, message.jumpUrl());
        statement.setString(9, message.content());
        statement.setString(10, codec.attachments(message.attachments()));
        statement.setTimestamp(11, Timestamp.from(capturedAt));
        statement.setTimestamp(12, Timestamp.from(capturedAt));
    }

    private static void insertContextOperation(
            Connection connection,
            InvestigationEvidence.ContextBatch batch
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_investigation_context_operations(operation_key, evidence_id, captured_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setString(1, batch.operationKey());
            statement.setBytes(2, UuidBytes.toBytes(batch.evidenceId()));
            statement.setTimestamp(3, Timestamp.from(batch.capturedAt()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "context operation was not inserted");
        }
    }

    private static void updateParentRevision(Connection connection, UUID evidenceId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evidence_metadata
                SET retain_until = GREATEST(retain_until, ?), revision = revision + 1
                WHERE evidence_id = ? AND purge_state = 'ACTIVE'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now.plus(INVESTIGATION_RETENTION_WINDOW)));
            statement.setBytes(2, UuidBytes.toBytes(evidenceId));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence metadata is not active");
        }
    }

    private static void touchCase(Connection connection, UUID caseId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_investigation_cases
                SET last_activity_at = GREATEST(last_activity_at, ?), revision = revision + 1
                WHERE case_id = ? AND state = 'OPEN'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(caseId));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence case is not open");
        }
    }

    private static void requireOpenCaseSubject(
            Connection connection,
            UUID caseId,
            ModerationSubjectId subjectId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT subject_id, state
                FROM discord_investigation_cases
                WHERE case_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(caseId));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || !"OPEN".equals(rows.getString("state"))) {
                    throw new SQLException("evidence case does not exist or is closed");
                }
                UUID persisted = UuidBytes.fromBytes(rows.getBytes("subject_id"));
                if (!persisted.equals(subjectId.value())) {
                    throw new SQLException("evidence subject does not match case subject");
                }
            }
        }
    }

    private EvidenceCurrent requireById(Connection connection, UUID evidenceId, boolean lock) throws SQLException {
        EvidenceCurrent current = byId(connection, evidenceId, lock);
        if (current == null) {
            throw new SQLException("Discord investigation evidence does not exist");
        }
        return current;
    }

    private EvidenceCurrent byId(Connection connection, UUID evidenceId, boolean lock) throws SQLException {
        return queryEvidence(
                connection,
                "m.evidence_id = ?",
                statement -> statement.setBytes(1, UuidBytes.toBytes(evidenceId)),
                lock
        );
    }

    private EvidenceCurrent byOperation(Connection connection, String operationKey, boolean lock) throws SQLException {
        return queryEvidence(connection, "m.operation_key = ?", statement -> statement.setString(1, operationKey), lock);
    }

    private EvidenceCurrent byMessage(
            Connection connection,
            String guildId,
            String channelId,
            String messageId
    ) throws SQLException {
        return queryEvidence(connection,
                "m.guild_id = ? AND m.channel_id = ? AND m.message_id = ? AND m.purge_state = 'ACTIVE'",
                statement -> {
                    statement.setBigDecimal(1, snowflake(guildId));
                    statement.setBigDecimal(2, snowflake(channelId));
                    statement.setBigDecimal(3, snowflake(messageId));
                },
                false
        );
    }

    private EvidenceCurrent queryEvidence(
            Connection connection,
            String predicate,
            Binder binder,
            boolean lock
    ) throws SQLException {
        String suffix = lock ? " FOR UPDATE" : "";
        String sql = "SELECT m.evidence_id, m.subject_id, m.guild_id, m.channel_id, m.message_id, "
                + "m.captured_at, e.investigation_case_id, e.last_observed_at, e.revision "
                + "FROM discord_evidence_metadata m "
                + "JOIN discord_investigation_evidence e ON e.evidence_id = m.evidence_id "
                + "WHERE " + predicate + suffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readEvidence(rows) : null;
            }
        }
    }

    private static EvidenceCurrent readEvidence(ResultSet rows) throws SQLException {
        return new EvidenceCurrent(
                UuidBytes.fromBytes(rows.getBytes("evidence_id")),
                UuidBytes.fromBytes(rows.getBytes("investigation_case_id")),
                new ModerationSubjectId(UuidBytes.fromBytes(rows.getBytes("subject_id"))),
                rows.getBigDecimal("guild_id").toPlainString(),
                rows.getBigDecimal("channel_id").toPlainString(),
                rows.getBigDecimal("message_id").toPlainString(),
                rows.getTimestamp("captured_at").toInstant(),
                rows.getTimestamp("last_observed_at").toInstant(),
                rows.getLong("revision")
        );
    }

    private static UUID versionEvidenceForOperation(Connection connection, String operationKey) throws SQLException {
        return evidenceForOperation(
                connection,
                "SELECT evidence_id FROM discord_investigation_evidence_versions WHERE operation_key = ?",
                operationKey
        );
    }

    private static UUID contextOperationEvidence(Connection connection, String operationKey) throws SQLException {
        return evidenceForOperation(
                connection,
                "SELECT evidence_id FROM discord_investigation_context_operations WHERE operation_key = ?",
                operationKey
        );
    }

    private static UUID evidenceForOperation(Connection connection, String sql, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operationKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? UuidBytes.fromBytes(rows.getBytes("evidence_id")) : null;
            }
        }
    }

    private static List<UUID> eligibleForPurge(Connection connection, Instant cutoff, int limit) throws SQLException {
        List<UUID> evidence = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT e.evidence_id
                FROM discord_investigation_evidence e
                JOIN discord_evidence_metadata m ON m.evidence_id = e.evidence_id
                JOIN discord_investigation_cases c ON c.case_id = e.investigation_case_id
                WHERE m.purge_state = 'ACTIVE'
                  AND ((c.source = 'DISCORD_PUNISHMENT' AND c.punishment_ended_at IS NOT NULL
                        AND c.punishment_ended_at <= ?)
                    OR (c.source = 'INVESTIGATION' AND c.state = 'CLOSED'
                        AND c.closed_at IS NOT NULL AND c.closed_at <= ?))
                ORDER BY COALESCE(c.punishment_ended_at, c.closed_at), e.evidence_id
                LIMIT ?
                FOR UPDATE
                """)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            statement.setTimestamp(2, Timestamp.from(cutoff));
            statement.setInt(3, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    evidence.add(UuidBytes.fromBytes(rows.getBytes("evidence_id")));
                }
            }
        }
        return List.copyOf(evidence);
    }

    private static void purgeOne(Connection connection, UUID evidenceId, Instant now) throws SQLException {
        deleteByEvidence(connection, "DELETE FROM discord_investigation_context_operations WHERE evidence_id = ?", evidenceId);
        deleteByEvidence(connection, "DELETE FROM discord_investigation_context WHERE evidence_id = ?", evidenceId);
        deleteByEvidence(connection, "DELETE FROM discord_investigation_evidence_versions WHERE evidence_id = ?", evidenceId);
        deleteByEvidence(connection, "DELETE FROM discord_investigation_evidence WHERE evidence_id = ?", evidenceId);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_evidence_metadata
                SET metadata_json = ?, purge_state = 'PURGED', retain_until = ?, revision = revision + 1
                WHERE evidence_id = ? AND purge_state = 'ACTIVE'
                """)) {
            statement.setString(1, PURGED_METADATA);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(evidenceId));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence purge state changed");
        }
    }

    private static void deleteByEvidence(Connection connection, String sql, UUID evidenceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(evidenceId));
            statement.executeUpdate();
        }
    }

    private static void requireCaptureReplay(
            EvidenceCurrent current,
            InvestigationEvidence.Capture capture
    ) throws SQLException {
        InvestigationEvidence.Message focus = capture.focus();
        if (!current.evidenceId().equals(capture.evidenceId()) || !current.caseId().equals(capture.caseId())
                || !current.subjectId().equals(capture.subjectId()) || !current.guildId().equals(focus.guildId())
                || !current.channelId().equals(focus.channelId()) || !current.messageId().equals(focus.messageId())) {
            throw new SQLException("evidence operation key was reused for a different capture");
        }
    }

    private static void requireMessageIdentity(
            EvidenceCurrent current,
            InvestigationEvidence.Message message
    ) throws SQLException {
        if (!current.guildId().equals(message.guildId()) || !current.channelId().equals(message.channelId())
                || !current.messageId().equals(message.messageId())) {
            throw new SQLException("evidence edit does not target the captured message");
        }
    }

    private static void requireContextLocation(
            EvidenceCurrent current,
            List<InvestigationEvidence.Message> messages
    ) throws SQLException {
        for (InvestigationEvidence.Message message : messages) {
            if (!current.guildId().equals(message.guildId()) || !current.channelId().equals(message.channelId())) {
                throw new SQLException("additional context does not belong to the evidence channel");
            }
        }
    }

    private static void requireSameEvidence(UUID actual, UUID expected) throws SQLException {
        if (!actual.equals(expected)) {
            throw new SQLException("evidence operation key was reused for another evidence record");
        }
    }

    private static BigDecimal snowflake(String value) throws SQLException {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new SQLException("Discord snowflake is invalid", exception);
        }
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record EvidenceCurrent(
            UUID evidenceId,
            UUID caseId,
            ModerationSubjectId subjectId,
            String guildId,
            String channelId,
            String messageId,
            Instant capturedAt,
            Instant lastObservedAt,
            long revision
    ) {
        InvestigationEvidence.Stored toStored(boolean replayed) {
            return new InvestigationEvidence.Stored(
                    evidenceId, caseId, subjectId, messageId, capturedAt, lastObservedAt, revision, replayed
            );
        }
    }
}
