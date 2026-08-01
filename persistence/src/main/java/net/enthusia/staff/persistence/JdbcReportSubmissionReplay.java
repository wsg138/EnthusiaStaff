package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportSubmissionResult;

final class JdbcReportSubmissionReplay {
    static final String FINGERPRINT_FIELD = "requestFingerprint";

    private static final String EXISTING_SUBMISSION_SQL = """
            SELECT submission.report_id, submission.merged, audit.actor_id, audit.target_id,
                   audit.occurred_at,
                   JSON_UNQUOTE(JSON_EXTRACT(audit.event_json, '$.reasonId')) AS reason_id,
                   JSON_UNQUOTE(JSON_EXTRACT(audit.event_json, '$.requestFingerprint')) AS request_fingerprint
            FROM report_submission_keys submission
            JOIN audit_events audit ON audit.idempotency_key = submission.idempotency_key
            WHERE submission.idempotency_key = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper json;

    JdbcReportSubmissionReplay(DataSource dataSource, ObjectMapper json) {
        this.dataSource = dataSource;
        this.json = json;
    }

    ReportSubmissionResult find(Connection connection, CreateReportRequest request)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTING_SUBMISSION_SQL)) {
            statement.setString(1, request.idempotencyKey().value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                if (!sameSubmission(result, request)) {
                    return new ReportSubmissionResult.Rejected(
                            "IDEMPOTENCY_CONFLICT",
                            "The idempotency key belongs to a different report submission"
                    );
                }
                return new ReportSubmissionResult.Accepted(
                        UuidBytes.fromBytes(result.getBytes("report_id")),
                        result.getBoolean("merged"),
                        true
                );
            }
        }
    }

    ReportSubmissionResult findAfterConflict(CreateReportRequest request) {
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, request);
        } catch (SQLException | JsonProcessingException | RuntimeException exception) {
            return null;
        }
    }

    String fingerprint(CreateReportRequest request) throws JsonProcessingException {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reporterId", request.reporterId().toString());
        content.put("targetId", request.targetId().toString());
        content.put("reasonId", request.reasonId());
        content.put("description", request.description());
        content.put("serverId", request.serverId());
        content.put("worldId", request.worldId().orElse(null));
        content.put("reporterCoordinates", request.reporterCoordinates().orElse(null));
        content.put("targetCoordinates", request.targetCoordinates().orElse(null));
        content.put("createdAt", request.createdAt().toString());
        content.put("publicChatContext", publicChatFingerprint(request.publicChatContext()));
        content.put("privateMessageContext", privateMessageFingerprint(request.privateMessageContext()));
        content.put("targetClientEvidence", request.targetClientEvidence()
                .map(ClientEvidencePersistence::toJson)
                .orElse(null));
        return sha256(json.writeValueAsString(content));
    }

    private boolean sameSubmission(ResultSet result, CreateReportRequest request)
            throws SQLException, JsonProcessingException {
        boolean sameIdentity = uuid(result, "actor_id").equals(request.reporterId())
                && uuid(result, "target_id").equals(request.targetId())
                && request.reasonId().equals(result.getString("reason_id"));
        if (!sameIdentity) {
            return false;
        }
        String savedFingerprint = result.getString("request_fingerprint");
        if (savedFingerprint != null) {
            return savedFingerprint.equals(fingerprint(request));
        }
        return result.getTimestamp("occurred_at").toInstant().equals(
                request.createdAt().truncatedTo(ChronoUnit.MICROS)
        );
    }

    private static UUID uuid(ResultSet result, String column) throws SQLException {
        return UuidBytes.fromBytes(result.getBytes(column));
    }

    private static List<PublicChatFingerprint> publicChatFingerprint(
            List<CreateReportRequest.ChatContextMessage> messages
    ) {
        return messages.stream()
                .map(message -> new PublicChatFingerprint(
                        message.senderId().toString(),
                        message.senderName(),
                        message.body(),
                        message.sentAt().toString()
                ))
                .toList();
    }

    private static List<PrivateMessageFingerprint> privateMessageFingerprint(
            List<CreateReportRequest.PrivateMessageContextMessage> messages
    ) {
        return messages.stream()
                .map(message -> new PrivateMessageFingerprint(
                        message.senderId().toString(),
                        message.senderName(),
                        message.recipientId().toString(),
                        message.recipientName(),
                        message.body(),
                        message.sentAt().toString()
                ))
                .toList();
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record PublicChatFingerprint(
            String senderId,
            String senderName,
            String body,
            String sentAt
    ) {
    }

    private record PrivateMessageFingerprint(
            String senderId,
            String senderName,
            String recipientId,
            String recipientName,
            String body,
            String sentAt
    ) {
    }
}
