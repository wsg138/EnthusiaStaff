package net.enthusia.staff.paper.config.reload;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.enthusia.staff.paper.config.AtomicReportConfiguration;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.ReportConfigurationSnapshot;

public final class ReportConfigurationReloadAction implements ConfigurationReloadAction {
    private final ConfigurationReloadAction delegate;
    private final Supplier<ReportConfigurationSnapshot> candidates;
    private final AtomicReportConfiguration active;
    private final Consumer<List<String>> rejectedCandidateLogger;

    public ReportConfigurationReloadAction(
            ConfigurationReloadAction delegate,
            Supplier<ReportConfigurationSnapshot> candidates,
            AtomicReportConfiguration active,
            Consumer<List<String>> rejectedCandidateLogger
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.active = Objects.requireNonNull(active, "active");
        this.rejectedCandidateLogger = Objects.requireNonNull(
                rejectedCandidateLogger,
                "rejectedCandidateLogger"
        );
    }

    @Override
    public ConfigurationReloadResult reload() {
        synchronized (active) {
            return reloadLocked();
        }
    }

    private ConfigurationReloadResult reloadLocked() {
        ReportConfigurationSnapshot candidate;
        try {
            candidate = Objects.requireNonNull(candidates.get(), "report configuration candidate");
        } catch (ConfigurationValidationException exception) {
            return validationFailed(sanitized(exception.getMessage()));
        } catch (RuntimeException exception) {
            return validationFailed("Report configuration candidate could not be loaded");
        }

        ReportConfigurationSnapshot previous = active.snapshot();
        ConfigurationReloadResult delegated = delegate.reload();
        if (!delegated.successful() || previous.equals(candidate)) {
            return delegated;
        }
        if (!active.replace(previous, candidate)) {
            return new ConfigurationReloadResult(
                    ConfigurationReloadResult.Outcome.UNAVAILABLE,
                    "Report configuration changed concurrently; the validated candidate was not applied",
                    List.of("Retry reload after reviewing the active runtime state"),
                    delegated.reasonPoliciesReloaded()
            );
        }
        ConfigurationReloadResult.Outcome outcome = delegated.outcome()
                == ConfigurationReloadResult.Outcome.NO_CHANGES
                ? ConfigurationReloadResult.Outcome.APPLIED
                : delegated.outcome();
        String message = delegated.outcome() == ConfigurationReloadResult.Outcome.NO_CHANGES
                ? "Report configuration reloaded atomically; other settings were unchanged"
                : delegated.message() + "; report configuration was replaced atomically";
        return new ConfigurationReloadResult(
                outcome,
                message,
                delegated.details(),
                delegated.reasonPoliciesReloaded()
        );
    }

    private ConfigurationReloadResult validationFailed(String detail) {
        List<String> details = List.of(detail);
        rejectedCandidateLogger.accept(details);
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.VALIDATION_FAILED,
                "Report configuration validation failed; previous runtime settings remain active",
                details,
                false
        );
    }

    private static String sanitized(String message) {
        return message == null || message.isBlank()
                ? "Report configuration is invalid"
                : message;
    }
}
