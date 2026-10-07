package net.enthusia.staff.paper.punishment.policyv2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.enthusia.staff.domain.policyv2.HistoryAssessment;
import net.enthusia.staff.domain.policyv2.IncidentAttributeValue;
import net.enthusia.staff.domain.policyv2.OffensePolicy;
import net.enthusia.staff.domain.policyv2.PolicyAction;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public record PolicyV2ReviewPresentation(
        String whatHappened,
        List<String> confirmedAttributes,
        String historyExplanation,
        List<String> remedies,
        List<String> sanctionRecommendation,
        String why,
        String policyVersion,
        String approvalRoute,
        String authorityNotice
) {
    public PolicyV2ReviewPresentation {
        if (whatHappened == null || confirmedAttributes == null || historyExplanation == null
                || remedies == null || sanctionRecommendation == null || why == null
                || policyVersion == null || approvalRoute == null || authorityNotice == null) {
            throw new IllegalArgumentException("Policy v2 presentation fields must be present");
        }
        confirmedAttributes = List.copyOf(confirmedAttributes);
        remedies = List.copyOf(remedies);
        sanctionRecommendation = List.copyOf(sanctionRecommendation);
    }

    public static PolicyV2ReviewPresentation from(PolicyV2ManualReview review) {
        return new PolicyV2ReviewPresentation(
                whatHappened(review),
                attributes(review),
                history(review),
                remedies(review),
                sanctions(review),
                why(review),
                review.snapshot().version(),
                route(review.route()),
                "Shadow evaluation only — Policy v1 remains authoritative and no live punishment is applied."
        );
    }

    private static String whatHappened(PolicyV2ManualReview review) {
        if (review.draft().isPolicyGap()) {
            return review.draft().policyGapSummary().orElse("Unclassified conduct");
        }
        return review.snapshot().offense(review.finding().offenseId())
                .map(OffensePolicy::displayName)
                .orElse("Configured conduct no longer available");
    }

    private static List<String> attributes(PolicyV2ManualReview review) {
        if (review.draft().isPolicyGap()) {
            return List.of("Unclassified conduct requires manual Admin/Founder review.");
        }
        List<String> values = new ArrayList<>();
        review.finding().attributes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.add(
                        humanize(entry.getKey()) + ": " + displayValue(entry.getValue())
                ));
        return values.isEmpty() ? List.of("No additional offense-specific facts were required.") : List.copyOf(values);
    }

    private static String history(PolicyV2ManualReview review) {
        if (review.draft().isPolicyGap()) {
            return review.historyInputs().isEmpty()
                    ? "No stored behavioral history is available; no relationship was inferred."
                    : "Stored history exists, but unclassified conduct is not automatically related or scored.";
        }
        HistoryAssessment assessment = review.resolution().history();
        if (assessment.contributions().isEmpty()) {
            return "No related confirmed history affects this recommendation.";
        }
        boolean repeated = assessment.contributions().stream()
                .anyMatch(contribution -> contribution.patternPersistence() > 0.01
                        || contribution.halfLifeMultiplier() > 1.01);
        double strongest = assessment.contributions().stream()
                .mapToDouble(HistoryAssessment.Contribution::decayFactor)
                .max()
                .orElse(0.0);
        String age = strongest >= 0.75 ? "Related history is still strongly relevant."
                : strongest >= 0.25 ? "Related history has partly decayed."
                : "Related history is mostly decayed but remains relevant.";
        return repeated
                ? age + " A recent related pattern is slowing decay; that pattern also fades during clean time."
                : age;
    }

    private static List<String> remedies(PolicyV2ManualReview review) {
        if (review.resolution().remedies().isEmpty()) {
            return List.of("No mandatory remedy is configured.");
        }
        return review.resolution().remedies().stream()
                .map(PolicyV2ReviewPresentation::remedy)
                .toList();
    }

    private static String remedy(RemedySpec remedy) {
        return switch (remedy.type()) {
            case REMOVE_CONTENT -> "Remove content: " + remedy.description();
            case CONFISCATE -> "Confiscate: " + remedy.description();
            case ACCESS_RESTRICTION -> "Access restriction: " + remedy.description();
            case CORRECT_PROFILE -> "Correct profile: " + remedy.description();
            case OTHER -> remedy.description();
        };
    }

    private static List<String> sanctions(PolicyV2ManualReview review) {
        if (review.resolution().action() instanceof PolicyAction.NoSanction) {
            return List.of("No punitive sanction is configured; apply only the listed remedies or compliance conditions.");
        }
        if (review.resolution().action() instanceof PolicyAction.Exact exact) {
            return exact.sanctions().stream().map(PolicyV2ReviewPresentation::sanction).toList();
        }
        if (review.resolution().action() instanceof PolicyAction.Bounded bounded) {
            List<String> options = new ArrayList<>();
            for (int index = 0; index < bounded.allowedOptions().size(); index++) {
                String joined = bounded.allowedOptions().get(index).stream()
                        .map(PolicyV2ReviewPresentation::sanction)
                        .collect(java.util.stream.Collectors.joining(" + "));
                options.add("Allowed option " + (index + 1) + ": " + joined);
            }
            return List.copyOf(options);
        }
        return List.of("No punishment may be invented; manual review is required.");
    }

    private static String sanction(SanctionSpec sanction) {
        String label = humanize(sanction.type().name());
        return sanction.length().isInstant() ? label : label + " — " + length(sanction.length());
    }

    private static String length(SanctionLength length) {
        if (length.isPermanent()) {
            return "Permanent";
        }
        Duration duration = length.temporary().orElseThrow();
        if (duration.toDays() > 0 && duration.toHours() % 24 == 0) {
            return duration.toDays() + (duration.toDays() == 1 ? " day" : " days");
        }
        if (duration.toHours() > 0 && duration.toMinutes() % 60 == 0) {
            return duration.toHours() + (duration.toHours() == 1 ? " hour" : " hours");
        }
        return duration.toMinutes() + (duration.toMinutes() == 1 ? " minute" : " minutes");
    }

    private static String why(PolicyV2ManualReview review) {
        if (review.resolution().action() instanceof PolicyAction.RequiresReview) {
            return "Configured policy cannot safely resolve these facts; Admin/Founder review is required.";
        }
        if (review.resolution().action() instanceof PolicyAction.Bounded) {
            return "Configured policy matched the confirmed conduct and history and allows a limited approved choice.";
        }
        if (review.resolution().action() instanceof PolicyAction.NoSanction) {
            return "Configured policy intentionally requires no punitive sanction; only the listed remedies or compliance conditions apply.";
        }
        return "Configured policy matched the confirmed conduct and current related history.";
    }

    private static String route(PolicyV2ManualReview.ApprovalRoute route) {
        return switch (route) {
            case DIRECT_CONFIRM -> "Confirm directly after an explicit Policy v2 cutover";
            case APPROVAL_REQUIRED -> "Request approval after an explicit Policy v2 cutover";
            case ADMIN_FOUNDER_REVIEW -> "Admin/Founder review required";
        };
    }

    private static String displayValue(IncidentAttributeValue value) {
        if (value instanceof IncidentAttributeValue.BooleanValue booleanValue) {
            return booleanValue.value() ? "Yes" : "No";
        }
        if (value instanceof IncidentAttributeValue.IntegerValue integerValue) {
            return Long.toString(integerValue.value());
        }
        if (value instanceof IncidentAttributeValue.EnumValue enumValue) {
            return humanize(enumValue.value());
        }
        return ((IncidentAttributeValue.TextValue) value).value();
    }

    static String humanize(String raw) {
        String normalized = raw.replace('.', ' ').replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isEmpty()) {
            return "Value";
        }
        String[] words = normalized.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
