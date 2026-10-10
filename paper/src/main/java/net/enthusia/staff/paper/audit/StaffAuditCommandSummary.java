package net.enthusia.staff.paper.audit;

/** Retains the command identity without leaking password, private-message or token arguments. */
final class StaffAuditCommandSummary {
    private static final int MAX_ROOT_LENGTH = 48;

    private StaffAuditCommandSummary() {
    }

    static String summarize(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "/unknown (arguments withheld)";
        }
        String trimmed = commandLine.stripLeading();
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))
                && end < MAX_ROOT_LENGTH) {
            end++;
        }
        String root = trimmed.substring(0, end).replaceAll("[^a-zA-Z0-9_:/.-]", "");
        if (!root.startsWith("/") || root.length() < 2) {
            return "/unknown (arguments withheld)";
        }
        return root + " (arguments withheld)";
    }
}
