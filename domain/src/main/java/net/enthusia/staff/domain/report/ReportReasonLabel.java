package net.enthusia.staff.domain.report;

import java.util.Locale;

/** Presentation only: stable machine reason IDs remain unchanged in storage and policy. */
public final class ReportReasonLabel {
    private ReportReasonLabel() { }

    public static String display(String reasonId) {
        if (reasonId == null || reasonId.isBlank()) {
            return "Unspecified reason";
        }
        String[] words = reasonId.replaceAll("[._-]+", " ").trim().split("\\s+");
        StringBuilder output = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!output.isEmpty()) {
                output.append(' ');
            }
            String lower = word.toLowerCase(Locale.ROOT);
            output.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return output.toString();
    }
}
