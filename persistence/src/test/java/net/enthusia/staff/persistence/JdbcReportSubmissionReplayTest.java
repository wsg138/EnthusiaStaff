package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.report.CreateReportRequest;
import org.junit.jupiter.api.Test;

final class JdbcReportSubmissionReplayTest {
    private static final UUID REPORTER_ID = UUID.fromString("6f09fc46-021c-44c4-95dc-d4c8c194aa45");
    private static final UUID TARGET_ID = UUID.fromString("303232ae-8bba-4605-b03b-c5bfaaa901af");
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T12:00:00Z");

    @Test
    void fingerprintIsIndependentOfMapperDateSettingsAndPreservesStringDateDigest() throws Exception {
        CreateReportRequest request = request();
        ObjectMapper stringDates = mapper(false);
        ObjectMapper timestampDates = mapper(true);
        String expected = previousStringDateFingerprint(stringDates, request);

        assertEquals(expected, new JdbcReportSubmissionReplay(null, stringDates).fingerprint(request));
        assertEquals(expected, new JdbcReportSubmissionReplay(null, timestampDates).fingerprint(request));
    }

    private static ObjectMapper mapper(boolean timestamps) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, timestamps);
    }

    private static String previousStringDateFingerprint(ObjectMapper mapper, CreateReportRequest request)
            throws Exception {
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
        content.put("publicChatContext", request.publicChatContext());
        content.put("privateMessageContext", request.privateMessageContext());
        content.put("targetClientEvidence", null);
        return sha256(mapper.writeValueAsString(content));
    }

    private static CreateReportRequest request() {
        return new CreateReportRequest(
                new IdempotencyKey("report:fingerprint"),
                REPORTER_ID,
                TARGET_ID,
                "chat.abuse",
                "Report description",
                "paper-test",
                Optional.of("minecraft:overworld"),
                Optional.of("1,64,1"),
                Optional.of("2,64,2"),
                CREATED_AT,
                List.of(new CreateReportRequest.ChatContextMessage(
                        REPORTER_ID, "Reporter", "Public evidence", CREATED_AT
                )),
                List.of(new CreateReportRequest.PrivateMessageContextMessage(
                        REPORTER_ID, "Reporter", TARGET_ID, "Target", "Private evidence", CREATED_AT
                )),
                Optional.empty()
        );
    }

    private static String sha256(String content) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
