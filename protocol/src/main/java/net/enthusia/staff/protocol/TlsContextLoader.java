package net.enthusia.staff.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Loads the explicit PKCS#12 material used by the persistent Paper–Velocity channel.
 */
public final class TlsContextLoader {
    private static final String STORE_TYPE = "PKCS12";

    private TlsContextLoader() {
    }

    public static SSLContext client(Path trustStorePath, char[] password) {
        KeyStore trustStore = loadStore(trustStorePath, password, "trust");
        try {
            if (!containsTrustedCertificate(trustStore)) {
                throw new IllegalStateException("Channel TLS trust store contains no trusted certificate entries");
            }
            TrustManagerFactory trustManagers =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize channel TLS trust", exception);
        }
    }

    public static SSLContext server(Path keyStorePath, char[] password) {
        KeyStore keyStore = loadStore(keyStorePath, password, "key");
        try {
            if (!containsPrivateKeyEntry(keyStore)) {
                throw new IllegalStateException("Channel TLS key store contains no private-key entry");
            }
            KeyManagerFactory keyManagers =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(keyManagers.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize channel TLS identity", exception);
        }
    }

    private static KeyStore loadStore(Path path, char[] password, String purpose) {
        Objects.requireNonNull(path, "path");
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Channel TLS store password is required");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Channel TLS " + purpose + " store is not a regular file");
        }
        try (InputStream input = Files.newInputStream(path)) {
            KeyStore store = KeyStore.getInstance(STORE_TYPE);
            store.load(input, password);
            return store;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to load channel TLS " + purpose + " store", exception);
        }
    }

    private static boolean containsTrustedCertificate(KeyStore store) throws GeneralSecurityException {
        var aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            if (store.isCertificateEntry(aliases.nextElement())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPrivateKeyEntry(KeyStore store) throws GeneralSecurityException {
        var aliases = store.aliases();
        while (aliases.hasMoreElements()) {
            if (store.entryInstanceOf(aliases.nextElement(), KeyStore.PrivateKeyEntry.class)) {
                return true;
            }
        }
        return false;
    }
}
