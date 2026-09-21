package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class PaperFreezeNetworkMessageHandlerTest {
    private static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    private static final UUID TARGET = UUID.fromString("10000000-0000-0000-0000-000000000011");

    @Test
    void successfulFreezeReconciliationPrecedesInboxReceipt() {
        List<String> actions = new ArrayList<>();
        RecordingInbox inbox = new RecordingInbox(actions);
        PaperNetworkMessageHandler handler = handler(actions, true);

        assertTrue(handler.handle(inbox, "paper-a", freezeEnvelope(TARGET)));
        assertEquals(List.of("reconcile:" + TARGET, "receipt"), actions);
    }

    @Test
    void failedFreezeReconciliationLeavesMessageUnacknowledgedAndUnrecorded() {
        List<String> actions = new ArrayList<>();
        RecordingInbox inbox = new RecordingInbox(actions);
        PaperNetworkMessageHandler handler = handler(actions, false);

        assertFalse(handler.handle(inbox, "paper-a", freezeEnvelope(TARGET)));
        assertEquals(List.of("reconcile:" + TARGET), actions);
    }

    @Test
    void malformedFreezeTargetFailsBeforeReceipt() {
        List<String> actions = new ArrayList<>();
        RecordingInbox inbox = new RecordingInbox(actions);
        PaperNetworkMessageHandler handler = handler(actions, true);
        ProtocolEnvelope malformed = envelope("FREEZE_CHANGED", "{\"targetId\":\"not-a-uuid\"}");

        assertThrows(IllegalArgumentException.class, () -> handler.handle(inbox, "paper-a", malformed));
        assertTrue(actions.isEmpty());
    }

    private static PaperNetworkMessageHandler handler(List<String> actions, boolean result) {
        return new PaperNetworkMessageHandler(
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                target -> actions.add("sanction:" + target),
                target -> {
                    actions.add("reconcile:" + target);
                    return result;
                }
        );
    }

    private static ProtocolEnvelope freezeEnvelope(UUID target) {
        return envelope("FREEZE_CHANGED", "{\"targetId\":\"" + target + "\"}");
    }

    private static ProtocolEnvelope envelope(String type, String payload) {
        return new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "velocity",
                type,
                NOW.toEpochMilli(),
                "nonce",
                payload,
                "mac"
        );
    }

    private static final class RecordingInbox implements NetworkOutboxStore {
        private final List<String> actions;

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
            actions.add("receipt");
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
