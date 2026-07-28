package net.enthusia.staff.domain.application;

public sealed interface PunishmentDraftEvaluation {
    record Prepared(PunishmentDraft draft, PunishmentAssessment assessment)
            implements PunishmentDraftEvaluation {
        public Prepared {
            if (draft == null || assessment == null) {
                throw new IllegalArgumentException("prepared draft fields must be present");
            }
        }
    }

    record Rejected(String code, String message) implements PunishmentDraftEvaluation {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("rejection code and message must be present");
            }
        }
    }
}
