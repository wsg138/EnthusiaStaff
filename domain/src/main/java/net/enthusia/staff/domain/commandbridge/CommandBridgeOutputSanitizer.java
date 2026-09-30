package net.enthusia.staff.domain.commandbridge;

import java.util.regex.Pattern;

/** Bounds console output and strips common secret, private-path, and moderation-evidence surfaces. */
public final class CommandBridgeOutputSanitizer {
    static final int MAX_OUTPUT_CHARACTERS = 2_000;
    static final int MAX_OUTPUT_LINES = 20;

    private static final Pattern WHOLE_SECRET_LINE = Pattern.compile("(?i)\\b(?:authorization|cookie)\\b");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[_-]?key|jdbc(?:url)?)[\\s:=]+\\S+"
    );
    private static final Pattern PRIVATE_PATH = Pattern.compile(
            "(?i)(?:[A-Z]:\\\\|/(?:home|root|mnt|srv|opt|var/lib)/)\\S+"
    );
    private static final Pattern SENSITIVE_EVIDENCE = Pattern.compile(
            "(?i)\\b(?:network[_ -]?identity|private[_ -]?message|evidence[_ -]?(?:json|body)|coordinates?|ip[_ -]?address)\\b"
    );

    public Sanitized sanitize(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return new Sanitized("", false, false);
        }
        String normalized = rawOutput.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder safe = new StringBuilder(Math.min(normalized.length(), MAX_OUTPUT_CHARACTERS));
        boolean redacted = false;
        boolean truncated = lines.length > MAX_OUTPUT_LINES;
        for (int index = 0; index < lines.length && index < MAX_OUTPUT_LINES; index++) {
            String sanitized = redactLine(lines[index]);
            redacted |= !sanitized.equals(lines[index]);
            if (!appendBounded(safe, sanitized, index > 0)) {
                truncated = true;
                break;
            }
        }
        return new Sanitized(safe.toString(), truncated, redacted);
    }

    private static String redactLine(String line) {
        if (WHOLE_SECRET_LINE.matcher(line).find()) {
            return "[redacted-secret-output]";
        }
        if (SENSITIVE_EVIDENCE.matcher(line).find()) {
            return "[redacted-sensitive-output]";
        }
        String safe = SECRET.matcher(line).replaceAll("$1=[redacted]");
        return PRIVATE_PATH.matcher(safe).replaceAll("[private-path]");
    }

    private static boolean appendBounded(StringBuilder target, String line, boolean newline) {
        int prefix = newline ? 1 : 0;
        int remaining = MAX_OUTPUT_CHARACTERS - target.length();
        if (remaining <= prefix) {
            return false;
        }
        if (newline) {
            target.append('\n');
            remaining--;
        }
        if (line.length() <= remaining) {
            target.append(line);
            return true;
        }
        target.append(line, 0, remaining);
        return false;
    }

    public record Sanitized(String text, boolean truncated, boolean redacted) {
    }
}
