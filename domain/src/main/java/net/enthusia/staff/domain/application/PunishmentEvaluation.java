package net.enthusia.staff.domain.application;

public sealed interface PunishmentEvaluation {
    record Allowed(PunishmentAssessment assessment) implements PunishmentEvaluation {
        public Allowed {
            if (assessment == null) {
                throw new IllegalArgumentException("assessment must be present");
            }
        }
    }

    record Rejected(String code, String message) implements PunishmentEvaluation {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("rejection code and message must be present");
            }
        }
    }
}
