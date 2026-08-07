package net.enthusia.staff.common.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rejects raw IP address literals before staff-entered text can reach durable storage.
 * This class never resolves hostnames: candidates must already have IPv4 or IPv6 literal shape.
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
        return containsValidLiteral(text, IPV4_CANDIDATE, false)
                || containsValidLiteral(text, IPV6_CANDIDATE, true);
    }

    public static void requireNoRawAddress(String text) {
        if (containsRawAddress(text)) {
            throw new IllegalArgumentException("raw network addresses are not permitted in stored text");
        }
    }

    private static boolean containsValidLiteral(String text, Pattern pattern, boolean requireColon) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (requireColon && candidate.indexOf(':') < 0) {
                continue;
            }
            try {
                InetAddress address = InetAddress.getByName(candidate);
                if ((requireColon && address.getAddress().length == 16)
                        || (!requireColon && address.getAddress().length == 4)) {
                    return true;
                }
            } catch (UnknownHostException ignored) {
                // Literal-shaped but invalid candidates are ordinary text, not network identity.
            }
        }
        return false;
    }
}
