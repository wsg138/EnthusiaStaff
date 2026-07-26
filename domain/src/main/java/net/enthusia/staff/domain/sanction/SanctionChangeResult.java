package net.enthusia.staff.domain.sanction;

public sealed interface SanctionChangeResult {
    record Applied(int affectedSanctions, boolean replayed) implements SanctionChangeResult {
        public Applied {
            if (affectedSanctions < 0) {
                throw new IllegalArgumentException("affected sanction count cannot be negative");
            }
        }
    }

    record Rejected(String code, String message) implements SanctionChangeResult {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("rejection fields must be present");
            }
        }
    }
}
