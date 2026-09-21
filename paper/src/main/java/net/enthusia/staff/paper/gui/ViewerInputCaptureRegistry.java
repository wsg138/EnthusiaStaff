package net.enthusia.staff.paper.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Coordinates one pending chat capture per viewer and rejects stale scheduled callbacks. */
public final class ViewerInputCaptureRegistry<T> {
    private final Map<UUID, Capture<T>> pending = new HashMap<>();
    private final Map<UUID, Long> activeGenerations = new HashMap<>();
    private long nextGeneration;

    public synchronized void begin(UUID viewerId, T payload) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(payload, "payload");
        long generation = ++nextGeneration;
        pending.put(viewerId, new Capture<>(generation, payload));
        activeGenerations.put(viewerId, generation);
    }

    public synchronized Capture<T> take(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        return pending.remove(viewerId);
    }

    /** Claims an in-flight capture only if no newer flow has invalidated or replaced it. */
    public synchronized boolean claim(UUID viewerId, Capture<T> capture) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(capture, "capture");
        Long activeGeneration = activeGenerations.get(viewerId);
        if (!Objects.equals(activeGeneration, capture.generation())) {
            return false;
        }
        activeGenerations.remove(viewerId);
        return true;
    }

    /** Invalidates both a pending chat capture and an already-scheduled callback. */
    public synchronized void invalidate(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        pending.remove(viewerId);
        activeGenerations.remove(viewerId);
    }

    public record Capture<T>(long generation, T payload) {
        public Capture {
            Objects.requireNonNull(payload, "payload");
        }
    }
}
