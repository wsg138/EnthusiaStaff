package net.enthusia.staff.paper.punishment;

import java.util.List;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;

sealed interface PunishmentRequestGuiState {
    record Queue(List<PunishmentApprovalRequest> requests) implements PunishmentRequestGuiState {
        Queue {
            if (requests == null) {
                throw new IllegalArgumentException("punishment request queue must be present");
            }
            requests = List.copyOf(requests);
        }
    }

    record Review(PunishmentApprovalLease lease) implements PunishmentRequestGuiState {
        Review {
            if (lease == null) {
                throw new IllegalArgumentException("punishment request review lease must be present");
            }
        }
    }

    record Denial(PunishmentApprovalLease lease) implements PunishmentRequestGuiState {
        Denial {
            if (lease == null) {
                throw new IllegalArgumentException("punishment request denial lease must be present");
            }
        }
    }
}
