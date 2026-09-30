package net.enthusia.staff.paper.integration;

import dev.rosewood.rosechat.api.staff.AutomatedModerationEvidence;
import dev.rosewood.rosechat.api.staff.AutomatedPublicMuteRequest;

final class AutomatedModerationEvidenceFormatter {
    private static final int MAX_EXPLANATION_LENGTH = 4_000;
    private static final String OMITTED_NOTICE =
            "Additional evidence omitted from this summary due to the case-note size limit.";

    private AutomatedModerationEvidenceFormatter() {
    }

    static String format(AutomatedPublicMuteRequest request) {
        StringBuilder explanation = header(request);
        boolean complete = true;
        for (int index = 0; index < request.evidence().size() && complete; index++) {
            complete = appendWithinLimit(explanation, index + 1, request.evidence().get(index));
        }
        if (!complete) {
            explanation.append(OMITTED_NOTICE);
        }
        return explanation.toString();
    }

    private static StringBuilder header(AutomatedPublicMuteRequest request) {
        return new StringBuilder(MAX_EXPLANATION_LENGTH)
                .append("RoseChat AI moderation automatic public mute; trigger_event=")
                .append(request.moderationEventId())
                .append("; strikes=").append(request.strikeCount())
                .append("; trigger_category=").append(request.category())
                .append("; trigger_severity=").append(request.severity())
                .append('\n');
    }

    private static boolean appendWithinLimit(
            StringBuilder explanation,
            int index,
            AutomatedModerationEvidence evidence
    ) {
        int start = explanation.length();
        appendEvidence(explanation, index, evidence);
        if (explanation.length() <= MAX_EXPLANATION_LENGTH - OMITTED_NOTICE.length()) {
            return true;
        }
        explanation.setLength(start);
        return false;
    }

    private static void appendEvidence(
            StringBuilder explanation,
            int index,
            AutomatedModerationEvidence evidence
    ) {
        explanation.append("Strike ").append(index)
                .append(": at=").append(evidence.occurredAt())
                .append(", event=").append(evidence.moderationEventId())
                .append(", category=").append(evidence.category())
                .append(", confidence=").append(evidence.confidence())
                .append(", severity=").append(evidence.severity())
                .append("/100\nExact message: ").append(evidence.message()).append('\n');
    }
}
