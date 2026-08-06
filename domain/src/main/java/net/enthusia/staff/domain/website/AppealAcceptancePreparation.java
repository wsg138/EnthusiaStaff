package net.enthusia.staff.domain.website;

import java.util.OptionalLong;

public sealed interface AppealAcceptancePreparation {
    record Ready(
            boolean replayed,
            OptionalLong pendingRevision,
            boolean finalized
    ) implements AppealAcceptancePreparation {
        public Ready {
            if (pendingRevision == null || pendingRevision.isPresent() && pendingRevision.orElseThrow() < 0) {
                throw new IllegalArgumentException("Appeal pending revision is invalid");
            }
            if (finalized && pendingRevision.isPresent()) {
                throw new IllegalArgumentException("A finalized appeal cannot retain a pending revision");
            }
        }

        public Ready(boolean replayed) {
            this(replayed, OptionalLong.empty(), false);
        }

        public Ready(boolean replayed, OptionalLong pendingRevision) {
            this(replayed, pendingRevision, false);
        }
    }

    record Rejected(String code, String message) implements AppealAcceptancePreparation {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("Appeal rejection fields are required");
            }
        }
    }
}
