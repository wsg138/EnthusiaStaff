package net.enthusia.staff.domain.application;

public sealed interface PunishmentDraftConfirmation {
    record Applied(PunishmentResult.Accepted accepted) implements PunishmentDraftConfirmation {
        public Applied {
            if (accepted == null) {
                throw new IllegalArgumentException("applied punishment result must be present");
            }
        }
    }

    record Requested(PunishmentRequestResult.Submitted submitted) implements PunishmentDraftConfirmation {
        public Requested {
            if (submitted == null) {
                throw new IllegalArgumentException("submitted punishment request must be present");
            }
        }
    }

    record Rejected(String code, String message) implements PunishmentDraftConfirmation {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("punishment draft rejection fields must be present");
            }
        }
    }
}
