package net.enthusia.staff.paper.market;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.enthusia.market.api.moderation.MarketBlacklistRemoval;
import net.enthusia.market.api.moderation.MarketBlacklistRequest;
import net.enthusia.market.api.moderation.MarketBlacklistResult;
import net.enthusia.market.api.moderation.MarketConfiscationApproval;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationRequest;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.MarketRestoreRequest;
import net.enthusia.market.api.moderation.StallBlacklistState;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;

public interface MarketGateway {
    IntegrationAvailability availability();

    String issue();

    CompletionStage<MarketOperationResult> prepare(MarketOperationRequest request);

    CompletionStage<MarketOperationResult> confiscate(MarketConfiscationApproval approval);

    CompletionStage<MarketOperationResult> restore(MarketRestoreRequest request);

    CompletionStage<MarketOperationResult> release(UUID operationId, String snapshotChecksum);

    CompletionStage<Optional<MarketOperationRecord>> findOperation(UUID operationId);

    CompletionStage<Optional<StallBlacklistState>> getBlacklist(UUID targetId);

    CompletionStage<MarketBlacklistResult> applyBlacklist(MarketBlacklistRequest request);

    CompletionStage<MarketBlacklistResult> removeBlacklist(MarketBlacklistRemoval removal);
}
