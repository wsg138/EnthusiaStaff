package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PaperReasonPolicyBootstrapTest {
    private static final String POLICY_RESOURCE = "/reason-policies.yml";
    private static final String POLICY_FILENAME = "reason-policies.yml";
    private static final String INSTALLED_VERSION = "older-test-version";
    private static final String VERSION_FIELD = "version: ";

    @TempDir
    Path directory;

    @Test
    void warnsOnOutdatedExternalPolicyWithoutOverwritingIt() throws IOException {
        String bundled = bundledPolicy();
        String installed = bundled.replaceFirst(
                "(?m)^version: .+$", VERSION_FIELD + "\"" + INSTALLED_VERSION + "\""
        );
        Path file = directory.resolve(POLICY_FILENAME);
        Files.writeString(file, installed);
        List<LogRecord> records = new ArrayList<>();
        AtomicReference<String> degraded = new AtomicReference<>();
        var bootstrap = new PaperReasonPolicyBootstrap(capturingLogger(records),
                () -> inputStream(bundled));

        var result = bootstrap.load(file, degraded::set);

        assertTrue(result.isPresent());
        assertEquals(INSTALLED_VERSION, result.orElseThrow().activeVersion());
        assertEquals(installed, Files.readString(file));
        assertTrue(records.stream().anyMatch(r -> r.getLevel() == Level.WARNING
                && r.getMessage().contains(INSTALLED_VERSION)
                && r.getMessage().contains("bundled version")
                && r.getMessage().contains("Existing settings remain unchanged")));
        assertEquals(null, degraded.get());
    }

    @Test
    void matchingPolicyVersionDoesNotProduceAWarning() throws IOException {
        String bundled = bundledPolicy();
        Path file = directory.resolve(POLICY_FILENAME);
        Files.writeString(file, bundled);
        List<LogRecord> records = new ArrayList<>();

        var result = new PaperReasonPolicyBootstrap(capturingLogger(records),
                () -> inputStream(bundled)).load(file, reason -> { });

        assertTrue(result.isPresent());
        assertFalse(records.stream().anyMatch(r -> r.getLevel() == Level.WARNING));
    }

    @Test
    void missingPackagedPolicyDoesNotDisableExistingValidConfiguration() throws IOException {
        Path file = directory.resolve(POLICY_FILENAME);
        Files.writeString(file, bundledPolicy());
        List<LogRecord> records = new ArrayList<>();

        var result = new PaperReasonPolicyBootstrap(capturingLogger(records), () -> null)
                .load(file, reason -> { throw new AssertionError(reason); });

        assertTrue(result.isPresent());
        assertTrue(records.stream().anyMatch(r -> r.getMessage()
                .contains("Packaged punishment policy unavailable")));
    }

    @Test
    void invalidInstalledPolicyStillDisablesPunishments() throws IOException {
        Path file = directory.resolve(POLICY_FILENAME);
        Files.writeString(file, "invalid yaml: [");
        AtomicReference<String> reason = new AtomicReference<>();
        List<LogRecord> records = new ArrayList<>();

        String bundled = bundledPolicy();
        var result = new PaperReasonPolicyBootstrap(capturingLogger(records),
                () -> inputStream(bundled)).load(file, reason::set);

        assertTrue(result.isEmpty());
        assertTrue(reason.get() != null && !reason.get().isBlank());
        assertTrue(records.stream().anyMatch(r -> r.getLevel() == Level.SEVERE));
    }

    private static InputStream inputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String bundledPolicy() throws IOException {
        try (InputStream stream = PaperReasonPolicyBootstrapTest.class.getResourceAsStream(POLICY_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Test bundled punishment policy is unavailable");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Logger capturingLogger(List<LogRecord> records) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        });
        return logger;
    }
}
