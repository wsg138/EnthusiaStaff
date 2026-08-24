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
import org.enthusia.rep.api.ReputationMutationResult;
import org.enthusia.rep.api.ReputationStateSnapshot;

public final class ReputationIntegration {
    private static final String PLAYER_ID_ARGUMENT = "playerId";

    private final IntegrationAvailability availability;
    private final String issue;
    private final AuthorizationPolicy authorization;
    private final ReputationModerationApi api;

    ReputationIntegration(
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
        return api.getBlacklist(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
    }

    public ReputationStateSnapshot snapshot(UUID playerId) {
        requireAvailable();
        return api.snapshot(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
    }

    public ReputationBlacklist apply(
            Actor actor,
            UUID playerId,
            Optional<Instant> expirationAt,
            String caseId
    ) {
        requireAvailable();
        requireMutationAuthority(actor);
        UUID target = Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        Optional<Instant> expiration = Objects.requireNonNull(expirationAt, "expirationAt");
        String linkedCase = Objects.requireNonNull(caseId, "caseId");
        Optional<ReputationBlacklist> current = api.getBlacklist(target);
        ReputationStateSnapshot before = api.snapshot(target);
        ReputationMutationResult result = api.applyBlacklist(
                UUID.randomUUID(),
                target,
                expiration,
                linkedCase,
                current.map(ReputationBlacklist::revision).orElse(0L),
                before.checksum()
        );
        return successfulBlacklist(result, "apply");
    }

    public boolean remove(Actor actor, UUID playerId, String caseId) {
        requireAvailable();
        requireMutationAuthority(actor);
        UUID target = Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        String linkedCase = Objects.requireNonNull(caseId, "caseId");
        Optional<ReputationBlacklist> current = api.getBlacklist(target);
        if (current.isEmpty() || current.orElseThrow().status() == ReputationBlacklist.Status.REMOVED) {
            return false;
        }
        ReputationStateSnapshot before = api.snapshot(target);
        ReputationMutationResult result = api.removeBlacklist(
                UUID.randomUUID(),
                target,
                linkedCase,
                current.orElseThrow().revision(),
                before.checksum()
        );
        if (!result.success()) {
            throw new IllegalStateException("Reputation blacklist removal failed: " + result.detail());
        }
        return true;
    }

    void markReconciliationPending(UUID playerId) {
        requireAvailable();
        api.markReconciliationPending(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
    }

    void clearReconciliationPending(UUID playerId) {
        requireAvailable();
        api.clearReconciliationPending(Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT));
    }

    ReputationMutationResult reconcileApply(
            UUID operationId,
            UUID playerId,
            Optional<Instant> expirationAt,
            String caseId,
            long expectedBlacklistRevision
    ) {
        requireAvailable();
        UUID target = Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        ReputationStateSnapshot before = api.snapshot(target);
        return api.applyBlacklist(
                Objects.requireNonNull(operationId, "operationId"),
                target,
                Objects.requireNonNull(expirationAt, "expirationAt"),
                Objects.requireNonNull(caseId, "caseId"),
                expectedBlacklistRevision,
                before.checksum()
        );
    }

    ReputationMutationResult reconcileRemove(
            UUID operationId,
            UUID playerId,
            String caseId,
            long expectedBlacklistRevision
    ) {
        requireAvailable();
        UUID target = Objects.requireNonNull(playerId, PLAYER_ID_ARGUMENT);
        ReputationStateSnapshot before = api.snapshot(target);
        return api.removeBlacklist(
                Objects.requireNonNull(operationId, "operationId"),
                target,
                Objects.requireNonNull(caseId, "caseId"),
                expectedBlacklistRevision,
                before.checksum()
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

    private ReputationBlacklist successfulBlacklist(ReputationMutationResult result, String operation) {
        if (!result.success() || result.blacklist().isEmpty()) {
            throw new IllegalStateException("Reputation blacklist " + operation + " failed: " + result.detail());
        }
        return result.blacklist().orElseThrow();
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
