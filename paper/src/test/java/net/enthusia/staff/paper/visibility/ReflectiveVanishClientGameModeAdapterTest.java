package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ReflectiveVanishClientGameModeAdapterTest {
    @Test
    void supportedRuntimeAbiFailureFailsClosedAndLogsSevere() {
        CapturingHandler handler = new CapturingHandler();
        Logger logger = isolatedLogger(handler);

        VanishClientGameModeAdapter adapter = ReflectiveVanishClientGameModeAdapter.install(
                logger,
                "Paper 26.2 build 129",
                () -> {
                    throw new ClassNotFoundException("forced ABI failure");
                }
        );

        assertFalse(adapter.available());
        assertTrue(adapter.unavailableReason().contains("ABI validation"));
        assertTrue(handler.levels.contains(Level.SEVERE));
    }

    private static Logger isolatedLogger(Handler handler) {
        Logger logger = Logger.getLogger(
                ReflectiveVanishClientGameModeAdapterTest.class.getName() + System.nanoTime()
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
