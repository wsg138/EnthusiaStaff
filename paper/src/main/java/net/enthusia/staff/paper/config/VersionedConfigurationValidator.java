package net.enthusia.staff.paper.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.enthusia.staff.paper.config.policyv2.PolicyV2Configuration;
import net.enthusia.staff.paper.config.policyv2.PolicyV2ConfigurationLoader;

public final class VersionedConfigurationValidator implements ConfigurationValidationAction {
    private static final int MAX_ERROR_LENGTH = 240;

    private final Path dataDirectory;
    private final ReportConfigurationLoader reportLoader;

    public VersionedConfigurationValidator(Path dataDirectory) {
        this(dataDirectory, new ReportConfigurationLoader());
    }

    VersionedConfigurationValidator(
            Path dataDirectory,
            ReportConfigurationLoader reportLoader
    ) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory")
                .toAbsolutePath()
                .normalize();
        this.reportLoader = Objects.requireNonNull(reportLoader, "reportLoader");
    }

    @Override
    public ConfigurationValidationReport validate() {
        List<ConfigurationValidationReport.Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        validateSource("config.yml", () -> {
            PaperConfigurationSnapshot snapshot = new PaperConfigurationLoader().load(
                    dataDirectory.resolve("config.yml"),
                    dataDirectory
            );
            return "schema " + snapshot.version();
        }, entries, errors);

        validateSource("reason-policies.yml", () -> {
            ReasonPolicyConfigurationLoader.LoadedPolicies policies =
                    new ReasonPolicyConfigurationLoader().load(dataDirectory.resolve("reason-policies.yml"));
            return "version " + policies.version();
        }, entries, errors);

        validateSource("messages.yml", () -> {
            MessageConfigurationSnapshot messages =
                    new MessageConfigurationLoader().load(dataDirectory.resolve("messages.yml"));
            return "schema " + messages.schemaVersion();
        }, entries, errors);

        validateSource("ranks.yml", () -> {
            RankConfigurationSnapshot ranks =
                    new RankConfigurationLoader().load(dataDirectory.resolve("ranks.yml"));
            return "schema " + ranks.schemaVersion() + " (preview only; not active authority)";
        }, entries, errors);

        validateReports(entries, errors);

        validateSource("policy-v2.yml", () -> {
            PolicyV2Configuration policy =
                    new PolicyV2ConfigurationLoader().load(dataDirectory.resolve("policy-v2.yml"));
            return "schema " + policy.schemaVersion()
                    + ", active " + policy.activeVersion()
                    + ", mode " + policy.mode().name();
        }, entries, errors);

        return new ConfigurationValidationReport(entries, errors);
    }

    private void validateReports(
            List<ConfigurationValidationReport.Entry> entries,
            List<String> errors
    ) {
        try {
            ReportConfigurationSnapshot reports = reportLoader.load(
                    dataDirectory.resolve("reports.yml"),
                    dataDirectory.resolve("gui").resolve("reports.yml")
            );
            entries.add(new ConfigurationValidationReport.Entry(
                    "reports.yml",
                    "version " + reports.policyVersion()
            ));
            entries.add(new ConfigurationValidationReport.Entry(
                    "gui/reports.yml",
                    "version " + reports.guiVersion()
            ));
        } catch (RuntimeException exception) {
            errors.add("reports.yml + gui/reports.yml: " + sanitized(exception));
        }
    }

    private static void validateSource(
            String source,
            Supplier<String> validator,
            List<ConfigurationValidationReport.Entry> entries,
            List<String> errors
    ) {
        try {
            entries.add(new ConfigurationValidationReport.Entry(source, validator.get()));
        } catch (RuntimeException exception) {
            errors.add(source + ": " + sanitized(exception));
        }
    }

    private static String sanitized(RuntimeException exception) {
        String message = exception.getMessage();
        String firstLine = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.lines().findFirst().orElse(exception.getClass().getSimpleName()).trim();
        return firstLine.length() <= MAX_ERROR_LENGTH
                ? firstLine
                : firstLine.substring(0, MAX_ERROR_LENGTH);
    }
}
