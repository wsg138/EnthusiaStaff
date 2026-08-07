package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReportWorkflowWiringTest {
    private static final Path REGISTRAR_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/PaperCommandRegistrar.java"
    );
    private static final Path REPORT_COMMAND_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/ReportCommand.java"
    );
    private static final Path REPORTS_COMMAND_SOURCE = Path.of(
            "src/main/java/net/enthusia/staff/paper/command/ReportsCommand.java"
    );

    @Test
    void registrarBindsPlayerAndStaffReportCommandsToOneDurableStore() throws IOException {
        String source = normalizedSource(REGISTRAR_SOURCE);

        assertTrue(source.contains("Supplier<net.enthusia.staff.domain.ports.ReportStore> reportStore"));
        assertTrue(source.contains("new ReportCommand("));
        assertTrue(source.contains("reportStore,"));
        assertTrue(source.contains("bindCompleting(\"report\", report, report);"));
        assertTrue(source.contains("new ReportGuiController("));
        assertTrue(source.contains("new ReportsCommand(plugin(), clock(), reportStore, workers(), reportGui)"));
        assertTrue(source.contains("bindCompleting(\"reports\", reports, reports);"));
    }

    @Test
    void playerSubmissionRetainsOfflineDirectoryAndEvidencePaths() throws IOException {
        String source = normalizedSource(REPORT_COMMAND_SOURCE);

        assertTrue(source.contains("storage.players().find(submission.targetName())"));
        assertTrue(source.contains("submission.publicChatContext()"));
        assertTrue(source.contains("dependencies.chat().privateSnapshot("));
        assertTrue(source.contains("submission.targetClientEvidence().filter("));
        assertTrue(source.contains("REPORT_RESTRICTIONS"));
        assertFalse(source.contains("RoseChat"));
    }

    @Test
    void staffFallbackExposesEvidenceOnlyBehindDedicatedPermission() throws IOException {
        String source = normalizedSource(REPORTS_COMMAND_SOURCE);

        assertTrue(source.contains("EVIDENCE_PERMISSION = \"enthusiastaff.reports.evidence\""));
        assertTrue(source.contains("sender.hasPermission(EVIDENCE_PERMISSION)"));
        assertTrue(source.contains("/reports evidence <report-id> <public|private|client> [snapshot] [page]"));
        assertTrue(source.contains("evidenceFormatter.render("));
        assertFalse(source.contains("send(sender, details.publicChatSnapshots().toString())"));
        assertFalse(source.contains("send(sender, details.privateMessageSnapshots().toString())"));
        assertFalse(source.contains("send(sender, details.clientEvidenceSnapshots().toString())"));
    }

    @Test
    void pluginMetadataKeepsTriageAndSensitiveEvidenceSeparate() throws IOException {
        JsonNode permissions = pluginMetadata().path("permissions");
        JsonNode helper = permissions.path("enthusiastaff.rank.helper").path("children");
        JsonNode developer = permissions.path("enthusiastaff.rank.developer").path("children");
        JsonNode moderator = permissions.path("enthusiastaff.rank.mod").path("children");

        assertFalse(permissions.path(ReportsCommand.EVIDENCE_PERMISSION).path("default").asBoolean());
        assertTrue(helper.path(ReportsCommand.MANAGE_PERMISSION).asBoolean());
        assertTrue(helper.path(ReportsCommand.EVIDENCE_PERMISSION).isMissingNode());
        assertTrue(developer.path(ReportsCommand.EVIDENCE_PERMISSION).asBoolean());
        assertTrue(moderator.path(ReportsCommand.EVIDENCE_PERMISSION).asBoolean());
        assertEquals(
                ReportsCommand.MANAGE_PERMISSION,
                pluginMetadata().path("commands").path("reports").path("permission").asText()
        );
    }

    @Test
    void playerReportCommandRemainsPublicWhileStaffEvidenceDoesNot() throws IOException {
        JsonNode metadata = pluginMetadata();

        assertTrue(metadata.path("commands").path("report").path("permission").isMissingNode());
        assertFalse(metadata.path("permissions").path(ReportsCommand.EVIDENCE_PERMISSION).path("default").asBoolean());
    }

    private static String normalizedSource(Path source) throws IOException {
        return Files.readString(source).replace("\r\n", "\n");
    }

    private static JsonNode pluginMetadata() throws IOException {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("plugin.yml")) {
            if (input == null) {
                throw new IOException("plugin.yml is absent from the test classpath");
            }
            return new ObjectMapper(new YAMLFactory()).readTree(input);
        }
    }
}
