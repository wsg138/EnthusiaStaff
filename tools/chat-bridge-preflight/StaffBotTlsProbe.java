import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * One-shot, read-only StaffBot-origin TCP/TLS preflight, never a bridge peer.
 * Requires authorized shell execution in the actual StaffBot container.
 * No Discord or Minecraft message, authentication frame, or application payload is sent.
 */
public final class StaffBotTlsProbe {
    private static final String HOST = "25319956-7c92-49d1-9afe-ea6e18758016";
    private static final int PORT = 28765;
    private static final int MAX_CONFIG_BYTES = 16384;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int HANDSHAKE_TIMEOUT_MS = 4000;
    private static final int OVERALL_TIMEOUT_SECONDS = 10;
    private static final String PREFIX = "ENTHUSIA_STAFF_BOT_CHAT_BRIDGE_";

    private StaffBotTlsProbe() {}

    private static final int REQUIRED_ARGUMENT_COUNT = 1;
    private static final int INVALID_USAGE_EXIT_CODE = 2;
    private static final int FAILED_PROBE_EXIT_CODE = 1;
    private static final String HELP_ARGUMENT = "--help";
    private static final String VERIFIED_STATE = "TLS_VERIFIED";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length != REQUIRED_ARGUMENT_COUNT) {
            return usage(INVALID_USAGE_EXIT_CODE);
        }
        if (HELP_ARGUMENT.equals(args[0])) {
            return usage(0);
        }
        SSLContext context;
        try {
            context = configuredContext(args[0]);
        } catch (Exception exception) {
            System.out.println("PREFLIGHT=INVALID_LOCAL_CONFIG");
            return INVALID_USAGE_EXIT_CODE;
        }
        return checkConnection(context);
    }

    private static int usage(int exitCode) {
        System.out.println("Usage: java StaffBotTlsProbe <private-tls-probe.properties>");
        System.out.println("Run only in an authorized StaffBot-container shell; never in an application console.");
        return exitCode;
    }

    private static SSLContext configuredContext(String path) throws Exception {
        Properties config = loadProperties(Path.of(path));
        if (!HOST.equals(config.getProperty(PREFIX + "HOST"))
                || !Integer.toString(PORT).equals(config.getProperty(PREFIX + "PORT"))) {
            throw new IllegalArgumentException("Unapproved network target");
        }
        return tlsContext(config);
    }

    private static int checkConnection(SSLContext context) {
        ThreadFactory daemon = task -> {
            Thread thread = new Thread(task, "staffbot-tls-probe");
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService pool = Executors.newSingleThreadExecutor(daemon);
        Future<Result> future = pool.submit((Callable<Result>) () -> perform(context));
        try {
            Result result = future.get(OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("PREFLIGHT=" + result.state);
            if (result.fingerprint != null) {
                System.out.println("TLS_PEER_CERT_SHA256=" + result.fingerprint);
            }
            return VERIFIED_STATE.equals(result.state) ? 0 : FAILED_PROBE_EXIT_CODE;
        } catch (TimeoutException exception) {
            future.cancel(true);
            System.out.println("PREFLIGHT=TIMEOUT");
            return FAILED_PROBE_EXIT_CODE;
        } catch (Exception exception) {
            System.out.println("PREFLIGHT=ERROR");
            return FAILED_PROBE_EXIT_CODE;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Result perform(SSLContext context) {
        try {
            InetAddress.getAllByName(HOST);
        } catch (Exception exception) {
            return new Result("DNS_FAILED", null);
        }

        SSLSocketFactory factory = context.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
            try {
                socket.connect(new InetSocketAddress(HOST, PORT), CONNECT_TIMEOUT_MS);
            } catch (SocketTimeoutException exception) {
                return new Result("TCP_TIMEOUT", null);
            } catch (Exception exception) {
                return new Result("TCP_UNREACHABLE", null);
            }
            socket.setEnabledProtocols(new String[] {"TLSv1.3"});
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            try {
                socket.startHandshake();
                Certificate[] peer = socket.getSession().getPeerCertificates();
                if (peer.length == 0 || !(peer[0] instanceof X509Certificate)) {
                    return new Result("TLS_CERT_INVALID", null);
                }
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(peer[0].getEncoded());
                return new Result("TLS_VERIFIED", HexFormat.of().formatHex(digest));
            } catch (Exception exception) {
                return new Result("TLS_FAILED", null);
            }
        } catch (Exception exception) {
            return new Result("TCP_UNREACHABLE", null);
        }
    }

    private static SSLContext tlsContext(Properties config) throws Exception {
        String rawPath = require(config, PREFIX + "TRUST_STORE");
        char[] password = require(config, PREFIX + "TRUST_STORE_PASSWORD").toCharArray();
        try {
            Path storePath = Path.of(rawPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(storePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Missing truststore");
            }
            KeyStore trust = KeyStore.getInstance("PKCS12");
            try (SeekableByteChannel channel = Files.newByteChannel(
                    storePath, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                 InputStream input = Channels.newInputStream(channel)) {
                trust.load(input, password);
            }
            boolean hasTrustedCertificate = false;
            var aliases = trust.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (trust.isKeyEntry(alias)) {
                    throw new IllegalArgumentException("Truststore contains a private key");
                }
                hasTrustedCertificate |= trust.isCertificateEntry(alias);
            }
            if (!hasTrustedCertificate) {
                throw new IllegalArgumentException("Truststore contains no trusted certificate");
            }
            TrustManagerFactory managers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            managers.init(trust);
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(null, managers.getTrustManagers(), null);
            return context;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static Properties loadProperties(Path path) throws Exception {
        Properties props = new Properties();
        try (SeekableByteChannel channel = Files.newByteChannel(
                path.toAbsolutePath().normalize(),
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             InputStream input = Channels.newInputStream(channel)) {
            if (channel.size() > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("Config too large");
            }
            props.load(input);
        }
        return props;
    }

    private static String require(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing config field");
        }
        return value;
    }

    private record Result(String state, String fingerprint) {}
}
