package net.enthusia.staff.paper;

import java.util.logging.Level;
import java.util.logging.Logger;

final class PaperResourceCloser {
    private final Logger logger;

    PaperResourceCloser(Logger logger) {
        this.logger = logger;
    }

    void close(String component, AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logFailure(component + " cleanup was interrupted", exception);
        } catch (Exception exception) {
            logFailure(component + " cleanup failed", exception);
        }
    }

    private void logFailure(String message, Exception exception) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, message, exception);
        }
    }
}
