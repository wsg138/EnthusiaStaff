package net.enthusia.staff.paper.punishment;

import java.util.List;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;

sealed interface PunishmentRequestGuiState {
    record RequestView(PunishmentApprovalRequest request, String targetName) {
        public RequestView {
            if (request == null || targetName == null || targetName.isBlank()) {
                throw new IllegalArgumentException("punishment request view fields must be present");
            }
        }
    }

    record Queue(
            List<RequestView> requests,
            int page,
            int totalPages,
            int totalEntries
    ) implements PunishmentRequestGuiState {
        public Queue {
            if (requests == null || page < 0 || totalPages < 1 || page >= totalPages || totalEntries < requests.size()) {
                throw new IllegalArgumentException("punishment request queue fields are invalid");
            }
            requests = List.copyOf(requests);
        }

        static Queue page(List<RequestView> all, int requestedPage, int pageSize) {
            if (all == null || requestedPage < 0 || pageSize < 1) {
                throw new IllegalArgumentException("punishment request pagination fields are invalid");
            }
            List<RequestView> values = List.copyOf(all);
            int totalPages = Math.max(1, (values.size() + pageSize - 1) / pageSize);
            int page = Math.min(requestedPage, totalPages - 1);
            int from = Math.min(values.size(), page * pageSize);
            int to = Math.min(values.size(), from + pageSize);
            return new Queue(values.subList(from, to), page, totalPages, values.size());
        }

        boolean hasPrevious() {
            return page > 0;
        }

        boolean hasNext() {
            return page + 1 < totalPages;
        }
    }

    record Review(PunishmentApprovalLease lease, String targetName) implements PunishmentRequestGuiState {
        public Review {
            if (lease == null || targetName == null || targetName.isBlank()) {
                throw new IllegalArgumentException("punishment request review fields must be present");
            }
        }
    }

    record Denial(PunishmentApprovalLease lease, String targetName) implements PunishmentRequestGuiState {
        public Denial {
            if (lease == null || targetName == null || targetName.isBlank()) {
                throw new IllegalArgumentException("punishment request denial fields must be present");
            }
        }
    }

    record Details(RequestView view) implements PunishmentRequestGuiState {
        public Details {
            if (view == null) {
                throw new IllegalArgumentException("punishment request details must be present");
            }
        }
    }
}
