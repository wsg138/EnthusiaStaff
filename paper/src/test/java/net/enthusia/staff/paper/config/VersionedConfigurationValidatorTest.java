package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VersionedConfigurationValidatorTest {
    private static final String RANKS_FILE = "ranks.yml";
    @TempDir
    Path tempDirectory;

    @Test
    void validatesAllCurrentlyRegisteredVersionedConfigurationFamilies() throws IOException {
        copyShippedConfiguration();

        ConfigurationValidationReport report = validator().validate();

        assertTrue(report.valid(), () -> String.join("; ", report.errors()));
        assertEquals(
                Set.of(
                        "config.yml",
                        "reason-policies.yml",
                        "messages.yml",
                        RANKS_FILE,
                        "reports.yml",
                        "gui/reports.yml",
                        "policy-v2.yml"
                ),
                report.entries().stream()
                        .map(ConfigurationValidationReport.Entry::source)
                        .collect(Collectors.toSet())
        );
    }

    @Test
    void oneInvalidFileDoesNotPreventIndependentValidationOfOtherFiles() throws IOException {
        copyShippedConfiguration();
        Files.writeString(tempDirectory.resolve("policy-v2.yml"), "schema-version: 999\n");

        ConfigurationValidationReport report = validator().validate();

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.startsWith("policy-v2.yml:")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("config.yml")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("reason-policies.yml")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("messages.yml")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("reports.yml")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("gui/reports.yml")));
    }

    @Test
    void invalidPreviewRanksDoNotBlockValidationOfActiveConfiguration() throws IOException {
        copyShippedConfiguration();
        Files.writeString(tempDirectory.resolve(RANKS_FILE),
                "schema-version: 1\nranks: {}\n");

        ConfigurationValidationReport report = validator().validate();

        assertFalse(report.valid());
        assertTrue(report.errors().stream().anyMatch(error -> error.startsWith("ranks.yml:")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("messages.yml")));
        assertTrue(report.entries().stream().anyMatch(entry -> entry.source().equals("policy-v2.yml")));
        assertTrue(report.entries().stream().noneMatch(entry -> entry.source().equals(RANKS_FILE)));
    }

    private VersionedConfigurationValidator validator() {
        return new VersionedConfigurationValidator(
                tempDirectory,
                new ReportConfigurationLoader(material -> true)
        );
    }

    private void copyShippedConfiguration() throws IOException {
        copyResource("config.yml");
        copyResource("reason-policies.yml");
        copyResource("messages.yml");
        copyResource(RANKS_FILE);
        copyResource("reports.yml");
        Files.createDirectories(tempDirectory.resolve("gui"));
        copyResource("gui/reports.yml");
        copyResource("policy-v2.yml");
    }

    private void copyResource(String resource) throws IOException {
        Path target = tempDirectory.resolve(resource);
        Files.createDirectories(target.getParent());
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing test resource " + resource);
            }
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
