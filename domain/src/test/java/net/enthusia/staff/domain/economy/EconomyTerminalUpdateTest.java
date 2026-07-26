package net.enthusia.staff.domain.economy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class EconomyTerminalUpdateTest {
    @Test
    void commitRequiresVerifiedResultState() {
        assertDoesNotThrow(() -> EconomyTerminalUpdate.committed(
                10L,
                "a".repeat(64),
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
