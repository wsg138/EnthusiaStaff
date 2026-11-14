package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ClientEvidenceOwnerCaptureTest {
    private static final String SNAPSHOT = "snapshot";
    private static final String UNEXPECTED_FAILURE = "unexpected failure";
    @Test
    void captureOnlyRunsOnTargetOwnerCallback() {
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger deliveries = new AtomicInteger();
        ClientEvidenceOwnerCapture.capture(
                (callback, retired) -> { owner.set(callback); return true; },
                () -> true,
                () -> { captures.incrementAndGet(); return SNAPSHOT; },
                result -> { assertEquals(SNAPSHOT, result); deliveries.incrementAndGet(); },
                () -> fail("unexpected retirement"),
                error -> fail(UNEXPECTED_FAILURE, error));
        assertNotNull(owner.get());
        assertEquals(0, captures.get());
        assertEquals(0, deliveries.get());
        owner.get().run();
        assertEquals(1, captures.get());
        assertEquals(1, deliveries.get());
    }

    @Test
    void targetDisconnectedBeforeCaptureFailsClosed() {
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
        ClientEvidenceOwnerCapture.capture(
                (callback, retired) -> { owner.set(callback); return true; },
                online::get,
                () -> { captures.incrementAndGet(); return SNAPSHOT; },
                result -> fail("should not deliver"),
                unavailable::incrementAndGet,
                error -> fail(UNEXPECTED_FAILURE, error));
        online.set(false);
        owner.get().run();
        assertEquals(0, captures.get());
        assertEquals(1, unavailable.get());
    }

    @Test
    void schedulerRetirementOrRejectionCannotCapture() {
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
        ClientEvidenceOwnerCapture.capture(
                (callback, retired) -> { retired.run(); return true; },
                () -> true,
                () -> { captures.incrementAndGet(); return SNAPSHOT; },
                result -> fail("should not deliver"),
                unavailable::incrementAndGet,
                error -> fail(UNEXPECTED_FAILURE, error));
        ClientEvidenceOwnerCapture.capture(
                (callback, retired) -> false,
                () -> true,
                () -> { captures.incrementAndGet(); return SNAPSHOT; },
                result -> fail("should not deliver"),
                unavailable::incrementAndGet,
                error -> fail(UNEXPECTED_FAILURE, error));
        assertEquals(0, captures.get());
        assertEquals(2, unavailable.get());
    }

    @Test
    void delayedOwnerCallbackAfterRetirementCannotReadStaleTarget() {
        AtomicReference<Runnable> owner = new AtomicReference<>();
        AtomicReference<Runnable> retired = new AtomicReference<>();
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
        ClientEvidenceOwnerCapture.capture(
                (callback, onRetire) -> {
                    owner.set(callback);
                    retired.set(onRetire);
                    return true;
                },
                () -> true,
                () -> { captures.incrementAndGet(); return SNAPSHOT; },
                result -> fail("stale target cannot disclose"),
                unavailable::incrementAndGet,
                error -> fail(UNEXPECTED_FAILURE, error));
        retired.get().run();
        owner.get().run();
        assertEquals(0, captures.get());
        assertEquals(1, unavailable.get());
    }

    @Test
    void thrownSchedulerAndCaptureFailuresAreContained() {
        AtomicInteger failures = new AtomicInteger();
        ClientEvidenceOwnerCapture.capture(
                (callback, retired) -> { throw new IllegalStateException("retired"); },
                () -> true, () -> SNAPSHOT, result -> fail("no delivery"),
                () -> fail("no retirement"), error -> failures.incrementAndGet());
        ClientEvidenceOwnerCapture.capture(
                (callback, retired) -> { callback.run(); return true; },
                () -> true,
                () -> { throw new IllegalStateException("capture failed"); },
                result -> fail("no delivery"), () -> fail("no retirement"),
                error -> failures.incrementAndGet());
        assertEquals(2, failures.get());
    }

    @Test
    void commandUsesTargetOwnerAndSenderOwnerBeforeEvidenceDisclosureOrSave() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/ClientCommand.java"));
        assertTrue(source.contains("ClientEvidenceOwnerCapture.capture("));
        assertTrue(source.contains("target.getScheduler().execute("));
        assertTrue(source.contains("responses.execute(sender,"));
        assertTrue(source.contains("sender.hasPermission(PERMISSION)"));
        assertFalse(source.contains("getGlobalRegionScheduler().execute("));
        assertFalse(source.contains("ClientEvidenceSnapshot snapshot = collector.capture(target);"));
    }
}
