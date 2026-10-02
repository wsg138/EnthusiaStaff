package net.enthusia.staff.paper.economy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryOnlyCurrencyGatewayTest {
    @Test
    void movementLockContractRemainsAvailableWithoutCurrency() {
        InventoryOnlyCurrencyGateway gateway = new InventoryOnlyCurrencyGateway();
        UUID playerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();

        assertTrue(gateway.acquireMovementLock(playerId, operationId, Duration.ofMinutes(2)));
        assertTrue(gateway.renewMovementLock(playerId, operationId, Duration.ofMinutes(2)));
        assertFalse(gateway.isMovementLocked(playerId));
        assertTrue(gateway.releaseMovementLock(playerId, operationId));
    }

    @Test
    void economyMutationSurfaceStaysUnavailable() {
        InventoryOnlyCurrencyGateway gateway = new InventoryOnlyCurrencyGateway();

        assertThrows(UnsupportedOperationException.class, () -> gateway.snapshot(null));
    }

    @Test
    void invalidLeaseIsRejected() {
        InventoryOnlyCurrencyGateway gateway = new InventoryOnlyCurrencyGateway();

        assertThrows(IllegalArgumentException.class, () -> gateway.acquireMovementLock(
                UUID.randomUUID(), UUID.randomUUID(), Duration.ZERO
        ));
    }
}
