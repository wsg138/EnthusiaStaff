package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class VanishClientGameModeAdapterFactoryTest {
    @Test
    void acceptsOnlyExplicitlyProvenRuntimeIdentities() {
        assertTrue(supported(VanishClientGameModeAdapterFactory.PAPER_BRAND_ID, "26.2", 129));
        assertTrue(supported(VanishClientGameModeAdapterFactory.LEAF_BRAND_ID, "1.21.11", 115));

        assertFalse(supported(VanishClientGameModeAdapterFactory.PAPER_BRAND_ID, "26.2", 128));
        assertFalse(supported(VanishClientGameModeAdapterFactory.PAPER_BRAND_ID, "26.3", 134));
        assertFalse(supported(VanishClientGameModeAdapterFactory.LEAF_BRAND_ID, "1.21.11", 114));
        assertFalse(VanishClientGameModeAdapterFactory.supportedRuntime(
                VanishClientGameModeAdapterFactory.LEAF_BRAND_ID,
                "1.21.11",
                OptionalInt.empty()
        ).isPresent());
    }

    @Test
    void supportedIdentitiesStaySeparateEvenWhenTheyShareReflectionImplementation() {
        var runtimes = VanishClientGameModeAdapterFactory.supportedRuntimes();

        assertEquals(2, runtimes.size());
        assertNotEquals(runtimes.get(0).brandId(), runtimes.get(1).brandId());
        assertNotEquals(runtimes.get(0).label(), runtimes.get(1).label());
    }

    @Test
    void unsupportedRuntimeFailsClosedWithoutSevereLog() {
        CapturingHandler handler = new CapturingHandler();
        Logger logger = isolatedLogger(handler);

        VanishClientGameModeAdapter adapter = VanishClientGameModeAdapterFactory.select(
                logger,
                "unknown:server",
                "99.0",
                OptionalInt.of(1),
                ignored -> {
                    throw new AssertionError("unsupported runtime must not install an adapter");
                }
        );

        assertFalse(adapter.available());
        assertTrue(adapter.unavailableReason().contains("unsupported runtime identity"));
        assertTrue(handler.levels.contains(Level.WARNING));
        assertFalse(handler.levels.contains(Level.SEVERE));
    }

    private static boolean supported(String brandId, String minecraftVersion, int buildNumber) {
        return VanishClientGameModeAdapterFactory.supportedRuntime(
                brandId,
                minecraftVersion,
                OptionalInt.of(buildNumber)
        ).isPresent();
    }

    private static Logger isolatedLogger(Handler handler) {
        Logger logger = Logger.getLogger(
                VanishClientGameModeAdapterFactoryTest.class.getName() + System.nanoTime()
        );
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        return logger;
    }

    private static final class CapturingHandler extends Handler {
        private final List<Level> levels = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            levels.add(record.getLevel());
        }

        @Override public void flush() { }
        @Override public void close() { }
    }
}
