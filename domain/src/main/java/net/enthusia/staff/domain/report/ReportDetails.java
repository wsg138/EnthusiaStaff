package net.enthusia.staff.domain.report;

import java.util.List;
import java.util.Optional;

public record ReportDetails(
        ReportSummary summary,
        String description,
        Optional<String> worldId,
        Optional<String> reporterCoordinates,
        Optional<String> targetCoordinates,
        List<String> publicChatSnapshots,
        List<String> privateMessageSnapshots,
        List<String> clientEvidenceSnapshots
) {
    public ReportDetails {
        if (summary == null || description == null || description.isBlank() || worldId == null
                || reporterCoordinates == null || targetCoordinates == null || publicChatSnapshots == null
                || privateMessageSnapshots == null || clientEvidenceSnapshots == null) {
            throw new IllegalArgumentException("report detail fields must be present");
        }
        publicChatSnapshots = List.copyOf(publicChatSnapshots);
        privateMessageSnapshots = List.copyOf(privateMessageSnapshots);
        clientEvidenceSnapshots = List.copyOf(clientEvidenceSnapshots);
    }
}
