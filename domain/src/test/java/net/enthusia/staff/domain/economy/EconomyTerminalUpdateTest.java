package net.enthusia.staff.domain.economy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class EconomyTerminalUpdateTest {
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    void commitRequiresVerifiedResultState() {
        assertDoesNotThrow(() -> EconomyTerminalUpdate.committed(
                10L,
                CHECKSUM,
                "{}"
        ));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyTerminalUpdate(
                        EconomyTerminalOutcome.COMMITTED,
                        OptionalLong.of(10L),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()
                )
        );
    }

    @Test
    void rollbackRejectsATotalWithoutVerifiedSnapshot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTerminalUpdate.rolledBack(
                        OptionalLong.of(10L),
                        Optional.empty(),
                        Optional.empty(),
                        "ROLLBACK_FAILED",
                        "Rollback evidence is incomplete"
                )
        );
    }

    @Test
    void rollbackRejectsAVerifiedSnapshotWithoutTotal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTerminalUpdate.rolledBack(
                        OptionalLong.empty(),
                        Optional.of(CHECKSUM),
                        Optional.of("{}"),
                        "ROLLBACK_FAILED",
                        "Rollback evidence is incomplete"
                )
        );
    }

    @Test
    void rollbackAcceptsCompleteOrAbsentEvidence() {
        assertDoesNotThrow(() -> EconomyTerminalUpdate.rolledBack(
                OptionalLong.of(10L),
                Optional.of(CHECKSUM),
                Optional.of("{}"),
                "ROLLBACK_VERIFIED",
                "Rollback restored the exact before state"
        ));
        assertDoesNotThrow(() -> EconomyTerminalUpdate.rolledBack(
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                "ROLLBACK_UNAPPLIED",
                "No assets changed"
        ));
    }

    @Test
    void quarantineRejectsPartialResultEvidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTerminalUpdate.quarantined(
                        OptionalLong.of(10L),
                        Optional.empty(),
                        Optional.empty(),
                        "QUARANTINE_REQUIRED",
                        "Observed result evidence is incomplete"
                )
        );
    }

    @Test
    void quarantineRequiresStableFailureCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EconomyTerminalUpdate(
                        EconomyTerminalOutcome.QUARANTINED,
                        OptionalLong.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("ambiguous")
                )
        );
    }
}
