package net.enthusia.staff.domain.application;

import net.enthusia.staff.common.CaseId;

public sealed interface PunishmentRequestResult {
    record Submitted(PunishmentApprovalRequest request, boolean replayed) implements PunishmentRequestResult {
        public Submitted {
            if (request == null) {
                throw new IllegalArgumentException("submitted punishment request must be present");
            }
        }
    }

    record Leased(PunishmentApprovalLease lease) implements PunishmentRequestResult {
        public Leased {
            if (lease == null) {
                throw new IllegalArgumentException("punishment approval lease must be present");
            }
        }
    }

    record Approved(PunishmentApprovalRequest request, CaseId caseId, boolean replayed)
            implements PunishmentRequestResult {
        public Approved {
            if (request == null || caseId == null
                    || request.status() != PunishmentRequestStatus.APPROVED
                    || !caseId.equals(request.resultingCaseId())) {
                throw new IllegalArgumentException("approved punishment request fields must agree");
            }
        }
    }

    record Denied(PunishmentApprovalRequest request, boolean replayed) implements PunishmentRequestResult {
        public Denied {
            if (request == null || request.status() != PunishmentRequestStatus.DENIED) {
                throw new IllegalArgumentException("denied punishment request must be present and denied");
            }
        }
    }

    record Rejected(String code, String message) implements PunishmentRequestResult {
        public Rejected {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("punishment request rejection fields must be present");
            }
        }
    }
}
