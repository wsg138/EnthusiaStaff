package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ReportConfigurationLoaderTest {
    @Test
    void loadsDefaultPolicyAndGui() {
        ReportConfigurationSnapshot snapshot = load(resource("reports.yml"), resource("gui/reports.yml"));

        assertEquals("2026-08-02.1", snapshot.policyVersion());
        assertEquals(Duration.ofMinutes(2), snapshot.policy().anyCooldown());
        assertEquals(5, snapshot.policy().maxOpenReports());
        assertEquals(100, snapshot.policy().queryLimit());
        assertEquals(36, snapshot.gui().pageSize());
        assertEquals(54, snapshot.gui().inventorySize());
        assertEquals(36, snapshot.gui().slot("queue-open"));
        assertEquals(Material.PAPER, snapshot.gui().material("state-open"));
    }

    @Test
    void rejectsUnknownPolicyFields() {
        String policy = resource("reports.yml") + "\nunknown: true\n";

        assertThrows(
                ConfigurationValidationException.class,
                () -> load(policy, resource("gui/reports.yml"))
        );
    }

    @Test
    void rejectsOverlappingDetailSlots() {
        String gui = resource("gui/reports.yml")
                .replace("detail-reporter: 10", "detail-reporter: 4");

        assertThrows(
                ConfigurationValidationException.class,
                () -> load(resource("reports.yml"), gui)
        );
    }

    private static ReportConfigurationSnapshot load(String policy, String gui) {
        return new ReportConfigurationLoader().load(
                new ByteArrayInputStream(policy.getBytes(StandardCharsets.UTF_8)),
                "reports.yml",
                new ByteArrayInputStream(gui.getBytes(StandardCharsets.UTF_8)),
                "gui/reports.yml"
        );
    }

    private static String resource(String name) {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("missing test resource " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to read test resource " + name, exception);
        }
    }
}
