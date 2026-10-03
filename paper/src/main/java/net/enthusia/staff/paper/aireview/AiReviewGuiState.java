package net.enthusia.staff.paper.aireview;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;

sealed interface AiReviewGuiState {
    UUID viewerId();

    long generation();

    record Queue(
            UUID viewerId,
            long generation,
            List<ReviewItem> items,
            int page,
            boolean authoritative,
            String issue
    ) implements AiReviewGuiState {
        public Queue {
            requireViewer(viewerId, generation);
            items = List.copyOf(items == null ? List.of() : items);
            if (page < 0) {
                throw new IllegalArgumentException("queue page cannot be negative");
            }
        }
    }

    record Detail(
            UUID viewerId,
            long generation,
            EventDetails details,
            int returnPage
    ) implements AiReviewGuiState {
        public Detail {
            requireViewer(viewerId, generation);
            if (details == null || returnPage < 0) {
                throw new IllegalArgumentException("detail state is invalid");
            }
        }
    }

    record LabelPicker(
            UUID viewerId,
            long generation,
            EventDetails details,
            int returnPage,
            List<String> labels,
            int page
    ) implements AiReviewGuiState {
        public LabelPicker {
            requireViewer(viewerId, generation);
            if (details == null || returnPage < 0 || page < 0) {
                throw new IllegalArgumentException("label picker state is invalid");
            }
            labels = List.copyOf(labels == null ? List.of() : labels);
        }
    }

    record Confirm(
            UUID viewerId,
            long generation,
            EventDetails details,
            int returnPage,
            WriteKind kind,
            CorrectionDecision decision,
            String proposalId,
            String description,
            boolean adminRequested
    ) implements AiReviewGuiState {
        public Confirm {
            requireViewer(viewerId, generation);
            if (details == null || returnPage < 0 || kind == null || description == null || description.isBlank()) {
                throw new IllegalArgumentException("confirm state is invalid");
            }
            if (kind == WriteKind.CORRECT && decision == null) {
                throw new IllegalArgumentException("correction confirmation requires a decision");
            }
            if (kind == WriteKind.REJECT && (proposalId == null || proposalId.isBlank())) {
                throw new IllegalArgumentException("rejection confirmation requires a proposal id");
            }
        }
    }

    enum WriteKind {
        CORRECT,
        REJECT
    }

    private static void requireViewer(UUID viewerId, long generation) {
        if (viewerId == null || generation < 0) {
            throw new IllegalArgumentException("viewer and generation must be valid");
        }
    }
}
