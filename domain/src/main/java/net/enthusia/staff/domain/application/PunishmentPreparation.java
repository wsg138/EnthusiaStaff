package net.enthusia.staff.domain.application;

/** Policy-evaluated punishment plan that has not yet crossed the persistence boundary. */
public sealed interface PunishmentPreparation {
    record Prepared(PunishmentPlan plan) implements PunishmentPreparation {
        public Prepared {
            if (plan == null) {
                throw new IllegalArgumentException("prepared punishment plan must be present");
            }
        }
    }

    record Rejected(String code, String message) implements PunishmentPreparation {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("punishment rejection fields must be present");
            }
        }
    }
}
