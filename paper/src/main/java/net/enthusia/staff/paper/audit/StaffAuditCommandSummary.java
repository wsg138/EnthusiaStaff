package net.enthusia.staff.paper.audit;

/** Retains the command identity without leaking password, private-message or token arguments. */
final class StaffAuditCommandSummary {
    private static final int MAX_ROOT_LENGTH = 48;
    // Keep a bounded, exact audit label allowlist; custom commands may embed secrets in the root token.
    private static final java.util.Set<String> AUDITABLE_ROOTS = java.util.Set.of(
            "/punish", "/ban", "/unban", "/mute", "/unmute", "/kick", "/warn", "/freeze",
            "/staff", "/staffmode", "/estaff", "/vanish", "/report", "/reports",
            "/case", "/cases", "/alt", "/alts", "/invsee", "/endersee", "/gamemode",
            "/gm", "/gmc", "/gms", "/tp", "/tphere", "/tpall", "/give", "/clear",
            "/heal", "/god", "/fly", "/speed", "/effect", "/msg", "/tell", "/w",
            "/whisper", "/r", "/reply", "/login", "/register", "/aireview");

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
        String root = trimmed.substring(0, end).toLowerCase(java.util.Locale.ROOT);
        // Never try to clean an unrecognized token into a valid name: that can retain secret suffixes.
        if (!AUDITABLE_ROOTS.contains(root)) {
            return "/unknown (arguments withheld)";
        }
        return root + " (arguments withheld)";
    }
}
