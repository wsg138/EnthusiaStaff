package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class AppliedMigrationChecksumTest {
    private static final Map<String, Integer> DEPLOYED_CHECKSUMS = Map.of(
            "db/migration/V11__durable_punishment_request_alerts.sql", -2005375055,
            "db/migration/V12__recipient_specific_staff_alert_deliveries.sql", -1787751803,
            "db/migration/V13__terminal_alert_delivery_reconciliation.sql", 1189066017
    );

    @Test
    void deployedMigrationsRemainByteCompatibleWithFlywayHistory() throws IOException {
        for (Map.Entry<String, Integer> migration : DEPLOYED_CHECKSUMS.entrySet()) {
            assertEquals(
                    migration.getValue(),
                    flywayChecksum(migration.getKey()),
                    () -> migration.getKey() + " was modified after deployment; add a new migration instead"
            );
        }
    }

    private static int flywayChecksum(String resourceName) throws IOException {
        CRC32 checksum = new CRC32();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IOException("Missing migration resource: " + resourceName);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    checksum.update(line.getBytes(StandardCharsets.UTF_8));
                    line = reader.readLine();
                }
            }
        }
        return (int) checksum.getValue();
    }
}
