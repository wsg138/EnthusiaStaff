package net.enthusia.staff.paper.aireview;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.enthusia.staff.paper.aireview.AiReviewModels.Advisory;
import net.enthusia.staff.paper.aireview.AiReviewModels.ContextEvidence;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;

final class AiReviewPresentation {
    private AiReviewPresentation() {
    }

    static List<String> queueLore(ReviewItem item, Instant now, AiReviewConfiguration config) {
        List<String> lines = new ArrayList<>();
        lines.add("Priority: " + item.reviewPriority());
        lines.add("Source: " + item.platform() + " / " + item.channelProfile());
        lines.add("Decision: " + item.messageAction() + " / " + item.semanticLabel());
        lines.add("Age: " + age(item.occurredAt(), now));
        List<String> reasons = item.reasonCodes().stream()
                .limit(config.maximumReasonCodes())
                .toList();
        if (!reasons.isEmpty()) {
            lines.add("Reasons: " + bounded(String.join(", ", reasons), 120));
        }
        if (item.incidentId() != null && !item.incidentId().isBlank()) {
            lines.add("Incident: " + bounded(item.incidentId(), 36));
        }
        return List.copyOf(lines);
    }

    static List<String> detailLines(EventDetails details, AiReviewConfiguration config) {
        List<String> lines = new ArrayList<>();
        lines.add("Event: " + bounded(details.eventId(), 48));
        lines.add("Source: " + details.platform() + " / " + details.channelProfile());
        lines.add("Scope: " + bounded(details.scopeId(), 64)
                + (details.channelId() == null ? "" : " channel=" + bounded(details.channelId(), 32)));
        lines.add("Occurred: " + details.occurredAt());
        lines.add("Message: " + bounded(details.text(), config.maximumMessageCharacters()));
        lines.add("Decision: " + details.decision().messageAction()
                + " / " + details.decision().semanticLabel()
                + " / review=" + details.decision().reviewPriority());
        lines.add("Strike/containment/support: " + details.decision().strikeRecommendation()
                + " / " + details.decision().containment()
                + " / " + details.decision().supportFlow());
        lines.add("Model: " + bounded(details.decision().localModelVersion(), 80)
                + " policy=" + bounded(details.decision().policyVersion(), 40));
        boundedEntries(details.decision().scores(), config.maximumScores(), "Scores", lines);
        List<String> reasons = details.decision().reasonCodes().stream()
                .limit(config.maximumReasonCodes())
                .toList();
        if (!reasons.isEmpty()) {
            lines.add("Reasons: " + bounded(String.join(", ", reasons), 240));
        }
        Advisory advisory = details.advisory();
        if (advisory != null) {
            lines.add("Advisory: " + advisory.status()
                    + (advisory.model() == null ? "" : " model=" + bounded(advisory.model(), 60)));
            boundedEntries(advisory.scores(), config.maximumScores(), "Advisory scores", lines);
        }
        int contextLimit = Math.min(config.maximumContextItems(), details.contextEvidence().size());
        for (int index = 0; index < contextLimit; index++) {
            ContextEvidence evidence = details.contextEvidence().get(index);
            lines.add("Context " + (index + 1) + ": "
                    + bounded(evidence.text(), Math.min(240, config.maximumMessageCharacters())));
        }
        details.corrections().stream()
                .sorted(Comparator.comparing(AiReviewModels.Correction::createdAt).reversed())
                .limit(6)
                .forEach(correction -> lines.add(
                        "Correction " + bounded(correction.proposalId(), 20)
                                + ": " + correction.status()
                                + " approvals=" + correction.approvals()
                                + " rejections=" + correction.rejections()
                ));
        if (details.acceptedCorrection() != null) {
            lines.add("Accepted correction: "
                    + bounded(details.acceptedCorrection().proposalId(), 28));
        }
        return lines.stream().map(line -> bounded(line, 1_000)).toList();
    }

    private static void boundedEntries(
            Map<String, Double> source,
            int limit,
            String label,
            List<String> output
    ) {
        if (source.isEmpty()) {
            return;
        }
        String joined = source.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> entry.getKey() + "=" + String.format(java.util.Locale.ROOT, "%.3f", entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
        output.add(label + ": " + bounded(joined, 240));
    }

    static String bounded(String value, int maximum) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() <= maximum) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maximum - 1)) + "…";
    }

    private static String age(Instant occurredAt, Instant now) {
        Duration age = Duration.between(occurredAt, now);
        if (age.isNegative()) {
            return "now";
        }
        long minutes = age.toMinutes();
        if (minutes < 1) {
            return "<1m";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = age.toHours();
        return hours < 48 ? hours + "h" : age.toDays() + "d";
    }
}
