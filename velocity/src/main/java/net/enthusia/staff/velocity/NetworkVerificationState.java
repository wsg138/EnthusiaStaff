package net.enthusia.staff.velocity;

import java.util.List;
import java.util.Set;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.protocol.PersistentChannelServer;

final class NetworkVerificationState {
    private NetworkVerificationState() {
    }

    record Snapshot(
            OperationalMode mode,
            MariaDbRuntime runtime,
            PersistentChannelServer channel,
            Set<String> expectedBackends,
            Set<String> connectedBackends,
            boolean networkIdentityReady,
            boolean discordWebhookReady,
            boolean websiteBridgeReady
    ) {
        Snapshot {
            expectedBackends = expectedBackends == null ? Set.of() : Set.copyOf(expectedBackends);
            connectedBackends = connectedBackends == null ? Set.of() : Set.copyOf(connectedBackends);
        }
    }

    record Cutover(boolean allowed, boolean evidencePresent, List<String> blockers) {
        Cutover {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }
}
