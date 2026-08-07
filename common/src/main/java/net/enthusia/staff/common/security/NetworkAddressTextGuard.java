package net.enthusia.staff.common.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects raw IP address literals before staff-entered text can reach durable storage.
 * Candidate recognition is local-only and never performs hostname or address resolution.
 */
public final class NetworkAddressTextGuard {
    private static final Pattern IPV4_CANDIDATE = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9])"
    );
    private static final Pattern IPV6_CANDIDATE = Pattern.compile(
            "(?i)(?<![0-9a-f:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![0-9a-f:])"
    );

    private NetworkAddressTextGuard() {
    }

    public static boolean containsRawAddress(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return containsValidIpv4(text) || containsValidIpv6(text);
    }

    public static void requireNoRawAddress(String text) {
        if (containsRawAddress(text)) {
            throw new IllegalArgumentException("raw network addresses are not permitted in stored text");
        }
    }

    private static boolean containsValidIpv4(String text) {
        Matcher matcher = IPV4_CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            int octetStart = 0;
            boolean valid = true;
            for (int octetIndex = 0; octetIndex < 4; octetIndex++) {
                int separator = octetIndex == 3 ? candidate.length() : candidate.indexOf('.', octetStart);
                int value = 0;
                for (int index = octetStart; index < separator; index++) {
                    value = (value * 10) + (candidate.charAt(index) - '0');
                }
                if (value > 255) {
                    valid = false;
                    break;
                }
                octetStart = separator + 1;
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsValidIpv6(String text) {
        Matcher matcher = IPV6_CANDIDATE.matcher(text);
        while (matcher.find()) {
            if (isValidIpv6Literal(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidIpv6Literal(String candidate) {
        int compression = candidate.indexOf("::");
        if (compression >= 0 && candidate.indexOf("::", compression + 2) >= 0) {
            return false;
        }
        if (compression < 0 && (candidate.startsWith(":") || candidate.endsWith(":"))) {
            return false;
        }

        int hextets = 0;
        int segmentStart = 0;
        for (int index = 0; index <= candidate.length(); index++) {
            if (index < candidate.length() && candidate.charAt(index) != ':') {
                continue;
            }
            if (index > segmentStart) {
                int length = index - segmentStart;
                if (length > 4 || !isHex(candidate, segmentStart, index)) {
                    return false;
                }
                hextets++;
            }
            segmentStart = index + 1;
        }
        return compression >= 0 ? hextets < 8 : hextets == 8;
    }

    private static boolean isHex(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lower = character >= 'a' && character <= 'f';
            boolean upper = character >= 'A' && character <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }
}
