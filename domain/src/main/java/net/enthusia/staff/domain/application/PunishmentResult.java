package net.enthusia.staff.domain.application;

import net.enthusia.staff.common.CaseId;

public sealed interface PunishmentResult {
    record Accepted(CaseId caseId, boolean replayed) implements PunishmentResult {
    }

    record Rejected(String code, String message) implements PunishmentResult {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("rejection code and message must be present");
            }
        }
    }
}
