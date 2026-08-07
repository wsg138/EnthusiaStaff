package net.enthusia.staff.common.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects raw IP address literals before staff-entered text can reach durable storage.
 * Candidate recognition is local-only and never performs hostname or address resolution.
 */
public final class NetworkAddressTextGuard {
    private static final int IPV4_OCTETS = 4;
    private static final int IPV4_MAX_OCTET = 255;
    private static final int IPV6_FULL_COLON_COUNT = 7;
    private static final String IPV6_COMPRESSED_SEPARATOR = "::";
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
        return containsValidIpv4(text) || containsIpv6AddressShape(text);
    }

    public static void requireNoRawAddress(String text) {
        if (containsRawAddress(text)) {
            throw new IllegalArgumentException("raw network addresses are not permitted in stored text");
        }
    }

    private static boolean containsValidIpv4(String text) {
        Matcher matcher = IPV4_CANDIDATE.matcher(text);
        while (matcher.find()) {
            if (hasValidIpv4Octets(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValidIpv4Octets(String candidate) {
        String[] octets = candidate.split("\\.", -1);
        if (octets.length != IPV4_OCTETS) {
            return false;
        }
        for (String octet : octets) {
            if (Integer.parseInt(octet) > IPV4_MAX_OCTET) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIpv6AddressShape(String text) {
        Matcher matcher = IPV6_CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate.contains(IPV6_COMPRESSED_SEPARATOR) || colonCount(candidate) >= IPV6_FULL_COLON_COUNT) {
                return true;
            }
        }
        return false;
    }

    private static int colonCount(String candidate) {
        int count = 0;
        for (int index = 0; index < candidate.length(); index++) {
            if (candidate.charAt(index) == ':') {
                count++;
            }
        }
        return count;
    }
}
