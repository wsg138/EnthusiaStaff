package net.enthusia.staff.paper.config;

import java.io.IOException;
import java.io.InputStream;
import org.bukkit.Material;

public final class ReportConfigurationTestFixtures {
    private ReportConfigurationTestFixtures() {
    }

    public static ReportConfigurationSnapshot defaults() {
        try (InputStream policy = resource("reports.yml");
             InputStream gui = resource("gui/reports.yml")) {
            return new ReportConfigurationLoader(ReportConfigurationTestFixtures::testItemMaterial)
                    .load(policy, "reports.yml", gui, "gui/reports.yml");
        } catch (IOException exception) {
            throw new IllegalStateException("unable to close report configuration resources", exception);
        }
    }

    private static boolean testItemMaterial(Material material) {
        return material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR;
    }

    private static InputStream resource(String name) {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IllegalStateException("missing test resource " + name);
        }
        return input;
    }
}
