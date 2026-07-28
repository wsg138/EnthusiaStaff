package net.enthusia.staff.domain.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;

public interface CaseReviewStore {
    Optional<CaseReview> find(CaseId caseId);

    List<CaseReview> recent(UUID targetId, int limit);
}
