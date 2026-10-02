package net.enthusia.staff.velocity;

import java.util.Objects;
import net.enthusia.staff.domain.OperationalMode;

final class VelocityLoginAdmissionPolicy {
    private VelocityLoginAdmissionPolicy() {
    }

    static boolean blocksInactiveLogin(OperationalMode mode, boolean activeObserved, boolean failClosed) {
        Objects.requireNonNull(mode, "mode");
        return mode == OperationalMode.BOOTSTRAP || mode == OperationalMode.MAINTENANCE
                || activeObserved && failClosed;
    }
}
