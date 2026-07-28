package net.enthusia.staff.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

final class TlsStoreFixture {
    private static final String ALIAS = "channel-server";
    private static final String STORE_TYPE = "PKCS12";
    private static final String PASSPHRASE = "temporary-fixture-only";

    private TlsStoreFixture() {
    }

    static Stores create(Path directory) throws IOException, InterruptedException {
        Path keyStore = directory.resolve("channel-server.p12");
        Path certificate = directory.resolve("channel-server.cer");
        Path trustStore = directory.resolve("channel-trust.p12");

        runKeytool(
                "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "EC",
                "-groupname", "secp256r1",
                "-sigalg", "SHA256withECDSA",
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost",
                "-ext", "EKU=serverAuth",
                "-validity", "2",
                "-storetype", STORE_TYPE,
                "-keystore", keyStore.toString(),
                "-storepass", PASSPHRASE,
                "-keypass", PASSPHRASE,
                "-noprompt"
        );
        runKeytool(
                "-exportcert",
                "-alias", ALIAS,
                "-keystore", keyStore.toString(),
                "-storepass", PASSPHRASE,
                "-file", certificate.toString(),
                "-rfc"
        );
        runKeytool(
                "-importcert",
                "-alias", ALIAS,
                "-file", certificate.toString(),
                "-keystore", trustStore.toString(),
                "-storetype", STORE_TYPE,
                "-storepass", PASSPHRASE,
                "-noprompt"
        );
        return new Stores(keyStore, trustStore);
    }

    static char[] password() {
        return PASSPHRASE.toCharArray();
    }

    private static void runKeytool(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(keytoolExecutable().toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished;
        try {
            finished = process.waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("keytool timed out while creating a temporary TLS test store");
        }
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (process.exitValue() != 0) {
            throw new IOException("keytool failed while creating a temporary TLS test store: " + output);
        }
    }

    private static Path keytoolExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "keytool.exe"
                : "keytool";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    record Stores(Path keyStore, Path trustStore) {
    }
}
