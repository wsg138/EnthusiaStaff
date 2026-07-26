package net.enthusia.staff.domain.economy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EconomyPrepareRequestTest {
    @Test
    void allRequiresNoExplicitAmount() {
        assertDoesNotThrow(() -> request(EconomyAmountMode.ALL, OptionalLong.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(EconomyAmountMode.ALL, OptionalLong.of(10L))
        );
    }

    @Test
    void customRequiresAPositiveAmount() {
        assertDoesNotThrow(() -> request(EconomyAmountMode.CUSTOM, OptionalLong.of(1L)));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(EconomyAmountMode.CUSTOM, OptionalLong.empty())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request(EconomyAmountMode.CUSTOM, OptionalLong.of(0L))
        );
    }

    private static EconomyPrepareRequest request(EconomyAmountMode mode, OptionalLong amount) {
        return new EconomyPrepareRequest(
                UUID.randomUUID(),
                "economy:test:" + UUID.randomUUID(),
                "ABC123",
                UUID.randomUUID(),
                UUID.randomUUID(),
                mode,
                amount,
                "SMP",
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
