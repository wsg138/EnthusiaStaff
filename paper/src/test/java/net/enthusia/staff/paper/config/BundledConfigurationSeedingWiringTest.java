package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BundledConfigurationSeedingWiringTest {
    @Test
    void reasonPolicyResourceIsOnlySeededWhenMissing() throws IOException {
        String source = readSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java"
        ));

        assertTrue(
                source.contains("if (Files.notExists(reasonPolicyFile())) {\n"
                        + "            saveResource(\"reason-policies.yml\", false);"),
                "Existing reason-policies.yml must be loaded without asking JavaPlugin to save it again"
        );
    }

    @Test
    void reportResourcesAreOnlySeededWhenMissing() throws IOException {
        String source = readSource(paperModule().resolve(
                "src/main/java/net/enthusia/staff/paper/config/ReportConfigurationRuntime.java"
        ));

        assertTrue(
                source.contains("if (Files.notExists(policyFile(plugin))) {\n"
                        + "                plugin.saveResource(\"reports.yml\", false);"),
                "Existing reports.yml must not trigger an expected saveResource warning"
        );
        assertTrue(
                source.contains("if (Files.notExists(guiFile(plugin))) {\n"
                        + "                plugin.saveResource(\"gui/reports.yml\", false);"),
                "Existing GUI reports.yml must not trigger an expected saveResource warning"
        );
    }

    private static String readSource(Path path) throws IOException {
        // Source-text wiring assertions use LF; Git may check files out as CRLF on Windows.
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static Path paperModule() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.exists(current.resolve("src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java"))) {
            return current;
        }
        Path paper = current.resolve("paper");
        if (Files.exists(paper.resolve("src/main/java/net/enthusia/staff/paper/EnthusiaStaffPaperPlugin.java"))) {
            return paper;
        }
        throw new IllegalStateException("Could not locate the Paper module from " + current);
    }
}
