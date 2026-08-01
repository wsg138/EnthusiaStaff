package net.enthusia.staff.paper.config.reload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertController;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.PaperConfigurationSnapshot;
import net.enthusia.staff.paper.config.PaperConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

public final class ConfigurationReloadCoordinator implements ConfigurationReloadAction {
    private final Supplier<PaperConfigurationSnapshot> configurationCandidates;
    private final Supplier<ReasonPolicyConfigurationLoader.LoadedPolicies> reasonPolicyCandidates;
    private final AtomicReasonPolicyRepository reasonPolicies;
    private final PunishmentRequestAlertController alertController;
    private final Consumer<List<String>> rejectedCandidateLogger;
    private final Consumer<String> reloadIssueSink;

    private PaperConfigurationSnapshot active;

    public ConfigurationReloadCoordinator(
            PaperConfigurationSnapshot active,
            Supplier<PaperConfigurationSnapshot> configurationCandidates,
            Supplier<ReasonPolicyConfigurationLoader.LoadedPolicies> reasonPolicyCandidates,
            AtomicReasonPolicyRepository reasonPolicies,
            PunishmentRequestAlertController alertController,
            Consumer<List<String>> rejectedCandidateLogger,
            Consumer<String> reloadIssueSink
    ) {
        this.active = Objects.requireNonNull(active, "active");
        this.configurationCandidates = Objects.requireNonNull(configurationCandidates, "configurationCandidates");
        this.reasonPolicyCandidates = Objects.requireNonNull(reasonPolicyCandidates, "reasonPolicyCandidates");
        this.reasonPolicies = Objects.requireNonNull(reasonPolicies, "reasonPolicies");
        this.alertController = Objects.requireNonNull(alertController, "alertController");
        this.rejectedCandidateLogger = Objects.requireNonNull(rejectedCandidateLogger, "rejectedCandidateLogger");
        this.reloadIssueSink = Objects.requireNonNull(reloadIssueSink, "reloadIssueSink");
    }

    @Override
    public synchronized ConfigurationReloadResult reload() {
        Candidate candidate;
        try {
            candidate = new Candidate(configurationCandidates.get(), reasonPolicyCandidates.get());
        } catch (PaperConfigurationValidationException exception) {
            return validationFailed(exception.errors());
        } catch (ConfigurationValidationException exception) {
            return validationFailed(List.of(sanitized(exception.getMessage(), "reason-policies.yml is invalid")));
        } catch (RuntimeException exception) {
            return validationFailed(List.of("Configuration candidate could not be loaded"));
        }

        List<String> restartRequired = active.restartRequired()
                .differencePaths(candidate.configuration().restartRequired());
        if (!restartRequired.isEmpty()) {
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

        PaperConfigurationSnapshot previousConfiguration = active;
        ReasonPolicyState previousPolicies = currentPolicies();
        boolean policiesChanged = !previousPolicies.matches(candidate.policies());
        PunishmentRequestAlertController.ApplyResult alertResult = alertController.apply(
                candidate.configuration().punishmentRequestAlerts()
        );
        return finish(candidate, previousConfiguration, previousPolicies, policiesChanged, alertResult);
    }

    public synchronized PaperConfigurationSnapshot activeSnapshot() {
        return active;
    }

    private ConfigurationReloadResult finish(
            Candidate candidate,
            PaperConfigurationSnapshot previousConfiguration,
            ReasonPolicyState previousPolicies,
            boolean policiesChanged,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
        return switch (alertResult.outcome()) {
            case RESTORED -> {
                reloadIssueSink.accept("");
                yield new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.RESTORED,
                        alertResult.message(),
                        failureDetails(alertResult),
                        false
                );
            }
            case UNAVAILABLE -> {
                active = candidate.configuration();
                reloadIssueSink.accept("");
                yield new ConfigurationReloadResult(
                        ConfigurationReloadResult.Outcome.UNAVAILABLE,
                        alertResult.message(),
                        failureDetails(alertResult),
                        false
                );
            }
            case SHUTTING_DOWN -> new ConfigurationReloadResult(
                    ConfigurationReloadResult.Outcome.SHUTTING_DOWN,
                    alertResult.message(),
                    List.of(),
                    false
            );
            case NO_CHANGES, ENABLED, DISABLED, REPLACED, WAITING_FOR_STORAGE ->
                    publishAccepted(candidate, previousConfiguration, previousPolicies, policiesChanged, alertResult);
        };
    }

    private ConfigurationReloadResult publishAccepted(
            Candidate candidate,
            PaperConfigurationSnapshot previousConfiguration,
            ReasonPolicyState previousPolicies,
            boolean policiesChanged,
            PunishmentRequestAlertController.ApplyResult alertResult
    ) {
        try {
            if (policiesChanged) {
                reasonPolicies.replace(candidate.policies().version(), candidate.policies().policies());
            }
        } catch (RuntimeException publicationFailure) {
            PunishmentRequestAlertController.ApplyResult rollback = alertController.apply(
                    previousConfiguration.punishmentRequestAlerts()
            );
            active = rollback.applied() ? previousConfiguration : candidate.configuration();
            List<String> details = List.of("Atomic reason-policy publication failed");
            rejectedCandidateLogger.accept(details);
            reloadIssueSink.accept("Configuration publication failed; see the sanitized console error");
            return new ConfigurationReloadResult(
                    rollback.applied()
                            ? ConfigurationReloadResult.Outcome.APPLY_FAILED
                            : ConfigurationReloadResult.Outcome.UNAVAILABLE,
                    rollback.applied()
                            ? "Reload failed; previous alert settings were restored"
                            : "Reload failed and alerts are unavailable",
                    details,
                    false
            );
        }

        active = candidate.configuration();
        reloadIssueSink.accept("");
        ConfigurationReloadResult.Outcome outcome = switch (alertResult.outcome()) {
            case NO_CHANGES -> policiesChanged
                    ? ConfigurationReloadResult.Outcome.APPLIED
                    : ConfigurationReloadResult.Outcome.NO_CHANGES;
            case WAITING_FOR_STORAGE -> ConfigurationReloadResult.Outcome.WAITING_FOR_STORAGE;
            default -> ConfigurationReloadResult.Outcome.APPLIED;
        };
        String message = policiesChanged
                && alertResult.outcome() == PunishmentRequestAlertController.Outcome.NO_CHANGES
                ? "Reason policies reloaded atomically; alert-worker settings were unchanged"
                : alertResult.message();
        return new ConfigurationReloadResult(outcome, message, List.of(), policiesChanged);
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

    private ReasonPolicyState currentPolicies() {
        return new ReasonPolicyState(reasonPolicies.activeVersion(), index(reasonPolicies.all()));
    }

    private static Map<String, ReasonPolicy> index(Collection<ReasonPolicy> policies) {
        Map<String, ReasonPolicy> indexed = new LinkedHashMap<>();
        for (ReasonPolicy policy : policies) {
            indexed.put(policy.id(), policy);
        }
        return Map.copyOf(indexed);
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

    private record ReasonPolicyState(String version, Map<String, ReasonPolicy> policies) {
        private ReasonPolicyState {
            Objects.requireNonNull(version, "version");
            policies = Map.copyOf(policies);
        }

        private boolean matches(ReasonPolicyConfigurationLoader.LoadedPolicies candidate) {
            return version.equals(candidate.version()) && policies.equals(index(candidate.policies()));
        }
    }
}
