package net.enthusia.staff.discordbot;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/** Optional one-shot TCP/TLS proof originating from the normal live StaffBot JVM. */
final class StaffBotTlsStartupDiagnostic {
    private static final System.Logger LOGGER = System.getLogger(StaffBotTlsStartupDiagnostic.class.getName());
    private static final String HOST = "25319956-7c92-49d1-9afe-ea6e18758016";
    private static final int PORT = 28765;
    private static final int CONNECT_MS = 3000;
    private static final int HANDSHAKE_MS = 4000;
    private static final String REPORT_NAME = "staffbot-tls-diagnostic-result.txt";
    private static final String TRUSTED_PUBLIC_RESOURCE = "/velocity-channel-trusted.pem";
    private static final String EXPECTED_CERT_SHA256 =
            "63dd196e42bb0aa08221581014166431c712b629a769b4ecdc25e0448c7ad73a";

    private StaffBotTlsStartupDiagnostic() {
    }

    static void start() {
        Thread.ofPlatform().daemon(true).name("staffbot-one-shot-tls-check").start(() -> {
            Result result = run();
            if (LOGGER.isLoggable(System.Logger.Level.INFO)) {
                LOGGER.log(System.Logger.Level.INFO,
                        "staffbot_tls_diagnostic state={0} origin=staffbot_jvm",
                        result.state());
            }
            saveReport(result);
        });
    }

    @SuppressWarnings("PMD.CloseResource") // shutdownNow below is deliberate: close() could wait on stuck DNS.
    static Result run() {
        ExecutorService executor = Executors.newSingleThreadExecutor(task ->
                Thread.ofPlatform().daemon(true).name("staffbot-tls-probe-worker").unstarted(task));
        Future<Result> result = executor.submit(StaffBotTlsStartupDiagnostic::perform);
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            result.cancel(true);
            return new Result("TIMEOUT", null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result.cancel(true);
            return new Result("INTERRUPTED", null);
        } catch (ExecutionException exception) {
            return new Result("ERROR", null);
        } finally {
            executor.shutdownNow();
        }
    }

    private static Result perform() {
        SSLContext context;
        try {
            context = trustedContext();
        } catch (Exception exception) {
            return new Result("TRUSTED_CERT_INVALID", null);
        }
        try {
            InetAddress.getAllByName(HOST);
        } catch (Exception exception) {
            return new Result("DNS_FAILED", null);
        }
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            try {
                socket.connect(new InetSocketAddress(HOST, PORT), CONNECT_MS);
            } catch (SocketTimeoutException exception) {
                return new Result("TCP_TIMEOUT", null);
            } catch (Exception exception) {
                return new Result("TCP_UNREACHABLE", null);
            }
            socket.setSoTimeout(HANDSHAKE_MS);
            socket.setEnabledProtocols(new String[] {"TLSv1.3"});
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            try {
                socket.startHandshake();
                var certificates = socket.getSession().getPeerCertificates();
                if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate peer)) {
                    return new Result("TLS_CERT_INVALID", null);
                }
                String fingerprint = fingerprint(peer);
                return new Result(EXPECTED_CERT_SHA256.equals(fingerprint)
                        ? "TLS_VERIFIED" : "TLS_CERT_UNEXPECTED", fingerprint);
            } catch (Exception exception) {
                return new Result("TLS_FAILED", null);
            }
        } catch (Exception exception) {
            return new Result("TCP_UNREACHABLE", null);
        }
    }

    static X509Certificate trustedCertificate() throws Exception {
        try (InputStream input = StaffBotTlsStartupDiagnostic.class
                .getResourceAsStream(TRUSTED_PUBLIC_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing public certificate resource");
            }
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(input);
            certificate.checkValidity();
            if (!EXPECTED_CERT_SHA256.equals(fingerprint(certificate))) {
                throw new IllegalStateException("unexpected public certificate identity");
            }
            return certificate;
        }
    }

    private static SSLContext trustedContext() throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("pinned-velocity", trustedCertificate());
        TrustManagerFactory managers = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, managers.getTrustManagers(), null);
        return context;
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    private static void saveReport(Result result) {
        Path temporary = null;
        try {
            Path parent = Path.of("").toAbsolutePath().normalize();
            Path target = parent.resolve(REPORT_NAME);
            temporary = parent.resolve("." + REPORT_NAME + "-" + UUID.randomUUID() + ".tmp");
            String content = "STAFFBOT_TLS_DIAGNOSTIC_STATE=" + result.state() + System.lineSeparator()
                    + "CHECKED_UTC=" + Instant.now() + System.lineSeparator()
                    + "SOURCE=STAFFBOT_JVM" + System.lineSeparator()
                    + "TARGET=PINNED_VELOCITY_INTERNAL" + System.lineSeparator()
                    + "HMAC_CHECKED=false" + System.lineSeparator()
                    + "CHAT_MODE_CHANGED=false" + System.lineSeparator()
                    + (result.fingerprint() == null ? "" :
                    "CERT_SHA256=" + result.fingerprint() + System.lineSeparator());
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            if (LOGGER.isLoggable(System.Logger.Level.WARNING)) {
                LOGGER.log(System.Logger.Level.WARNING, "staffbot_tls_diagnostic_report_unavailable");
            }
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception ignored) {
                    // A failed best-effort cleanup must not affect the bot's normal operation.
                }
            }
        }
    }

    record Result(String state, String fingerprint) {
    }
}
