package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import org.junit.jupiter.api.Test;

final class InventoryOperationContextTest {
    private static final String SCOPE_ID = "network";
    private static final String SERVER_ID = "paper-1";

    @Test
    void preservesValidatedBackendIdentity() {
        Clock clock = Clock.systemUTC();

        InventoryOperationContext context =
                new InventoryOperationContext(clock, SCOPE_ID, SERVER_ID);

        assertSame(clock, context.clock());
        assertEquals(SCOPE_ID, context.scopeId());
        assertEquals(SERVER_ID, context.serverId());
    }

    @Test
    void rejectsMissingClock() {
        assertThrows(
                NullPointerException.class,
                () -> new InventoryOperationContext(null, SCOPE_ID, SERVER_ID)
        );
    }

    @Test
    void rejectsInvalidIdentifiers() {
        Clock clock = Clock.systemUTC();

        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryOperationContext(clock, " ", SERVER_ID)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventoryOperationContext(clock, SCOPE_ID, "x".repeat(65))
        );
    }
}
