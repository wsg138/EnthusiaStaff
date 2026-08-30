package net.enthusia.staff.discordbot;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Staging-only web-preview bind/public-address configuration. */
record ModerationPreviewWebConfig(InetSocketAddress bindAddress, Optional<URI> publicBaseUri) {
    static final String BIND_ENV = "ENTHUSIA_STAFF_BOT_UI_PREVIEW_WEB_BIND";
    static final String PUBLIC_URL_ENV = "ENTHUSIA_STAFF_BOT_UI_PREVIEW_PUBLIC_URL";
    private static final String DEFAULT_BIND = "127.0.0.1:0";
    private static final String IPV4_LOOPBACK_HOST = "127.0.0.1";
    private static final String IPV6_LOOPBACK_HOST = "::1";
    private static final String LOCALHOST = "localhost";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final InetAddress IPV4_LOOPBACK = literalAddress(new byte[] {127, 0, 0, 1});
    private static final InetAddress IPV6_LOOPBACK = literalAddress(new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    });

    ModerationPreviewWebConfig {
        Objects.requireNonNull(bindAddress, "bindAddress");
        Objects.requireNonNull(publicBaseUri, "publicBaseUri");
        if (bindAddress.getAddress() == null || !bindAddress.getAddress().isLoopbackAddress()) {
            throw new IllegalArgumentException("preview web bind must use a loopback address");
        }
        if (publicBaseUri.isPresent() && bindAddress.getPort() == 0) {
            throw new IllegalArgumentException("preview public URL requires an explicit web bind port");
        }
    }

    static ModerationPreviewWebConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        InetSocketAddress bind = parseBind(environment.getOrDefault(BIND_ENV, DEFAULT_BIND));
        String publicValue = environment.getOrDefault(PUBLIC_URL_ENV, "").trim();
        Optional<URI> publicUri = publicValue.isEmpty()
                ? Optional.empty()
                : Optional.of(parsePublicUri(publicValue));
        return new ModerationPreviewWebConfig(bind, publicUri);
    }

    boolean secureCookie() {
        return publicBaseUri.map(uri -> HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())).orElse(false);
    }

    private static InetSocketAddress parseBind(String value) {
        HostAndPort parsed = parseHostAndPort(value);
        if (IPV4_LOOPBACK_HOST.equals(parsed.host()) || LOCALHOST.equalsIgnoreCase(parsed.host())) {
            return new InetSocketAddress(IPV4_LOOPBACK, parsed.port());
        }
        if (IPV6_LOOPBACK_HOST.equals(parsed.host())) {
            return new InetSocketAddress(IPV6_LOOPBACK, parsed.port());
        }
        throw new IllegalArgumentException("preview web bind must use an explicit loopback host");
    }

    private static HostAndPort parseHostAndPort(String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        int separator = normalized.lastIndexOf(':');
        if (separator <= 0 || separator == normalized.length() - 1) {
            throw new IllegalArgumentException("preview web bind must be host:port");
        }
        String host = normalized.substring(0, separator).trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return new HostAndPort(host, parsePort(normalized.substring(separator + 1)));
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 0 || port > 65_535) {
                throw new IllegalArgumentException("preview web bind port is out of range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("preview web bind port must be numeric", exception);
        }
    }

    private static URI parsePublicUri(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("preview public URL must be an origin URL");
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new IllegalArgumentException("preview public URL must not contain a path");
        }
        if (!HTTPS_SCHEME.equalsIgnoreCase(scheme) && !localHttp(uri)) {
            throw new IllegalArgumentException("preview public URL must use HTTPS outside loopback development");
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private static boolean localHttp(URI uri) {
        if (!HTTP_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        return LOCALHOST.equalsIgnoreCase(host) || IPV4_LOOPBACK_HOST.equals(host) || IPV6_LOOPBACK_HOST.equals(host);
    }

    private static InetAddress literalAddress(byte[] address) {
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record HostAndPort(String host, int port) {
    }
}
