package net.enthusia.staff.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LiteBansSchemaInspectorTest {
    @Test
    void resolvesKnownAliasesWithoutInventingMissingColumns() {
        Map<String, String> mapping = LiteBansSchemaInspector.resolveColumns(Set.of(
                "id", "uuid", "name", "reason", "banned_by_name", "time", "until", "active"
        ), "bans");

        assertEquals("name", mapping.get("username"));
        assertEquals("banned_by_name", mapping.get("staff"));
        assertFalse(mapping.containsKey("ip_ban"));
    }
}
