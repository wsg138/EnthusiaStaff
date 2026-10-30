package net.enthusia.staff.domain.ports;

import java.util.UUID;

/** Local Minecraft execution seam. Implementations must never retry a command internally. */
@FunctionalInterface
public interface CommandBridgeExecutor {
    Execution execute(UUID actorPlayerId, String normalizedCommand);

    record Execution(boolean accepted, String output) {
        public Execution {
            output = output == null ? "" : output;
        }

        public static Execution accepted(String output) {
            return new Execution(true, output);
        }

        public static Execution rejected() {
            return new Execution(false, "");
        }
    }
}
