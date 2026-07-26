package net.enthusia.staff.velocity;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.OperationalMode;

final class VelocityRuntimeHealth {
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
            new Snapshot(OperationalMode.BOOTSTRAP, Map.of("bootstrap", "Initialization has not completed"))
    );

    Snapshot snapshot() {
        return snapshot.get();
    }

    void update(OperationalMode mode, Map<String, String> issues) {
        snapshot.set(new Snapshot(mode, Map.copyOf(issues)));
    }

    record Snapshot(OperationalMode mode, Map<String, String> issues) {
    }
}
