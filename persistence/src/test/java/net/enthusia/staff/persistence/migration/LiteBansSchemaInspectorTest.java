package net.enthusia.staff.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LiteBansSchemaInspectorTest {
    private static final String BAN_STAFF = "banned_by_name";
    private static final String MUTE_STAFF = "muted_by_name";
    private static final String STAFF_COLUMN = "staff";

    @Test
    void resolvesKnownAliasesWithoutInventingMissingColumns() {
        Map<String, String> mapping = LiteBansSchemaInspector.resolveColumns(Set.of(
                "id", "uuid", "name", "reason", BAN_STAFF, "time", "until", "active",
                "removed_by_date"
        ), "bans");

        assertEquals("name", mapping.get("username"));
        assertEquals(BAN_STAFF, mapping.get(STAFF_COLUMN));
        assertEquals("removed_by_date", mapping.get("ended_at"));
        assertFalse(mapping.containsKey("ip_ban"));
    }

    @Test
    void givesTheCurrentSanctionTypeStaffAliasPrecedence() {
        Set<String> columns = Set.of(BAN_STAFF, MUTE_STAFF, "staff_name");

        assertEquals(
                BAN_STAFF,
                LiteBansSchemaInspector.resolveColumns(columns, "bans").get(STAFF_COLUMN)
        );
        assertEquals(
                MUTE_STAFF,
                LiteBansSchemaInspector.resolveColumns(columns, "mutes").get(STAFF_COLUMN)
        );
    }

    @Test
    void acceptsTheSharedLegacyStaffAliasWhenItIsTheOnlyChoice() {
        Map<String, String> mapping = LiteBansSchemaInspector.resolveColumns(
                Set.of(BAN_STAFF),
                "mutes"
        );

        assertEquals(BAN_STAFF, mapping.get(STAFF_COLUMN));
    }

    @Test
    void acceptsUuidOnlySanctionsWhenModernLiteBansOmitsPlayerNames() {
        Map<String, String> mapping = LiteBansSchemaInspector.resolveColumns(Set.of(
                "id", "uuid", "reason", BAN_STAFF, "time", "until", "active"
        ), "bans");

        assertEquals("uuid", mapping.get("uuid"));
        assertFalse(mapping.containsKey("username"));
    }
}
