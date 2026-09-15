package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.InvestigationEvidence;

/** Persists bounded message context independently from primary evidence lifecycle orchestration. */
final class JdbcDiscordInvestigationContextStore {
    private final DiscordInvestigationJsonCodec codec;

    JdbcDiscordInvestigationContextStore(DiscordInvestigationJsonCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("investigation context codec must be present");
        }
        this.codec = codec;
    }

    void insert(
            Connection connection,
            UUID evidenceId,
            List<InvestigationEvidence.Message> messages,
            Instant capturedAt
    ) throws SQLException {
        for (InvestigationEvidence.Message message : messages) {
            insertOne(connection, evidenceId, message, capturedAt);
        }
    }

    void upsert(
            Connection connection,
            UUID evidenceId,
            List<InvestigationEvidence.Message> messages,
            Instant capturedAt
    ) throws SQLException {
        for (InvestigationEvidence.Message message : messages) {
            upsertOne(connection, evidenceId, message, capturedAt);
        }
    }

    void recordOperation(Connection connection, InvestigationEvidence.ContextBatch batch) throws SQLException {
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

    UUID operationEvidence(Connection connection, String operationKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT evidence_id
                FROM discord_investigation_context_operations
                WHERE operation_key = ?
                """)) {
            statement.setString(1, operationKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? UuidBytes.fromBytes(rows.getBytes("evidence_id")) : null;
            }
        }
    }

    private void insertOne(
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
            bind(statement, evidenceId, message, capturedAt);
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "evidence context was not inserted");
        }
    }

    private void upsertOne(
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
                ON DUPLICATE KEY UPDATE
                    author_user_id = VALUES(author_user_id), edited_at = VALUES(edited_at),
                    message_link = VALUES(message_link), message_content = VALUES(message_content),
                    attachment_metadata_json = VALUES(attachment_metadata_json),
                    last_observed_at = VALUES(last_observed_at), revision = revision + 1
                """)) {
            bind(statement, evidenceId, message, capturedAt);
            statement.executeUpdate();
        }
    }

    private void bind(
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
}
