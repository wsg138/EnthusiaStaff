package net.enthusia.staff.velocity;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

record PrivateReadinessEndpoint(URI uri) {
    static Optional<PrivateReadinessEndpoint> parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parse(raw));
    }

    private static PrivateReadinessEndpoint parse(String raw) {
        URI parsed = URI.create(raw.trim());
        validateStructure(parsed);
        InetAddress address = numericAddress(parsed.getHost());
        if (!allowedAddress(address)) {
            throw new IllegalArgumentException("Readiness endpoints must use a private or loopback IP address");
        }
        return new PrivateReadinessEndpoint(parsed);
    }

    private static void validateStructure(URI uri) {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException(
                    "Readiness endpoints must be plain HTTP(S) private IP URLs without credentials, query, or fragment"
            );
        }
    }

    private static InetAddress numericAddress(String rawHost) {
        String host = stripIpv6Brackets(rawHost);
        if (!isIpv4Literal(host) && !isIpv6Literal(host)) {
            throw new IllegalArgumentException("Readiness endpoint hostnames are not allowed; use a private IP literal");
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Readiness endpoint IP address is invalid", exception);
        }
    }

    private static String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (!validIpv4Part(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validIpv4Part(String part) {
        if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int value = Integer.parseInt(part);
        return value >= 0 && value <= 255;
    }

    private static boolean isIpv6Literal(String host) {
        if (!host.contains(":") || host.contains("%")) {
            return false;
        }
        for (int index = 0; index < host.length(); index++) {
            char value = host.charAt(index);
            if (value != ':' && value != '.' && Character.digit(value, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean allowedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        return address instanceof Inet6Address && isUniqueLocal(address.getAddress());
    }

    private static boolean isUniqueLocal(byte[] address) {
        return address.length == 16 && (address[0] & 0xfe) == 0xfc;
    }
}
