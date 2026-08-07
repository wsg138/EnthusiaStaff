package net.enthusia.staff.common.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects raw IP address literals before staff-entered text can reach durable storage.
 * Candidate recognition never performs hostname resolution.
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
            String[] octets = matcher.group().split("\\.", -1);
            boolean valid = true;
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value > 255) {
                    valid = false;
                    break;
                }
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
            String candidate = matcher.group();
            try {
                InetAddress address = InetAddress.getByName(candidate);
                if (address.getAddress().length == 16) {
                    return true;
                }
            } catch (UnknownHostException ignored) {
                // Colon-containing invalid literals are ordinary text.
            }
        }
        return false;
    }
}
