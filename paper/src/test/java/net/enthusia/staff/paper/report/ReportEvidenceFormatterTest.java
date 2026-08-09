package net.enthusia.staff.paper.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.report.ReportDetails;
import net.enthusia.staff.domain.report.ReportState;
import net.enthusia.staff.domain.report.ReportSummary;
import net.enthusia.staff.paper.report.ReportEvidenceFormatter.EvidenceKind;
import net.enthusia.staff.paper.report.ReportEvidenceFormatter.EvidencePage;
import org.junit.jupiter.api.Test;

final class ReportEvidenceFormatterTest {
    private final ReportEvidenceFormatter formatter = new ReportEvidenceFormatter();

    @Test
    void publicChatIsRenderedAsBoundedFiveMessagePages() {
        StringBuilder snapshot = new StringBuilder(512).append('[');
        for (int index = 0; index < 6; index++) {
            if (index > 0) {
                snapshot.append(',');
            }
            snapshot.append("{\"senderName\":\"Player")
                    .append(index)
                    .append("\",\"body\":\"message ")
                    .append(index)
                    .append("\",\"sentAt\":\"2026-08-07T12:00:00Z\"}");
        }
        snapshot.append(']');
        ReportDetails details = details(List.of(snapshot.toString()), List.of(), List.of());

        EvidencePage first = formatter.render(details, EvidenceKind.PUBLIC_CHAT, 1, 1);
        EvidencePage second = formatter.render(details, EvidenceKind.PUBLIC_CHAT, 1, 2);

        assertEquals(5, first.lines().size());
        assertEquals(1, second.lines().size());
        assertEquals(2, first.totalPages());
        assertTrue(first.lines().getFirst().contains("Player0: message 0"));
        assertTrue(second.lines().getFirst().contains("Player5: message 5"));
    }

    @Test
    void oversizedChatIdentityFieldsAreBounded() {
        String oversizedSender = "S".repeat(1_100);
        String stored = "[{\"senderName\":\"" + oversizedSender
                + "\",\"body\":\"bounded body\",\"sentAt\":\"2026-08-07T12:00:00Z\"}]";

        EvidencePage page = formatter.render(
                details(List.of(stored), List.of(), List.of()),
                EvidenceKind.PUBLIC_CHAT,
                1,
                1
        );
        String line = page.lines().getFirst();

        assertTrue(line.contains("S".repeat(1_000) + "…: bounded body"));
        assertFalse(line.contains("S".repeat(1_001)));
    }

    @Test
    void privateMessagesRetainDirectionWithoutReturningRawJson() {
        String stored = "[{\"senderName\":\"Reporter\",\"recipientName\":\"Target\","
                + "\"body\":\"private context\",\"sentAt\":\"2026-08-07T12:00:00Z\"}]";

        EvidencePage page = formatter.render(
                details(List.of(), List.of(stored), List.of()),
                EvidenceKind.PRIVATE_MESSAGES,
                1,
                1
        );

        assertEquals(1, page.lines().size());
        assertTrue(page.lines().getFirst().contains("Reporter -> Target: private context"));
        assertFalse(page.lines().getFirst().contains("senderName"));
    }

    @Test
    void clientEvidenceUsesAllowlistAndWithholdsOpaqueMetadata() {
        String stored = "{"
                + "\"capturedAt\":\"2026-08-07T12:00:00Z\","
                + "\"platform\":\"BEDROCK\","
                + "\"protocolVersion\":800,"
                + "\"minecraftVersion\":\"1.21.11\","
                + "\"reportedBrand\":\"Geyser\","
                + "\"viaVersionStatus\":\"AVAILABLE\","
                + "\"floodgateStatus\":\"AVAILABLE\","
                + "\"floodgatePlayer\":true,"
                + "\"geyserStatus\":\"AVAILABLE\","
                + "\"autoClickerStatus\":\"AVAILABLE\","
                + "\"autoClickerHandshake\":{\"modVersion\":\"1.2.3\","
                + "\"futureSecret\":\"must-not-render\"},"
                + "\"polarStatus\":\"AVAILABLE\","
                + "\"polarMetadata\":\"opaque-private-provider-value\"}"
                ;

        EvidencePage first = formatter.render(
                details(List.of(), List.of(), List.of(stored)),
                EvidenceKind.CLIENT,
                1,
                1
        );
        EvidencePage second = formatter.render(
                details(List.of(), List.of(), List.of(stored)),
                EvidenceKind.CLIENT,
                1,
                2
        );
        List<String> all = java.util.stream.Stream.concat(first.lines().stream(), second.lines().stream()).toList();

        assertTrue(all.stream().anyMatch(line -> line.equals("Platform: BEDROCK")));
        assertTrue(all.stream().anyMatch(line -> line.equals("ViaVersion: AVAILABLE")));
        assertTrue(all.stream().anyMatch(line -> line.equals("AutoClicker mod version: 1.2.3")));
        assertTrue(all.stream().anyMatch(line -> line.equals("Polar metadata: withheld from chat presentation")));
        assertFalse(all.stream().anyMatch(line -> line.contains("opaque-private-provider-value")));
        assertFalse(all.stream().anyMatch(line -> line.contains("must-not-render")));
    }

    @Test
    void structuredAllowlistedClientValuesAreWithheldWithoutRenderingNestedData() {
        String stored = "{\"reportedBrand\":{\"secret\":\"must-not-render\"},"
                + "\"floodgatePlayer\":[\"also-secret\"]}";

        EvidencePage page = formatter.render(
                details(List.of(), List.of(), List.of(stored)),
                EvidenceKind.CLIENT,
                1,
                1
        );

        assertTrue(page.lines().contains("Brand: withheld (unexpected structured value)"));
        assertTrue(page.lines().contains("Floodgate player: withheld (unexpected structured value)"));
        assertFalse(page.lines().stream().anyMatch(line -> line.contains("must-not-render") || line.contains("also-secret")));
    }

    @Test
    void newestSnapshotCanBeSelectedWithoutKnowingItsIndex() {
        ReportDetails details = details(
                List.of("[]", "[{\"senderName\":\"New\",\"body\":\"latest\","
                        + "\"sentAt\":\"2026-08-07T12:00:00Z\"}]"),
                List.of(),
                List.of()
        );

        EvidencePage page = formatter.render(details, EvidenceKind.PUBLIC_CHAT, 0, 1);

        assertEquals(2, page.snapshot());
        assertTrue(page.lines().getFirst().contains("New: latest"));
    }

    @Test
    void invalidSnapshotAndPageAreRejected() {
        ReportDetails details = details(List.of("[]"), List.of(), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> formatter.render(details, EvidenceKind.PUBLIC_CHAT, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> formatter.render(details, EvidenceKind.PUBLIC_CHAT, 1, 2));
    }

    @Test
    void missingEvidenceReturnsNoContentPage() {
        EvidencePage page = formatter.render(details(List.of(), List.of(), List.of()), EvidenceKind.CLIENT, 0, 1);

        assertEquals(0, page.totalSnapshots());
        assertEquals(List.of("No retained evidence is available."), page.lines());
    }

    @Test
    void aliasesAreExplicitAndUnknownKindsAreRejected() {
        assertEquals(EvidenceKind.PUBLIC_CHAT, formatter.parseKind("chat").orElseThrow());
        assertEquals(EvidenceKind.PRIVATE_MESSAGES, formatter.parseKind("PM").orElseThrow());
        assertEquals(EvidenceKind.CLIENT, formatter.parseKind("client").orElseThrow());
        assertTrue(formatter.parseKind("attachment").isEmpty());
    }

    private static ReportDetails details(
            List<String> publicChat,
            List<String> privateMessages,
            List<String> clientEvidence
    ) {
        return new ReportDetails(
                new ReportSummary(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "chat.spam",
                        ReportState.OPEN,
                        Optional.empty(),
                        "smp",
                        Instant.parse("2026-08-07T12:00:00Z"),
                        Instant.parse("2026-08-07T12:00:00Z"),
                        0
                ),
                "Report description",
                Optional.of("minecraft:overworld"),
                Optional.of("1,64,1"),
                Optional.of("2,64,2"),
                publicChat,
                privateMessages,
                clientEvidence
        );
    }
}