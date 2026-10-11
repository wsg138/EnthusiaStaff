package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class CheatTesterSnapshotFailureCallbackTest {
    @Test
    void decodingOrItemDeserializationErrorsReachTheRecoveryCallback() {
        IllegalArgumentException decodeFailure = new IllegalArgumentException("corrupt snapshot");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CheatTesterSnapshotCodec.runWithFailureCallback(() -> {
            throw decodeFailure;
        }, failure::set);
        assertSame(decodeFailure, failure.get());
    }

    @Test
    void successfulRestorationDoesNotSignalFailure() {
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CheatTesterSnapshotCodec.runWithFailureCallback(completed::incrementAndGet,
                ignored -> failed.incrementAndGet());
        assertEquals(1, completed.get());
        assertEquals(0, failed.get());
    }
}
