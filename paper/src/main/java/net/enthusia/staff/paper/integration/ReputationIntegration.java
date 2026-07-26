package net.enthusia.staff.paper.integration;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.ServicesManager;
import org.enthusia.rep.api.ReputationBlacklist;
import org.enthusia.rep.api.ReputationModerationApi;

public final class ReputationIntegration {
    private final IntegrationAvailability availability;
    private final String issue;
    private final AuthorizationPolicy authorization;
    private final ReputationModerationApi api;

    private ReputationIntegration(
            IntegrationAvailability availability,
            String issue,
            AuthorizationPolicy authorization,
            ReputationModerationApi api
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.api = api;
    }

    public static ReputationIntegration discover(
            ServicesManager services,
            boolean pluginEnabled,
            AuthorizationPolicy authorization
    ) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(authorization, "authorization");
        if (!pluginEnabled) {
            return unavailable(
                    IntegrationAvailability.NOT_INSTALLED,
                    "EnthusiaCommend is not installed or enabled",
                    authorization
            );
        }
        try {
            ReputationModerationApi api = services.load(ReputationModerationApi.class);
            if (api == null) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "EnthusiaCommend did not register its moderation service",
                        authorization
                );
            }
            if (api.apiVersion() != ReputationModerationApi.API_VERSION) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "Reputation API version " + api.apiVersion()
                                + " is incompatible with required version "
                                + ReputationModerationApi.API_VERSION,
                        authorization
                );
            }
            return new ReputationIntegration(
                    IntegrationAvailability.AVAILABLE,
                    "",
                    authorization,
                    api
            );
        } catch (LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "Reputation API could not be linked: " + exception.getClass().getSimpleName(),
                    authorization
            );
        }
    }

    public Optional<ReputationBlacklist> blacklist(UUID playerId) {
        requireAvailable();
        return api.getBlacklist(Objects.requireNonNull(playerId, "playerId"));
    }

    public ReputationBlacklist apply(
            Actor actor,
            UUID playerId,
            Optional<Instant> expirationAt,
            String caseId
    ) {
        requireAvailable();
        requireMutationAuthority(actor);
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(expirationAt, "expirationAt");
        Objects.requireNonNull(caseId, "caseId");
        return expirationAt
                .map(expiration -> api.blacklist(playerId, expiration, caseId))
                .orElseGet(() -> api.blacklistPermanently(playerId, caseId));
    }

    public boolean remove(Actor actor, UUID playerId, String caseId) {
        requireAvailable();
        requireMutationAuthority(actor);
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

    private void requireMutationAuthority(Actor actor) {
        if (!authorization.permits(actor, ModerationAction.MODIFY_REPUTATION_RESTRICTION)) {
            throw new SecurityException("Actor cannot modify reputation restrictions");
        }
    }

    private static ReputationIntegration unavailable(
            IntegrationAvailability availability,
            String issue,
            AuthorizationPolicy authorization
    ) {
        return new ReputationIntegration(availability, issue, authorization, null);
    }
}
