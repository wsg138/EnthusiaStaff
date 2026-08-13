package net.enthusia.staff.paper.integration;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import net.enthusia.market.api.moderation.MarketBlacklistRemoval;
import net.enthusia.market.api.moderation.MarketBlacklistRequest;
import net.enthusia.market.api.moderation.MarketBlacklistResult;
import net.enthusia.market.api.moderation.MarketConfiscationApproval;
import net.enthusia.market.api.moderation.MarketModerationApi;
import net.enthusia.market.api.moderation.MarketOperationRecord;
import net.enthusia.market.api.moderation.MarketOperationRequest;
import net.enthusia.market.api.moderation.MarketOperationResult;
import net.enthusia.market.api.moderation.MarketOwnership;
import net.enthusia.market.api.moderation.MarketRestoreRequest;
import net.enthusia.market.api.moderation.MarketStallRecord;
import net.enthusia.market.api.moderation.StallBlacklistState;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.paper.market.MarketGateway;
import org.bukkit.plugin.ServicesManager;

/** Typed, provider-owned EnthusiaMarket service boundary. */
public final class MarketIntegration implements MarketGateway {
    private final IntegrationAvailability availability;
    private final String issue;
    private final MarketModerationApi api;

    private MarketIntegration(
            IntegrationAvailability availability,
            String issue,
            MarketModerationApi api
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.api = api;
    }

    public static MarketIntegration discover(ServicesManager services, boolean pluginEnabled) {
        Objects.requireNonNull(services, "services");
        if (!pluginEnabled) {
            return unavailable(
                    IntegrationAvailability.NOT_INSTALLED,
                    "EnthusiaMarket is not installed or enabled"
            );
        }
        try {
            MarketModerationApi provider = services.load(MarketModerationApi.class);
            if (provider == null) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "EnthusiaMarket did not register its moderation service"
                );
            }
            int version = provider.apiVersion();
            if (version != MarketModerationApi.API_VERSION) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "Market API version " + version
                                + " is incompatible with required version "
                                + MarketModerationApi.API_VERSION
                );
            }
            return new MarketIntegration(IntegrationAvailability.AVAILABLE, "", provider);
        } catch (LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "Market API could not be linked: " + exception.getClass().getSimpleName()
            );
        }
    }

    public CompletionStage<PlayerMarketStatus> status(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (availability != IntegrationAvailability.AVAILABLE) {
            return CompletableFuture.failedStage(new IllegalStateException(issue));
        }
        try {
            CompletionStage<List<MarketStallRecord>> stalls = Objects.requireNonNull(
                    api.findStalls(playerId),
                    "Market API returned no stall query stage"
            );
            CompletionStage<Optional<StallBlacklistState>> blacklist = Objects.requireNonNull(
                    api.getStallBlacklist(playerId),
                    "Market API returned no blacklist query stage"
            );
            return stalls.thenCombine(blacklist, MarketIntegration::toStatus);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedStage(
                    new IllegalStateException("Market status query could not start", exception)
            );
        }
    }

    public MarketModerationApi requireApi() {
        if (availability != IntegrationAvailability.AVAILABLE) {
            throw new IllegalStateException(issue);
        }
        return api;
    }

    @Override
    public CompletionStage<MarketOperationResult> prepare(MarketOperationRequest request) {
        return invoke(() -> api.prepare(request), "prepare");
    }

    @Override
    public CompletionStage<MarketOperationResult> confiscate(MarketConfiscationApproval approval) {
        return invoke(() -> api.confiscate(approval), "confiscate");
    }

    @Override
    public CompletionStage<MarketOperationResult> restore(MarketRestoreRequest request) {
        return invoke(() -> api.restore(request), "restore");
    }

    @Override
    public CompletionStage<MarketOperationResult> release(UUID operationId, String snapshotChecksum) {
        return invoke(() -> api.release(operationId, snapshotChecksum), "release");
    }

    @Override
    public CompletionStage<Optional<MarketOperationRecord>> findOperation(UUID operationId) {
        return invoke(() -> api.findOperation(operationId), "find operation");
    }

    @Override
    public CompletionStage<Optional<StallBlacklistState>> getBlacklist(UUID targetId) {
        return invoke(() -> api.getStallBlacklist(targetId), "read blacklist");
    }

    @Override
    public CompletionStage<MarketBlacklistResult> applyBlacklist(MarketBlacklistRequest request) {
        return invoke(() -> api.applyBlacklist(request), "apply blacklist");
    }

    @Override
    public CompletionStage<MarketBlacklistResult> removeBlacklist(MarketBlacklistRemoval removal) {
        return invoke(() -> api.removeBlacklist(removal), "remove blacklist");
    }

    public IntegrationAvailability availability() {
        return availability;
    }

    public String issue() {
        return issue;
    }

    public int apiVersion() {
        return requireApi().apiVersion();
    }

    private <T> CompletionStage<T> invoke(Supplier<CompletionStage<T>> invocation, String operation) {
        if (availability != IntegrationAvailability.AVAILABLE) {
            return CompletableFuture.failedStage(new IllegalStateException(issue));
        }
        try {
            return Objects.requireNonNull(
                    invocation.get(),
                    "Market API returned no stage for " + operation
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.failedStage(
                    new IllegalStateException("Market API could not " + operation, exception)
            );
        }
    }

    private static PlayerMarketStatus toStatus(
            List<MarketStallRecord> stalls,
            Optional<StallBlacklistState> blacklist
    ) {
        List<StallView> stallViews = Objects.requireNonNull(stalls, "stalls").stream()
                .map(MarketIntegration::stallView)
                .toList();
        Optional<BlacklistView> blacklistView = Objects.requireNonNull(blacklist, "blacklist")
                .map(MarketIntegration::blacklistView);
        return new PlayerMarketStatus(stallViews, blacklistView);
    }

    private static StallView stallView(MarketStallRecord stall) {
        MarketStallRecord checked = Objects.requireNonNull(stall, "stall");
        MarketOwnership ownership = checked.ownership();
        return new StallView(
                checked.id(),
                checked.world(),
                checked.state(),
                ownership.type().name(),
                ownership.id()
        );
    }

    private static BlacklistView blacklistView(StallBlacklistState blacklist) {
        return new BlacklistView(
                blacklist.status().name(),
                blacklist.expiresAt(),
                blacklist.caseId()
        );
    }

    private static MarketIntegration unavailable(
            IntegrationAvailability availability,
            String issue
    ) {
        return new MarketIntegration(availability, issue, null);
    }

    public record PlayerMarketStatus(List<StallView> stalls, Optional<BlacklistView> blacklist) {
        public PlayerMarketStatus {
            stalls = List.copyOf(stalls);
            blacklist = Objects.requireNonNull(blacklist, "blacklist");
        }
    }

    public record StallView(
            String id,
            String world,
            String state,
            String ownerType,
            Optional<String> ownerId
    ) {
        public StallView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(ownerType, "ownerType");
            ownerId = Objects.requireNonNull(ownerId, "ownerId");
        }
    }

    public record BlacklistView(
            String status,
            Optional<Instant> expirationAt,
            String caseId
    ) {
        public BlacklistView {
            Objects.requireNonNull(status, "status");
            expirationAt = Objects.requireNonNull(expirationAt, "expirationAt");
            Objects.requireNonNull(caseId, "caseId");
        }
    }
}
