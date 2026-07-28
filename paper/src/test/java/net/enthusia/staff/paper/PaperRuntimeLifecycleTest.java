package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PaperRuntimeLifecycleTest {
    private static final String STORAGE = "storage";

    @Test
    void removesPublishedHandlesExactlyOnce() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();

        assertTrue(lifecycle.publishStorage(STORAGE));
        assertTrue(lifecycle.publishTask("task"));
        assertTrue(lifecycle.publishChannel("channel"));
        assertFalse(lifecycle.publishStorage("replacement"));
        assertFalse(lifecycle.publishTask("replacement"));
        assertFalse(lifecycle.publishChannel("replacement"));
        assertEquals("STORAGE", lifecycle.storageValue(String::toUpperCase));
        assertEquals("task", lifecycle.removeTask().orElseThrow());
        assertEquals("channel", lifecycle.removeChannel().orElseThrow());
        assertTrue(lifecycle.removeTask().isEmpty());
        assertTrue(lifecycle.removeChannel().isEmpty());
        assertTrue(lifecycle.removeStorageIf("other"::equals).isEmpty());
        assertEquals(STORAGE, lifecycle.removeStorageIf(STORAGE::equals).orElseThrow());
        assertTrue(lifecycle.removeStorage().isEmpty());
    }

    @Test
    void shutdownPreventsLatePublicationAndModeChanges() {
        PaperRuntimeLifecycle<String, String, String> lifecycle = new PaperRuntimeLifecycle<>();
        AtomicBoolean transitioned = new AtomicBoolean();
        AtomicBoolean lateAction = new AtomicBoolean();

        lifecycle.beginShutdown(() -> transitioned.set(true));

        assertTrue(lifecycle.stopping());
        assertTrue(transitioned.get());
        assertFalse(lifecycle.publishStorage(STORAGE));
        assertFalse(lifecycle.publishTask("task"));
        assertFalse(lifecycle.publishChannel("channel"));
        assertFalse(lifecycle.runIfRunning(() -> lateAction.set(true)));
        assertFalse(lateAction.get());
    }
}
