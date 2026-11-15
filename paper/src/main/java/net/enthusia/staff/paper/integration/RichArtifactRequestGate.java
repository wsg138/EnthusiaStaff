package net.enthusia.staff.paper.integration;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.api.chat.RichChatArtifact;

/**
 * Tracks optional image requests so provider shutdown immediately releases pending callers.
 * Late scheduler/worker completions cannot overwrite an already completed text-only fallback.
 */
final class RichArtifactRequestGate {
    private final Set<CompletableFuture<List<RichChatArtifact>>> pending =
            ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    CompletableFuture<List<RichChatArtifact>> begin() {
        CompletableFuture<List<RichChatArtifact>> result = new CompletableFuture<>();
        if (closed.get()) {
            result.complete(List.of());
            return result;
        }
        pending.add(result);
        result.whenComplete((ignored, failure) -> pending.remove(result));
        if (closed.get()) {
            result.complete(List.of());
        }
        return result;
    }

    boolean isClosed() {
        return closed.get();
    }

    boolean close() {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        for (CompletableFuture<List<RichChatArtifact>> result : pending) {
            result.complete(List.of());
        }
        return true;
    }
}
