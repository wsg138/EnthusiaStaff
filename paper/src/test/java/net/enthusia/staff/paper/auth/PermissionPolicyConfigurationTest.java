package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PermissionPolicyConfigurationTest {
    private static final Set<String> MUTATION_PERMISSIONS = Set.of(
            "enthusiastaff.punish",
            "enthusiastaff.punish.configured",
            "enthusiastaff.punish.ip",
            "enthusiastaff.punish.custom-duration",
            "enthusiastaff.punish.custom-combination",
            "enthusiastaff.remove",
            "enthusiastaff.remove.lower",
            "enthusiastaff.remove.raise",
            "enthusiastaff.remove.custom-duration",
            "enthusiastaff.remove.end",
            "enthusiastaff.remove.revoke",
            "enthusiastaff.remove.request-overturn",
            "enthusiastaff.remove.full-overturn",
            "enthusiastaff.remove.approve-overturn",
            "enthusiastaff.confiscate.economy",
            "enthusiastaff.confiscate.items",
            "enthusiastaff.case.restoreitems",
            "enthusiastaff.market.restrict",
            "enthusiastaff.reputation.restrict",
            "enthusiastaff.owner.recovery"
    );

    @Test
    void everyPermissionIsExplicitlyNonDefault() throws IOException {
        JsonNode permissions = permissions();
        assertFalse(permissions.isEmpty(), "plugin.yml must declare permissions");
        permissions.properties().forEach(entry -> assertEquals(
                "false",
                entry.getValue().path("default").asText(),
                entry.getKey()
        ));
    }

    @Test
    void developerRetainsOperationalToolsButNoPunishmentMutation() throws IOException {
        JsonNode permissions = permissions();
        Set<String> effective = effectiveChildren(
                permissions,
                "enthusiastaff.rank.developer",
                new HashSet<>()
        );

        assertTrue(effective.containsAll(Set.of(
                "enthusiastaff.status",
                "enthusiastaff.verify",
                "enthusiastaff.reload",
                "enthusiastaff.diagnostics",
                "enthusiastaff.punishment.read",
                "enthusiastaff.case.read",
                "enthusiastaff.reports.manage",
                "enthusiastaff.staffmode",
                "enthusiastaff.vanish",
                "enthusiastaff.client",
                "enthusiastaff.inventory.view",
                "enthusiastaff.inventory.edit",
                "enthusiastaff.inspect"
        )));
        MUTATION_PERMISSIONS.forEach(permission -> assertFalse(
                effective.contains(permission),
                permission
        ));
    }

    @Test
    void rankPermissionInheritanceMatchesTheAuthoritativePolicy() throws IOException {
        JsonNode permissions = permissions();
        Set<String> mod = effectiveChildren(permissions, "enthusiastaff.rank.mod", new HashSet<>());
        Set<String> admin = effectiveChildren(permissions, "enthusiastaff.rank.admin", new HashSet<>());
        Set<String> founder = effectiveChildren(permissions, "enthusiastaff.rank.founder", new HashSet<>());

        assertTrue(mod.containsAll(Set.of(
                "enthusiastaff.punish.configured",
                "enthusiastaff.remove.lower",
                "enthusiastaff.remove.end",
                "enthusiastaff.remove.revoke",
                "enthusiastaff.remove.request-overturn",
                "enthusiastaff.confiscate.economy",
                "enthusiastaff.confiscate.items"
        )));
        assertFalse(mod.contains("enthusiastaff.remove.raise"));
        assertFalse(mod.contains("enthusiastaff.remove.full-overturn"));

        assertTrue(admin.containsAll(Set.of(
                "enthusiastaff.punish.custom-duration",
                "enthusiastaff.remove.raise",
                "enthusiastaff.remove.custom-duration",
                "enthusiastaff.remove.full-overturn",
                "enthusiastaff.remove.approve-overturn",
                "enthusiastaff.market.restrict",
                "enthusiastaff.reputation.restrict"
        )));
        assertFalse(admin.contains("enthusiastaff.punish.custom-combination"));
        assertFalse(admin.contains("enthusiastaff.case.restoreitems"));

        assertTrue(founder.containsAll(MUTATION_PERMISSIONS));
    }

    private static JsonNode permissions() throws IOException {
        try (InputStream input = PermissionPolicyConfigurationTest.class
                .getClassLoader()
                .getResourceAsStream("plugin.yml")) {
            if (input == null) {
                throw new IOException("plugin.yml is absent from the test classpath");
            }
            return new ObjectMapper(new YAMLFactory()).readTree(input).path("permissions");
        }
    }

    private static Set<String> effectiveChildren(
            JsonNode permissions,
            String permission,
            Set<String> visiting
    ) {
        if (!visiting.add(permission)) {
            throw new IllegalArgumentException("Permission inheritance cycle at " + permission);
        }
        Set<String> effective = new HashSet<>();
        JsonNode children = permissions.path(permission).path("children");
        children.properties().forEach(entry -> {
            if (!entry.getValue().asBoolean()) {
                return;
            }
            effective.add(entry.getKey());
            effective.addAll(effectiveChildren(permissions, entry.getKey(), visiting));
        });
        visiting.remove(permission);
        return Set.copyOf(effective);
    }
}
