package net.enthusia.staff.paper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.OperationalMode;

public final class RuntimeHealth {
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
            new Snapshot(OperationalMode.BOOTSTRAP, Map.of("bootstrap", "Initialization has not completed"), Instant.now())
    );

    public Snapshot snapshot() {
        return snapshot.get();
    }

    public void update(OperationalMode mode, Map<String, String> issues) {
        snapshot.set(new Snapshot(mode, issues, Instant.now()));
    }

    public record Snapshot(OperationalMode mode, Map<String, String> issues, Instant updatedAt) {
        public Snapshot {
            issues = Map.copyOf(new LinkedHashMap<>(issues));
        }
    }
}
