package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.network.NetworkOutboxMessage;
import net.enthusia.staff.domain.ports.NetworkOutboxStore;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import org.junit.jupiter.api.Test;

final class PaperNetworkMessageHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString("018f42d3-5195-7b32-80a0-418fd8249258");
    private static final UUID TARGET_ID = UUID.fromString("018f42d3-5195-7b32-80a0-418fd8249259");
    private static final String INVALIDATE_PREFIX = "invalidate:";

    @Test
    void validSanctionInvalidatesBeforeRecordingTheInboxReceipt() {
        List<String> actions = new ArrayList<>();
        RecordingInbox inbox = new RecordingInbox(actions);
        PaperNetworkMessageHandler handler = new PaperNetworkMessageHandler(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                target -> actions.add(INVALIDATE_PREFIX + target)
        );

        assertTrue(handler.handle(inbox, "paper-smp", envelope(
                "PUNISHMENT_CREATED",
                "{\"targetId\":\"" + TARGET_ID + "\"}"
        )));

        assertEquals(List.of(INVALIDATE_PREFIX + TARGET_ID, "record:PUNISHMENT_CREATED"), actions);
        assertEquals(NOW, inbox.recordedAt);
    }

    @Test
    void malformedSanctionIsNotRecordedAsApplied() {
        List<String> actions = new ArrayList<>();
        RecordingInbox inbox = new RecordingInbox(actions);
        PaperNetworkMessageHandler handler = new PaperNetworkMessageHandler(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                target -> actions.add(INVALIDATE_PREFIX + target)
        );

        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                inbox,
                "paper-smp",
                envelope("SANCTION_CHANGED", "{\"targetId\":\"not-a-uuid\"}")
        ));

        assertTrue(actions.isEmpty());
    }

    @Test
    void unrelatedMessagesAreRecordedWithoutCacheInvalidation() {
        List<String> actions = new ArrayList<>();
        RecordingInbox inbox = new RecordingInbox(actions);
        PaperNetworkMessageHandler handler = new PaperNetworkMessageHandler(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                target -> actions.add(INVALIDATE_PREFIX + target)
        );

        assertTrue(handler.handle(inbox, "paper-smp", envelope("STAFF_CHAT", "{}")));

        assertEquals(List.of("record:STAFF_CHAT"), actions);
    }

    private ProtocolEnvelope envelope(String type, String payload) {
        return new ProtocolEnvelope(1, MESSAGE_ID, "velocity", type, NOW.toEpochMilli(), "nonce", payload, "mac");
    }

    private static final class RecordingInbox implements NetworkOutboxStore {
        private final List<String> actions;
        private Instant recordedAt;

        private RecordingInbox(List<String> actions) {
            this.actions = actions;
        }

        @Override
        public boolean recordInboxOnce(
                String consumerId,
                UUID messageId,
                String messageType,
                String outcomeJson,
                Instant now
        ) {
            actions.add("record:" + messageType);
            recordedAt = now;
            return true;
        }

        @Override
        public List<NetworkOutboxMessage> claimDue(String owner, int limit, Duration lease, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void prepareDeliveries(UUID messageId, Collection<String> serverIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> pendingDestinations(UUID messageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void acknowledgeDelivery(UUID messageId, String serverId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean complete(UUID messageId, String owner, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void retry(UUID messageId, String owner, Instant availableAt, String errorCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deadLetter(UUID messageId, String owner, String errorCode) {
            throw new UnsupportedOperationException();
        }
    }
}
