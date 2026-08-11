package net.enthusia.staff.paper.config.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertController;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.PaperConfigurationSnapshot;
import net.enthusia.staff.paper.config.PaperConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

public final class ConfigurationReloadCoordinator implements ConfigurationReloadAction {
    private static final String POLICY_RESTORATION_FAILED = "Reason-policy restoration failed: ";

    private final Supplier<PaperConfigurationSnapshot> configurationCandidates;
    private final Supplier<ReasonPolicyConfigurationLoader.LoadedPolicies> reasonPolicyCandidates;
    private final ReasonPolicyPublisher policyPublisher;
    private final PunishmentRequestAlertController alertController;
    private final Consumer<List<String>> rejectedCandidateLogger;
    private final Consumer<String> reloadIssueSink;

    private PaperConfigurationSnapshot active;
    private ReasonPolicyPublisher.Snapshot activePolicies;

    public ConfigurationReloadCoordinator(
            PaperConfigurationSnapshot active,
            Supplier<PaperConfigurationSnapshot> configurationCandidates,
            Supplier<ReasonPolicyConfigurationLoader.LoadedPolicies> reasonPolicyCandidates,
            AtomicReasonPolicyRepository reasonPolicies,
            PunishmentRequestAlertController alertController,
            Consumer<List<String>> rejectedCandidateLogger,
            Consumer<String> reloadIssueSink
    ) {
        this(
                active,
                configurationCandidates,
                reasonPolicyCandidates,
                new AtomicReasonPolicyPublisher(reasonPolicies),
                alertController,
                rejectedCandidateLogger,
                reloadIssueSink
        );
    }

    ConfigurationReloadCoordinator(
            PaperConfigurationSnapshot active,
            Supplier<PaperConfigurationSnapshot> configurationCandidates,
            Supplier<ReasonPolicyConfigurationLoader.LoadedPolicies> reasonPolicyCandidates,
            ReasonPolicyPublisher policyPublisher,
            PunishmentRequestAlertController alertController,
            Consumer<List<String>> rejectedCandidateLogger,
            Consumer<String> reloadIssueSink
    ) {
        this.active = Objects.requireNonNull(active, "active");
        this.configurationCandidates = Objects.requireNonNull(configurationCandidates, "configurationCandidates");
        this.reasonPolicyCandidates = Objects.requireNonNull(reasonPolicyCandidates, "reasonPolicyCandidates");
        this.policyPublisher = Objects.requireNonNull(policyPublisher, "policyPublisher");
        this.alertController = Objects.requireNonNull(alertController, "alertController");
        this.rejectedCandidateLogger = Objects.requireNonNull(rejectedCandidateLogger, "rejectedCandidateLogger");
        this.reloadIssueSink = Objects.requireNonNull(reloadIssueSink, "reloadIssueSink");
        this.activePolicies = policyPublisher.snapshot();
    }

    @Override
    public ConfigurationReloadResult reload() {
        synchronized (this) {
            return reloadLocked();
        }
    }

    private ConfigurationReloadResult reloadLocked() {
        Candidate candidate;
        try {
            candidate = new Candidate(configurationCandidates.get(), reasonPolicyCandidates.get());
        } catch (PaperConfigurationValidationException exception) {
            return validationFailed(exception.errors());
        } catch (ConfigurationValidationException exception) {
            return validationFailed(List.of(sanitized(
                    exception.getMessage(), "reason-policies.yml is invalid")));
        } catch (RuntimeException exception) {
            return validationFailed(List.of("Configuration candidate could not be loaded"));
        }
        return reloadCandidate(candidate);
    }

    private ConfigurationReloadResult reloadCandidate(Candidate candidate) {
        ConfigurationReloadResult restartRejection = rejectRestartRequired(candidate);
        if (restartRejection != null) {
            return restartRejection;
        }

        Previous previous = new Previous(
                active,
                alertController.currentSettings(),
                policyPublisher.snapshot()
        );
        activePolicies = previous.policies();
        if (!previous.configuration().punishmentRequestAlerts().equals(previous.alertSettings())) {
            return unavailable(
                    previous.configuration(),
                    previous.alertSettings(),
                    "Reload cannot start because active alert settings are inconsistent",
                    List.of("Coordinator snapshot and alert controller settings disagreed before commit")
            );
        }

        try (PunishmentRequestAlertController.PreparedChange prepared = alertController.prepare(
                candidate.configuration().punishmentRequestAlerts())) {
            if (prepared.preparationFailure() != null) {
                active = previous.configuration();
                activePolicies = previous.policies();
                return result(
                        ConfigurationReloadResult.Outcome.RESTORED,
                        "Alert candidate construction failed before runtime mutation",
                        List.of(prepared.preparationFailure().getClass().getSimpleName()),
                        false,
                        ""
                );
            }

            PolicyPublication publication = publishCandidatePolicies(candidate, previous);
            if (!publication.candidateActive()) {
                return publication.result();
            }
            if (alertController.shuttingDown()) {
                restorePolicies(previous.policies());
                return shuttingDown("Shutdown began before the alert candidate commit point");
            }

            PunishmentRequestAlertController.ApplyResult alertResult = prepared.commit();
            return finishAlertCommit(candidate, previous, alertResult);
        }
    }

    private PolicyPublication publishCandidatePolicies(Candidate candidate, Previous previous) {
        RuntimeException publicationFailure = null;
        try {
            policyPublisher.publish(candidate.policies());
        } catch (RuntimeException exception) {
            publicationFailure = exception;
        }
        ReasonPolicyPublisher.Snapshot observed = policyPublisher.snapshot();
        if (publicationFailure == null && observed.matches(candidate.policies())) {
            activePolicies = observed;
            return PolicyPublication.active();
        }

        RuntimeException restorationFailure = restorePolicies(previous.policies());
        observed = policyPublisher.snapshot();
        List<String> details = new ArrayList<>();
        details.add("Atomic reason-policy publication failed before alert mutation");
        if (publicationFailure != null) {
            details.add(publicationFailure.getClass().getSimpleName());
        }
        if (restorationFailure != null) {
            details.add(POLICY_RESTORATION_FAILED
                    + restorationFailure.getClass().getSimpleName());
        }

        if (observed.sameAs(previous.policies())) {
            active = previous.configuration();
            activePolicies = observed;
            return PolicyPublication.failed(result(
                    ConfigurationReloadResult.Outcome.RESTORED,
                    "Reload failed before alert mutation; previous policies remain active",
                    details,
                    false,
                    "Configuration publication failed; previous runtime remains active"
            ));
        }
        if (observed.matches(candidate.policies())) {
            // The publication threw after atomically committing. The prepared alert lifecycle can
            // still be committed so the three runtime components converge on the candidate.
            activePolicies = observed;
            return PolicyPublication.active();
        }
        return PolicyPublication.failed(unavailable(
                previous.configuration(),
                previous.alertSettings(),
                "Reason-policy publication and restoration left an unrecognized active policy state",
                appendPolicyState(details, observed)
        ));
    }

    private ConfigurationReloadResult finishAlertCommit(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
        if (alertController.shuttingDown()
                || alertResult.outcome() == PunishmentRequestAlertController.Outcome.SHUTTING_DOWN) {
            restorePolicies(previous.policies());
            return shuttingDown(alertResult.message());
        }
        return switch (alertResult.outcome()) {
            case NO_CHANGES, ENABLED, DISABLED, REPLACED, WAITING_FOR_STORAGE ->
                    commitCandidateOrRollback(candidate, previous, alertResult);
            case RESTORED -> restoreAfterCandidateFailure(candidate, previous, alertResult);
            case UNAVAILABLE -> recoverAfterUnavailableCandidate(candidate, previous, alertResult);
            case SHUTTING_DOWN -> shuttingDown(alertResult.message());
        };
    }

    private ConfigurationReloadResult commitCandidateOrRollback(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
        ReasonPolicyPublisher.Snapshot policies = policyPublisher.snapshot();
        if (candidateIsFullyActive(candidate, policies)) {
            return commitVerifiedCandidate(candidate, previous, alertResult, policies);
        }
        return rollbackFromCandidate(
                candidate,
                previous,
                "Final commit verification detected divergent alert or policy state",
                List.of("Candidate verification failed after alert commit")
        );
    }

    private boolean candidateIsFullyActive(
            Candidate candidate,
            ReasonPolicyPublisher.Snapshot policies
    ) {
        return alertController.currentSettings().equals(
                candidate.configuration().punishmentRequestAlerts()
        ) && policies.matches(candidate.policies()) && !alertController.shuttingDown();
    }

    private ConfigurationReloadResult commitVerifiedCandidate(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult alertResult,
            ReasonPolicyPublisher.Snapshot policies
    ) {
        active = candidate.configuration();
        activePolicies = policies;
        boolean policiesChanged = !previous.policies().matches(candidate.policies());
        return result(
                committedOutcome(alertResult.outcome(), policiesChanged),
                committedMessage(alertResult, policiesChanged),
                List.of(),
                policiesChanged,
                ""
        );
    }

    private static ConfigurationReloadResult.Outcome committedOutcome(
            PunishmentRequestAlertController.Outcome alertOutcome,
            boolean policiesChanged
    ) {
        return switch (alertOutcome) {
            case NO_CHANGES -> policiesChanged
                    ? ConfigurationReloadResult.Outcome.APPLIED
                    : ConfigurationReloadResult.Outcome.NO_CHANGES;
            case WAITING_FOR_STORAGE -> ConfigurationReloadResult.Outcome.WAITING_FOR_STORAGE;
            default -> ConfigurationReloadResult.Outcome.APPLIED;
        };
    }

    private static String committedMessage(
            PunishmentRequestAlertController.ApplyResult alertResult,
            boolean policiesChanged
    ) {
        return policiesChanged
                && alertResult.outcome() == PunishmentRequestAlertController.Outcome.NO_CHANGES
                ? "Reason policies reloaded atomically; alert-worker settings were unchanged"
                : alertResult.message();
    }

    private ConfigurationReloadResult restoreAfterCandidateFailure(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
        RuntimeException policyRestoreFailure = restorePolicies(previous.policies());
        ReasonPolicyPublisher.Snapshot policies = policyPublisher.snapshot();
        List<String> details = new ArrayList<>(failureDetails(alertResult));
        if (policyRestoreFailure != null) {
            details.add(POLICY_RESTORATION_FAILED
                    + policyRestoreFailure.getClass().getSimpleName());
        }
        if (alertController.currentSettings().equals(previous.alertSettings())
                && policies.sameAs(previous.policies())) {
            active = previous.configuration();
            activePolicies = policies;
            return result(
                    ConfigurationReloadResult.Outcome.RESTORED,
                    "Candidate alert replacement failed; both previous components were restored",
                    details,
                    false,
                    ""
            );
        }
        if (policies.matches(candidate.policies())) {
            return reconcileAlertToCandidate(candidate, previous, details);
        }
        return unavailableForObservedState(
                candidate,
                previous,
                policies,
                "Alert replacement failed and reason-policy restoration did not converge",
                details
        );
    }

    private ConfigurationReloadResult recoverAfterUnavailableCandidate(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
        RuntimeException policyRestoreFailure = restorePolicies(previous.policies());
        ReasonPolicyPublisher.Snapshot policies = policyPublisher.snapshot();
        PunishmentRequestAlertController.ApplyResult rollback =
                alertController.apply(previous.alertSettings());
        List<String> details = new ArrayList<>(failureDetails(alertResult));
        if (policyRestoreFailure != null) {
            details.add(POLICY_RESTORATION_FAILED
                    + policyRestoreFailure.getClass().getSimpleName());
        }
        return settleRollback(candidate, previous, rollback, policies,
                "Candidate alert lifecycle became unavailable", details);
    }

    private ConfigurationReloadResult rollbackFromCandidate(
            Candidate candidate,
            Previous previous,
            String message,
            List<String> details
    ) {
        RuntimeException policyRestoreFailure = restorePolicies(previous.policies());
        ReasonPolicyPublisher.Snapshot policies = policyPublisher.snapshot();
        PunishmentRequestAlertController.ApplyResult rollback =
                alertController.apply(previous.alertSettings());
        List<String> reported = new ArrayList<>(details);
        if (policyRestoreFailure != null) {
            reported.add(POLICY_RESTORATION_FAILED
                    + policyRestoreFailure.getClass().getSimpleName());
        }
        return settleRollback(candidate, previous, rollback, policies, message, reported);
    }

    private ConfigurationReloadResult settleRollback(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult rollback,
            ReasonPolicyPublisher.Snapshot policies,
            String context,
            List<String> details
    ) {
        if (rollback.outcome() == PunishmentRequestAlertController.Outcome.SHUTTING_DOWN
                || alertController.shuttingDown()) {
            activePolicies = policyPublisher.snapshot();
            return shuttingDown(rollback.message());
        }
        if (previousStateWasRestored(rollback, previous, policies)) {
            return restoredPreviousResult(
                    previous,
                    policies,
                    context + "; previous alert settings and policies were restored",
                    details
            );
        }
        if (rollback.outcome() == PunishmentRequestAlertController.Outcome.RESTORED) {
            Optional<ConfigurationReloadResult> restored = settleRestoredRollback(
                    candidate,
                    previous,
                    policies,
                    context,
                    details
            );
            if (restored.isPresent()) {
                return restored.orElseThrow();
            }
        }
        return unavailableAfterRollback(
                candidate,
                previous,
                rollback,
                policies,
                context,
                details
        );
    }

    private ConfigurationReloadResult unavailableAfterRollback(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult rollback,
            ReasonPolicyPublisher.Snapshot policies,
            String context,
            List<String> details
    ) {
        boolean replacementFailed = rollback.outcome()
                == PunishmentRequestAlertController.Outcome.UNAVAILABLE
                || !alertController.active();
        String issue = replacementFailed
                ? context + "; rollback replacement and restoration both failed"
                : context + "; rollback result did not match observed runtime state";
        return unavailableForObservedState(
                candidate,
                previous,
                policies,
                issue,
                appendFailure(details, rollback)
        );
    }

    private boolean previousStateWasRestored(
            PunishmentRequestAlertController.ApplyResult rollback,
            Previous previous,
            ReasonPolicyPublisher.Snapshot policies
    ) {
        return accepted(rollback.outcome())
                && previousRuntimeIsActive(previous, policies);
    }

    private Optional<ConfigurationReloadResult> settleRestoredRollback(
            Candidate candidate,
            Previous previous,
            ReasonPolicyPublisher.Snapshot policies,
            String context,
            List<String> details
    ) {
        // RESTORED means the lifecycle active immediately before rollback survived. During
        // rollback that lifecycle is normally the candidate lifecycle.
        if (candidateLifecycleIsActive(candidate)) {
            return Optional.of(preserveSurvivingCandidate(
                    candidate,
                    previous,
                    policies,
                    details
            ));
        }
        if (previousRuntimeIsActive(previous, policies)) {
            return Optional.of(restoredPreviousResult(
                    previous,
                    policies,
                    context + "; previous lifecycle survived rollback",
                    details
            ));
        }
        return Optional.empty();
    }

    private boolean candidateLifecycleIsActive(Candidate candidate) {
        return alertController.currentSettings().equals(
                candidate.configuration().punishmentRequestAlerts()
        ) && alertController.active();
    }

    private boolean previousRuntimeIsActive(
            Previous previous,
            ReasonPolicyPublisher.Snapshot policies
    ) {
        return alertController.currentSettings().equals(previous.alertSettings())
                && policies.sameAs(previous.policies());
    }

    private ConfigurationReloadResult restoredPreviousResult(
            Previous previous,
            ReasonPolicyPublisher.Snapshot policies,
            String message,
            List<String> details
    ) {
        active = previous.configuration();
        activePolicies = policies;
        return result(
                ConfigurationReloadResult.Outcome.RESTORED,
                message,
                details,
                false,
                ""
        );
    }

    private ConfigurationReloadResult preserveSurvivingCandidate(
            Candidate candidate,
            Previous previous,
            ReasonPolicyPublisher.Snapshot policies,
            List<String> details
    ) {
        ReasonPolicyPublisher.Snapshot aligned = policies;
        if (!aligned.matches(candidate.policies())) {
            try {
                policyPublisher.publish(candidate.policies());
            } catch (RuntimeException exception) {
                List<String> reported = new ArrayList<>(details);
                reported.add("Candidate policy reconciliation failed: "
                        + exception.getClass().getSimpleName());
                return unavailable(
                        candidate.configuration(),
                        candidate.configuration().punishmentRequestAlerts(),
                        "Candidate lifecycle survived rollback but its policies could not be restored",
                        reported
                );
            }
            aligned = policyPublisher.snapshot();
        }
        if (aligned.matches(candidate.policies()) && alertController.active()) {
            active = candidate.configuration();
            activePolicies = aligned;
            return result(
                    ConfigurationReloadResult.Outcome.APPLIED,
                    "Rollback replacement failed, so the surviving candidate lifecycle and policies were retained",
                    details,
                    !previous.policies().matches(candidate.policies()),
                    ""
            );
        }
        return unavailableForObservedState(
                candidate,
                previous,
                aligned,
                "Candidate lifecycle survived rollback but final reconciliation was inconclusive",
                details
        );
    }

    private ConfigurationReloadResult reconcileAlertToCandidate(
            Candidate candidate,
            Previous previous,
            List<String> details
    ) {
        PunishmentRequestAlertController.ApplyResult reconciliation = alertController.apply(
                candidate.configuration().punishmentRequestAlerts());
        if (candidateReconciliationSucceeded(candidate, reconciliation)) {
            active = candidate.configuration();
            activePolicies = policyPublisher.snapshot();
            return result(
                    ConfigurationReloadResult.Outcome.APPLIED,
                    "Policy restoration failed; alert settings were reconciled to the active candidate policies",
                    details,
                    true,
                    ""
            );
        }
        if (previousLifecycleSurvivedReconciliation(previous, reconciliation)) {
            return restorePreviousPoliciesAfterReconciliation(
                    candidate,
                    previous,
                    reconciliation,
                    details
            );
        }
        return unavailableForObservedState(
                candidate,
                previous,
                policyPublisher.snapshot(),
                "Alert replacement failed and policy restoration also failed",
                appendFailure(details, reconciliation)
        );
    }

    private boolean candidateReconciliationSucceeded(
            Candidate candidate,
            PunishmentRequestAlertController.ApplyResult reconciliation
    ) {
        return accepted(reconciliation.outcome())
                && alertController.currentSettings().equals(
                        candidate.configuration().punishmentRequestAlerts()
                ) && policyPublisher.snapshot().matches(candidate.policies());
    }

    private boolean previousLifecycleSurvivedReconciliation(
            Previous previous,
            PunishmentRequestAlertController.ApplyResult reconciliation
    ) {
        return reconciliation.outcome() == PunishmentRequestAlertController.Outcome.RESTORED
                && alertController.currentSettings().equals(previous.alertSettings())
                && alertController.active();
    }

    private ConfigurationReloadResult restorePreviousPoliciesAfterReconciliation(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertController.ApplyResult reconciliation,
            List<String> details
    ) {
        RuntimeException restorationFailure = restorePolicies(previous.policies());
        ReasonPolicyPublisher.Snapshot restoredPolicies = policyPublisher.snapshot();
        List<String> reported = new ArrayList<>(appendFailure(details, reconciliation));
        if (restorationFailure != null) {
            reported.add("Reason-policy restoration retry failed: "
                    + restorationFailure.getClass().getSimpleName());
        }
        if (restoredPolicies.sameAs(previous.policies())) {
            active = previous.configuration();
            activePolicies = restoredPolicies;
            return result(
                    ConfigurationReloadResult.Outcome.RESTORED,
                    "Candidate alert retry failed; the previous lifecycle and policies remain active",
                    reported,
                    false,
                    ""
            );
        }
        return unavailableForObservedState(
                candidate,
                previous,
                restoredPolicies,
                "Candidate alert retry restored the previous lifecycle but previous policies could not be restored",
                reported
        );
    }

    private ConfigurationReloadResult unavailableForObservedState(
            Candidate candidate,
            Previous previous,
            ReasonPolicyPublisher.Snapshot policies,
            String issue,
            List<String> details
    ) {
        PunishmentRequestAlertWorkerSettings settings = alertController.currentSettings();
        PaperConfigurationSnapshot snapshot = snapshotForSettings(candidate, previous, settings);
        return unavailable(snapshot, settings, issue, appendPolicyState(details, policies));
    }

    private ConfigurationReloadResult unavailable(
            PaperConfigurationSnapshot snapshot,
            PunishmentRequestAlertWorkerSettings settings,
            String issue,
            List<String> details
    ) {
        PunishmentRequestAlertController.ApplyResult unavailable =
                alertController.forceUnavailable(settings, issue);
        if (unavailable.outcome() == PunishmentRequestAlertController.Outcome.SHUTTING_DOWN) {
            return shuttingDown(unavailable.message());
        }
        active = snapshot;
        activePolicies = policyPublisher.snapshot();
        return result(
                ConfigurationReloadResult.Outcome.UNAVAILABLE,
                issue,
                appendPolicyState(details, activePolicies),
                false,
                issue
        );
    }

    private RuntimeException restorePolicies(ReasonPolicyPublisher.Snapshot previous) {
        try {
            policyPublisher.restore(previous);
            return null;
        } catch (RuntimeException exception) {
            return exception;
        } finally {
            activePolicies = policyPublisher.snapshot();
        }
    }

    private ConfigurationReloadResult rejectRestartRequired(Candidate candidate) {
        List<String> restartRequired = active.restartRequired()
                .differencePaths(candidate.configuration().restartRequired());
        if (restartRequired.isEmpty()) {
            return null;
        }
        List<String> details = restartRequired.stream()
                .map(path -> path + " requires a full server restart")
                .toList();
        rejectedCandidateLogger.accept(details);
        reloadIssueSink.accept("Restart-required configuration change rejected; previous runtime remains active");
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.RESTART_REQUIRED,
                "Reload rejected because startup-only settings changed",
                details,
                false
        );
    }

    private ConfigurationReloadResult validationFailed(List<String> errors) {
        List<String> sanitized = errors.stream()
                .map(error -> sanitized(error, "Configuration validation failed"))
                .toList();
        rejectedCandidateLogger.accept(sanitized);
        reloadIssueSink.accept("Configuration validation failed while the previous runtime remains active");
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.VALIDATION_FAILED,
                "Reload rejected; the previous configuration remains active",
                sanitized,
                false
        );
    }

    private ConfigurationReloadResult shuttingDown(String message) {
        reloadIssueSink.accept("Configuration reload stopped because shutdown has started");
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.SHUTTING_DOWN,
                message == null || message.isBlank()
                        ? "Configuration reload stopped because shutdown has started"
                        : message,
                List.of(),
                false
        );
    }

    private ConfigurationReloadResult result(
            ConfigurationReloadResult.Outcome outcome,
            String message,
            List<String> details,
            boolean policiesReloaded,
            String issue
    ) {
        if (!details.isEmpty()) {
            rejectedCandidateLogger.accept(List.copyOf(details));
        }
        reloadIssueSink.accept(issue);
        return new ConfigurationReloadResult(outcome, message, details, policiesReloaded);
    }

    public PaperConfigurationSnapshot activeSnapshot() {
        synchronized (this) {
            return active;
        }
    }

    public ReasonPolicyPublisher.Snapshot activeReasonPolicies() {
        synchronized (this) {
            return activePolicies;
        }
    }

    private static boolean accepted(PunishmentRequestAlertController.Outcome outcome) {
        return switch (outcome) {
            case NO_CHANGES, ENABLED, DISABLED, REPLACED, WAITING_FOR_STORAGE -> true;
            case RESTORED, UNAVAILABLE, SHUTTING_DOWN -> false;
        };
    }

    private static PaperConfigurationSnapshot snapshotForSettings(
            Candidate candidate,
            Previous previous,
            PunishmentRequestAlertWorkerSettings settings
    ) {
        if (settings.equals(candidate.configuration().punishmentRequestAlerts())) {
            return candidate.configuration();
        }
        if (settings.equals(previous.alertSettings())) {
            return previous.configuration();
        }
        return new PaperConfigurationSnapshot(
                previous.configuration().version(),
                previous.configuration().restartRequired(),
                settings,
                previous.configuration().moderationFeatures()
        );
    }

    private static List<String> appendFailure(
            List<String> details,
            PunishmentRequestAlertController.ApplyResult result
    ) {
        List<String> reported = new ArrayList<>(details);
        reported.addAll(failureDetails(result));
        return List.copyOf(reported);
    }

    private static List<String> appendPolicyState(
            List<String> details,
            ReasonPolicyPublisher.Snapshot policies
    ) {
        List<String> reported = new ArrayList<>(details);
        String state = "Active reason-policy version: " + policies.version();
        if (!reported.contains(state)) {
            reported.add(state);
        }
        return List.copyOf(reported);
    }

    private static List<String> failureDetails(PunishmentRequestAlertController.ApplyResult result) {
        List<String> details = new ArrayList<>();
        if (!result.status().issue().isBlank()) {
            details.add(result.status().issue());
        }
        if (result.failure() != null) {
            details.add(result.failure().getClass().getSimpleName());
        }
        return List.copyOf(details);
    }

    private static String sanitized(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        String firstLine = message.lines().findFirst().orElse(fallback).trim();
        return firstLine.length() > 240 ? firstLine.substring(0, 240) : firstLine;
    }

    private record Candidate(
            PaperConfigurationSnapshot configuration,
            ReasonPolicyConfigurationLoader.LoadedPolicies policies
    ) {
        private Candidate {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(policies, "policies");
        }
    }

    private record Previous(
            PaperConfigurationSnapshot configuration,
            PunishmentRequestAlertWorkerSettings alertSettings,
            ReasonPolicyPublisher.Snapshot policies
    ) {
    }

    private record PolicyPublication(
            boolean candidateActive,
            ConfigurationReloadResult result
    ) {
        private static PolicyPublication active() {
            return new PolicyPublication(true, null);
        }

        private static PolicyPublication failed(ConfigurationReloadResult result) {
            return new PolicyPublication(false, result);
        }
    }
}
