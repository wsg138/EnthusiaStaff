package net.enthusia.staff.paper.integration;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.ServicesManager;
import org.enthusia.rep.api.ReputationBlacklist;
import org.enthusia.rep.api.ReputationModerationApi;

public final class ReputationIntegration {
    private final IntegrationAvailability availability;
    private final String issue;
    private final ReputationModerationApi api;

    private ReputationIntegration(
            IntegrationAvailability availability,
            String issue,
            ReputationModerationApi api
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.api = api;
    }

    public static ReputationIntegration discover(
            ServicesManager services,
            boolean pluginEnabled
    ) {
        Objects.requireNonNull(services, "services");
        if (!pluginEnabled) {
            return unavailable(
                    IntegrationAvailability.NOT_INSTALLED,
                    "EnthusiaCommend is not installed or enabled"
            );
        }
        try {
            ReputationModerationApi api = services.load(ReputationModerationApi.class);
            if (api == null) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "EnthusiaCommend did not register its moderation service"
                );
            }
            if (api.apiVersion() != ReputationModerationApi.API_VERSION) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "Reputation API version " + api.apiVersion()
                                + " is incompatible with required version "
                                + ReputationModerationApi.API_VERSION
                );
            }
            return new ReputationIntegration(IntegrationAvailability.AVAILABLE, "", api);
        } catch (LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "Reputation API could not be linked: " + exception.getClass().getSimpleName()
            );
        }
    }

    public Optional<ReputationBlacklist> blacklist(UUID playerId) {
        requireAvailable();
        return api.getBlacklist(Objects.requireNonNull(playerId, "playerId"));
    }

    public ReputationBlacklist apply(
            UUID playerId,
            Optional<Instant> expirationAt,
            String caseId
    ) {
        requireAvailable();
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(expirationAt, "expirationAt");
        Objects.requireNonNull(caseId, "caseId");
        return expirationAt
                .map(expiration -> api.blacklist(playerId, expiration, caseId))
                .orElseGet(() -> api.blacklistPermanently(playerId, caseId));
    }

    public boolean remove(UUID playerId, String caseId) {
        requireAvailable();
        return api.removeBlacklist(
                Objects.requireNonNull(playerId, "playerId"),
                Objects.requireNonNull(caseId, "caseId")
        );
    }

    public IntegrationAvailability availability() {
        return availability;
    }

    public String issue() {
        return issue;
    }

    public int apiVersion() {
        requireAvailable();
        return api.apiVersion();
    }

    private void requireAvailable() {
        if (availability != IntegrationAvailability.AVAILABLE || api == null) {
            throw new IllegalStateException(issue);
        }
    }

    private static ReputationIntegration unavailable(
            IntegrationAvailability availability,
            String issue
    ) {
        return new ReputationIntegration(availability, issue, null);
    }
}
