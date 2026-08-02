package net.enthusia.staff.paper.report;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportQueue;
import net.enthusia.staff.domain.report.ReportSummary;

sealed interface ReportGuiState {
    UUID viewerId();

    ReportQueue queue();

    int queuePage();

    record Queue(
            UUID viewerId,
            ReportQueue queue,
            List<ReportSummary> reports,
            int queuePage
    ) implements ReportGuiState {
        public Queue {
            validateBase(viewerId, queue, queuePage);
            if (reports == null || reports.size() > 100) {
                throw new IllegalArgumentException("report queue state must contain a bounded report list");
            }
            reports = List.copyOf(reports);
        }
    }

    record Detail(
            UUID viewerId,
            ReportQueue queue,
            int queuePage,
            ReportDetails details
    ) implements ReportGuiState {
        public Detail {
            validateBase(viewerId, queue, queuePage);
            if (details == null) {
                throw new IllegalArgumentException("report detail state must contain report details");
            }
        }
    }

    record Review(
            UUID viewerId,
            ReportQueue queue,
            int queuePage,
            ReportDetails details,
            ReportAction action,
            String note,
            UUID operationId
    ) implements ReportGuiState {
        public Review {
            validateBase(viewerId, queue, queuePage);
            if (details == null || action == null || note == null || note.isBlank() || operationId == null) {
                throw new IllegalArgumentException("report review state fields must be present");
            }
            note = note.trim();
            if (note.length() > 2_000) {
                throw new IllegalArgumentException("report action note exceeds 2000 characters");
            }
        }
    }

    private static void validateBase(UUID viewerId, ReportQueue queue, int queuePage) {
        if (viewerId == null || queue == null || queuePage < 0) {
            throw new IllegalArgumentException("report GUI state fields must be present");
        }
    }
}
