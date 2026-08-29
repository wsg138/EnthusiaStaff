package net.enthusia.staff.discordbot;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Staging-only web-preview bind/public-address configuration. */
record ModerationPreviewWebConfig(InetSocketAddress bindAddress, Optional<URI> publicBaseUri) {
    static final String BIND_ENV = "ENTHUSIA_STAFF_BOT_UI_PREVIEW_WEB_BIND";
    static final String PUBLIC_URL_ENV = "ENTHUSIA_STAFF_BOT_UI_PREVIEW_PUBLIC_URL";
    private static final String DEFAULT_BIND = "127.0.0.1:0";

    ModerationPreviewWebConfig {
        Objects.requireNonNull(bindAddress, "bindAddress");
        Objects.requireNonNull(publicBaseUri, "publicBaseUri");
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
        return publicBaseUri.map(uri -> "https".equalsIgnoreCase(uri.getScheme())).orElse(false);
    }

    private static InetSocketAddress parseBind(String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        int separator = normalized.lastIndexOf(':');
        if (separator <= 0 || separator == normalized.length() - 1) {
            throw new IllegalArgumentException("preview web bind must be host:port");
        }
        String host = normalized.substring(0, separator).trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        int port = parsePort(normalized.substring(separator + 1));
        return new InetSocketAddress(host, port);
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
        if (!"https".equalsIgnoreCase(scheme) && !localHttp(uri)) {
            throw new IllegalArgumentException("preview public URL must use HTTPS outside loopback development");
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private static boolean localHttp(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }
}
