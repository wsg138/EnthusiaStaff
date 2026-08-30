from pathlib import Path


def replace_once(path, old, new):
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}")
    file.write_text(text.replace(old, new, 1))


config = "staff-bot/src/main/java/net/enthusia/staff/discordbot/ModerationPreviewWebConfig.java"
config_test = "staff-bot/src/test/java/net/enthusia/staff/discordbot/ModerationPreviewWebConfigTest.java"
docs = "docs/staff-bot-staging-ui-preview.md"

replace_once(
    config,
    '''    private static final InetAddress IPV6_LOOPBACK = literalAddress(new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    });
''',
    '''    private static final InetAddress IPV6_LOOPBACK = literalAddress(new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    });
    private static final int EPHEMERAL_PORT = 0;
    private static final int STAGING_WEB_PORT = 8_765;
    private static final InetSocketAddress IPV4_EPHEMERAL_BIND =
            new InetSocketAddress(IPV4_LOOPBACK, EPHEMERAL_PORT);
    private static final InetSocketAddress IPV6_EPHEMERAL_BIND =
            new InetSocketAddress(IPV6_LOOPBACK, EPHEMERAL_PORT);
    private static final InetSocketAddress IPV4_STAGING_BIND =
            new InetSocketAddress(IPV4_LOOPBACK, STAGING_WEB_PORT);
    private static final InetSocketAddress IPV6_STAGING_BIND =
            new InetSocketAddress(IPV6_LOOPBACK, STAGING_WEB_PORT);
''')

replace_once(
    config,
    '''    private static InetSocketAddress parseBind(String value) {
        HostAndPort parsed = parseHostAndPort(value);
        if (IPV4_LOOPBACK_HOST.equals(parsed.host()) || LOCALHOST.equalsIgnoreCase(parsed.host())) {
            return new InetSocketAddress(IPV4_LOOPBACK, parsed.port());
        }
        if (IPV6_LOOPBACK_HOST.equals(parsed.host())) {
            return new InetSocketAddress(IPV6_LOOPBACK, parsed.port());
        }
        throw new IllegalArgumentException("preview web bind must use an explicit loopback host");
    }
''',
    '''    private static InetSocketAddress parseBind(String value) {
        HostAndPort parsed = parseHostAndPort(value);
        LoopbackHost host = parseLoopbackHost(parsed.host());
        return fixedBindAddress(host, parsed.port());
    }

    private static LoopbackHost parseLoopbackHost(String host) {
        if (IPV4_LOOPBACK_HOST.equals(host) || LOCALHOST.equalsIgnoreCase(host)) {
            return LoopbackHost.IPV4;
        }
        if (IPV6_LOOPBACK_HOST.equals(host)) {
            return LoopbackHost.IPV6;
        }
        throw new IllegalArgumentException("preview web bind must use an explicit loopback host");
    }

    private static InetSocketAddress fixedBindAddress(LoopbackHost host, int port) {
        if (port == EPHEMERAL_PORT) {
            return host == LoopbackHost.IPV6 ? IPV6_EPHEMERAL_BIND : IPV4_EPHEMERAL_BIND;
        }
        if (port == STAGING_WEB_PORT) {
            return host == LoopbackHost.IPV6 ? IPV6_STAGING_BIND : IPV4_STAGING_BIND;
        }
        throw new IllegalArgumentException("preview web bind port must be 0 or 8765");
    }
''')

replace_once(
    config,
    '''    private record HostAndPort(String host, int port) {
    }
''',
    '''    private enum LoopbackHost {
        IPV4,
        IPV6
    }

    private record HostAndPort(String host, int port) {
    }
''')

replace_once(
    config_test,
    '''    @Test
    void rawNonLoopbackListenerIsRejectedEvenWithoutPublicLauncher() {
''',
    '''    @Test
    void explicitBindPortIsRestrictedToDocumentedStagingPort() {
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "127.0.0.1:9000")));
        assertThrows(IllegalArgumentException.class, () -> ModerationPreviewWebConfig.fromEnvironment(Map.of(
                ModerationPreviewWebConfig.BIND_ENV, "localhost:65535")));
    }

    @Test
    void rawNonLoopbackListenerIsRejectedEvenWithoutPublicLauncher() {
''')

replace_once(
    docs,
    '''For local-only development, `http://127.0.0.1:<port>` or `http://localhost:<port>` is permitted as the public origin. Non-loopback public HTTP is rejected.
''',
    '''For local-only development, omit the bind variable for an ephemeral loopback listener, or use the fixed staging bind port `8765` on `127.0.0.1`, `localhost`, or `[::1]`. A loopback HTTP public origin is permitted only for that local development case. Other explicit bind ports and non-loopback public HTTP are rejected.
''')
