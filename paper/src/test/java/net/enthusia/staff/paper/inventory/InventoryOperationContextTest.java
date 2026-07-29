package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import org.junit.jupiter.api.Test;

final class InventoryOperationContextTest {
    @Test
    void preservesValidatedBackendIdentity() {
        Clock clock = Clock.systemUTC();

        InventoryOperationContext context =
                new InventoryOperationContext(clock, "network", "paper-1");

        assertSame(clock, context.clock());
        assertEquals("network", context.scopeId());
        assertEquals("paper-1", context.serverId());
    }

    @Test
    void rejectsMissingClock() {
        assertThrows(
                NullPointerException.class,
                () -> new InventoryOperationContext(null, "network", "paper-1")
        );
    }

    @Test
    void rejectsInvalidIdentifiers() {
        Clock clock = Clock.systemUTC();

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryOperationContext(clock, " ", "paper-1")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryOperationContext(clock, "network", "x".repeat(65))
        );
    }
}
