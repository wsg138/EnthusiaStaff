package net.enthusia.staff.domain.website;

public sealed interface AppealAcceptancePreparation {
    record Ready(boolean replayed) implements AppealAcceptancePreparation {
    }

    record Rejected(String code, String message) implements AppealAcceptancePreparation {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("Appeal rejection fields are required");
            }
        }
    }
}
