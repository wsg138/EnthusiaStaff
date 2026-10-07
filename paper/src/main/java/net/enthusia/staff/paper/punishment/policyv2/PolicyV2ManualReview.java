package net.enthusia.staff.paper.punishment.policyv2;

import java.util.List;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.IncidentFinding;
import net.enthusia.staff.domain.policyv2.PolicyResolution;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;

public record PolicyV2ManualReview(
        PolicyV2ManualDraft draft,
        PolicySnapshot snapshot,
        IncidentFinding finding,
        List<BehavioralHistoryEntry> historyInputs,
        PolicyResolution resolution,
        ApprovalRoute route
) {
    public enum ApprovalRoute {
        DIRECT_CONFIRM,
        APPROVAL_REQUIRED,
        ADMIN_FOUNDER_REVIEW
    }

    public PolicyV2ManualReview {
        if (draft == null || snapshot == null || finding == null || historyInputs == null
                || resolution == null || route == null) {
            throw new IllegalArgumentException("Policy v2 review fields must be present");
        }
        historyInputs = List.copyOf(historyInputs);
    }
}
