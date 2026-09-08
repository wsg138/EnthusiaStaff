package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.market.MarketComplianceOperation;
import net.enthusia.staff.domain.market.MarketComplianceRequest;
import net.enthusia.staff.domain.market.MarketComplianceResult;
import net.enthusia.staff.domain.market.MarketComplianceUpdate;

public interface MarketComplianceStore {
    MarketComplianceResult start(MarketComplianceRequest request);

    Optional<MarketComplianceOperation> find(UUID operationId);

    MarketComplianceResult update(
            UUID operationId,
            long expectedJournalRevision,
            MarketComplianceUpdate update
    );

    List<MarketComplianceOperation> recoverable(int limit);

    int emitDueReviewAlerts(Instant now, int limit);
}
