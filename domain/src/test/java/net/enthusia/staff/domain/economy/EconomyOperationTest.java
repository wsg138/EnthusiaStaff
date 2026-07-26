package net.enthusia.staff.domain.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EconomyOperationTest {
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    void terminalReplayRequiresTheExactEvidence() {
        EconomyTerminalUpdate original = EconomyTerminalUpdate.committed(25L, CHECKSUM, "{}");
        EconomyOperation operation = committedOperation(original);

        assertTrue(operation.terminalMatches(original));
        assertFalse(operation.terminalMatches(EconomyTerminalUpdate.committed(
                24L,
                "b".repeat(64),
                "{\"changed\":true}"
        )));
    }

    private static EconomyOperation committedOperation(EconomyTerminalUpdate terminal) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new EconomyOperation(
                UUID.randomUUID(),
                "economy:test",
                "01ARZ3NDEKTSV4RR",
                UUID.randomUUID(),
                Optional.of(UUID.randomUUID()),
                EconomyAmountMode.CUSTOM,
                OptionalLong.of(75L),
                OptionalLong.of(100L),
                Optional.of("smp"),
                EconomyOperationState.COMMITTED,
                Optional.of(terminal.outcome()),
                1L,
                Optional.of(now.plusSeconds(30L)),
                Optional.of("c".repeat(64)),
                Optional.of(CHECKSUM),
                Optional.of("{}"),
                Optional.of("{}"),
                terminal.resultTotal(),
                terminal.resultChecksum(),
                terminal.resultSnapshotJson(),
                terminal.failureCode(),
                terminal.failureDetail(),
                now,
                now
        );
    }
}
