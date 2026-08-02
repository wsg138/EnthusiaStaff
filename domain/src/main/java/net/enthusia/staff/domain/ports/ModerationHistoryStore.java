package net.enthusia.staff.domain.ports;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.history.CaseHistoryDetail;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryPage;

public interface ModerationHistoryStore {
    ModerationHistoryPage page(
            UUID subjectId,
            int page,
            int pageSize,
            HistoryQueryOptions options
    );

    Optional<CaseHistoryDetail> caseDetail(CaseId caseId, HistoryQueryOptions options);
}
