package net.enthusia.staff.paper.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.ServicesManager;

public final class MarketIntegration {
    private static final int REQUIRED_API_VERSION = 1;

    private final IntegrationAvailability availability;
    private final String issue;
    private final Object api;
    private final Method findStalls;
    private final Method getBlacklist;
    private final Method stallId;
    private final Method stallWorld;
    private final Method stallState;
    private final Method stallOwnership;
    private final Method ownershipType;
    private final Method ownershipId;
    private final Method blacklistStatus;
    private final Method blacklistExpiration;
    private final Method blacklistCaseId;

    private MarketIntegration(
            IntegrationAvailability availability,
            String issue,
            Object api,
            Method findStalls,
            Method getBlacklist,
            Method stallId,
            Method stallWorld,
            Method stallState,
            Method stallOwnership,
            Method ownershipType,
            Method ownershipId,
            Method blacklistStatus,
            Method blacklistExpiration,
            Method blacklistCaseId
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.api = api;
        this.findStalls = findStalls;
        this.getBlacklist = getBlacklist;
        this.stallId = stallId;
        this.stallWorld = stallWorld;
        this.stallState = stallState;
        this.stallOwnership = stallOwnership;
        this.ownershipType = ownershipType;
        this.ownershipId = ownershipId;
        this.blacklistStatus = blacklistStatus;
        this.blacklistExpiration = blacklistExpiration;
        this.blacklistCaseId = blacklistCaseId;
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
            Class<?> apiClass = Class.forName(
                    "net.badgersmc.em.api.moderation.MarketModerationApi"
            );
            Class<?> stallClass = Class.forName(
                    "net.badgersmc.em.api.moderation.MarketStallRecord"
            );
            Class<?> ownershipClass = Class.forName(
                    "net.badgersmc.em.api.moderation.MarketOwnership"
            );
            Class<?> blacklistClass = Class.forName(
                    "net.badgersmc.em.api.moderation.StallBlacklistState"
            );
            Object api = services.load(apiClass);
            if (api == null) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "EnthusiaMarket did not register its moderation service"
                );
            }
            int version = (int) apiClass.getMethod("apiVersion").invoke(api);
            if (version != REQUIRED_API_VERSION) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "Market API version " + version
                                + " is incompatible with required version "
                                + REQUIRED_API_VERSION
                );
            }
            return new MarketIntegration(
                    IntegrationAvailability.AVAILABLE,
                    "",
                    api,
                    apiClass.getMethod("findStalls", UUID.class),
                    apiClass.getMethod("getStallBlacklist", UUID.class),
                    stallClass.getMethod("getId"),
                    stallClass.getMethod("getWorld"),
                    stallClass.getMethod("getState"),
                    stallClass.getMethod("getOwnership"),
                    ownershipClass.getMethod("getType"),
                    ownershipClass.getMethod("getId"),
                    blacklistClass.getMethod("getStatus"),
                    blacklistClass.getMethod("getExpiresAt"),
                    blacklistClass.getMethod("getCaseId")
            );
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException | LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "Market API could not be linked: " + exception.getClass().getSimpleName()
            );
        }
    }

    public CompletionStage<PlayerMarketStatus> status(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (availability != IntegrationAvailability.AVAILABLE) {
            return java.util.concurrent.CompletableFuture.failedStage(
                    new IllegalStateException(issue)
            );
        }
        try {
            CompletionStage<?> stalls = (CompletionStage<?>) findStalls.invoke(api, playerId);
            CompletionStage<?> blacklist = (CompletionStage<?>) getBlacklist.invoke(api, playerId);
            return stalls.thenCombine(blacklist, this::toStatus);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return java.util.concurrent.CompletableFuture.failedStage(
                    new IllegalStateException("Market status query could not start", exception)
            );
        }
    }

    public IntegrationAvailability availability() {
        return availability;
    }

    public String issue() {
        return issue;
    }

    public int apiVersion() {
        if (availability != IntegrationAvailability.AVAILABLE) {
            throw new IllegalStateException(issue);
        }
        return REQUIRED_API_VERSION;
    }

    private PlayerMarketStatus toStatus(Object stallsValue, Object blacklistValue) {
        try {
            List<?> rawStalls = (List<?>) stallsValue;
            List<StallView> stalls = rawStalls.stream().map(this::stallView).toList();
            Optional<BlacklistView> blacklist = Optional.ofNullable(blacklistValue)
                    .map(this::blacklistView);
            return new PlayerMarketStatus(stalls, blacklist);
        } catch (ClassCastException exception) {
            throw new IllegalStateException("Market API returned an incompatible status model", exception);
        }
    }

    private StallView stallView(Object stall) {
        try {
            Object ownership = stallOwnership.invoke(stall);
            return new StallView(
                    (String) stallId.invoke(stall),
                    (String) stallWorld.invoke(stall),
                    stallState.invoke(stall).toString(),
                    ownershipType.invoke(ownership).toString(),
                    Optional.ofNullable((String) ownershipId.invoke(ownership))
            );
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Market stall record could not be read", exception);
        }
    }

    private BlacklistView blacklistView(Object blacklist) {
        try {
            return new BlacklistView(
                    blacklistStatus.invoke(blacklist).toString(),
                    Optional.ofNullable((Instant) blacklistExpiration.invoke(blacklist)),
                    (String) blacklistCaseId.invoke(blacklist)
            );
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Market blacklist record could not be read", exception);
        }
    }

    private static MarketIntegration unavailable(
            IntegrationAvailability availability,
            String issue
    ) {
        return new MarketIntegration(
                availability,
                issue,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
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
