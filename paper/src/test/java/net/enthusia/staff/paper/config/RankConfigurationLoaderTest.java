package net.enthusia.staff.paper.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringReader;
import java.util.EnumSet;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class RankConfigurationLoaderTest {
    private static final String UNKNOWN = "unknown";
    private static final String CANNOT_INHERIT = "cannot inherit";
    private final RankConfigurationLoader loader = new RankConfigurationLoader();

    @Test
    void shippedCandidateMatchesTheAuditedRankDimension() {
        RankConfigurationSnapshot snapshot;
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ranks.yml")) {
            snapshot = loader.load(input);
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }

        assertEquals(1, snapshot.schemaVersion());
        assertEquals(EnumSet.allOf(StaffRank.class), snapshot.grants().keySet());
        for (StaffRank rank : StaffRank.values()) {
            boolean helper = rank == StaffRank.HELPER;
            boolean staff = rank != StaffRank.SYSTEM;
            boolean vanish = staff && !helper;
            boolean advanced = vanish;
            boolean inventoryEdit = vanish;
            boolean allModes = rank == StaffRank.DEVELOPER
                    || rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER;
            boolean enderOpen = allModes;
            boolean enderEdit = rank == StaffRank.DEVELOPER || rank == StaffRank.FOUNDER;

            assertEquals(staff, snapshot.proposes(rank, StaffCapability.STAFF_MODE_GAMEMODE_SURVIVAL));
            assertEquals(staff, snapshot.proposes(rank, StaffCapability.STAFF_MODE_GAMEMODE_SPECTATOR));
            assertEquals(allModes, snapshot.proposes(rank, StaffCapability.STAFF_MODE_GAMEMODE_CREATIVE));
            assertEquals(allModes, snapshot.proposes(rank, StaffCapability.STAFF_MODE_GAMEMODE_ADVENTURE));
            assertEquals(vanish, snapshot.proposes(rank, StaffCapability.VANISH));
            assertEquals(advanced, snapshot.proposes(rank, StaffCapability.STAFF_MODE_ADVANCED_TOOLS));
            assertEquals(inventoryEdit, snapshot.proposes(rank, StaffCapability.STAFF_MODE_INVENTORY_EDIT));
            assertEquals(enderOpen, snapshot.proposes(rank, StaffCapability.STAFF_MODE_ENDER_CHEST_OPEN));
            assertEquals(enderEdit, snapshot.proposes(rank, StaffCapability.STAFF_MODE_ENDER_CHEST_EDIT));
            assertEquals(rank == StaffRank.DEVELOPER,
                    snapshot.proposes(rank, StaffCapability.STAFF_MODE_COMBAT_TEST));
        }
        assertTrue(snapshot.effectiveCapabilities(StaffRank.SYSTEM).isEmpty());
        assertTrue(snapshot.effectiveCapabilities(null).isEmpty());
        assertFalse(snapshot.proposes(null, StaffCapability.VANISH));
        assertFalse(snapshot.proposes(StaffRank.MOD, null));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.grants().put(StaffRank.MOD, EnumSet.noneOf(StaffCapability.class)));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.grants().get(StaffRank.MOD).clear());
    }

    @Test
    void canModifyPreviewGrantsWithoutMutatingAnyRuntimeAuthority() {
        RankConfigurationSnapshot candidate = parse(valid().replace(
                "      - VANISH\n  DEVELOPER:",
                "  DEVELOPER:"
        ));
        assertFalse(candidate.proposes(StaffRank.MOD, StaffCapability.VANISH));
        assertFalse(candidate.proposes(StaffRank.ADMIN, StaffCapability.VANISH));
        assertTrue(candidate.proposes(StaffRank.DEVELOPER, StaffCapability.VANISH));
    }

    @Test
    void unknownOrMissingFieldFailsClosed() {
        assertAll(() -> {
            invalid(valid().replace("schema-version: 1", "schema-version: 999"), "schema-version");
            invalid(valid().replace("schema-version: 1", "schema-version: true"), "schema-version");
            invalid(valid().replace("ranks:", "rankz:"), "missing");
            invalid(valid().replace("  SYSTEM:", "  SERVICE:"), "SYSTEM");
            invalid(valid().replace("    inherits: []", "    parents: []"), UNKNOWN);
            invalid(valid().replace("STAFF_MODE_COMBAT_TEST", "SUPER_ADMIN_BYPASS"), UNKNOWN);
            invalid(valid().replace("  SYSTEM:\n", "  SYSTEM:\n    unknown: true\n"), UNKNOWN);
        });
    }

    @Test
    void rejectsDuplicateYamlAndArrayMembers() {
        assertAll(() -> {
            invalid(valid().replace("schema-version: 1", "schema-version: 1\nschema-version: 1"),
                    "parse");
            invalid(valid().replace("    inherits: [HELPER]", "    inherits: [HELPER, HELPER]"),
                    "duplicate");
            invalid(valid().replace("      - VANISH\n  DEVELOPER:",
                    "      - VANISH\n      - VANISH\n  DEVELOPER:"), "duplicate");
        });
    }

    @Test
    void rejectsUnsafeCrossBranchInheritanceAndSystemGrants() {
        assertAll(() -> {
            invalid(valid().replace("  MOD:\n    inherits: [HELPER]",
                    "  MOD:\n    inherits: [DEVELOPER]"), CANNOT_INHERIT);
            invalid(valid().replace("  HELPER:\n    inherits: []",
                    "  HELPER:\n    inherits: [ADMIN]"), CANNOT_INHERIT);
            invalid(valid().replace("  DEVELOPER:\n    inherits: []",
                    "  DEVELOPER:\n    inherits: [MOD]"), CANNOT_INHERIT);
            invalid(valid().replace("  SYSTEM:\n    inherits: []",
                    "  SYSTEM:\n    inherits: [HELPER]"), CANNOT_INHERIT);
            invalid(valid().replace("  SYSTEM:\n    inherits: []\n    grants: []",
                    "  SYSTEM:\n    inherits: []\n    grants: [VANISH]"), "SYSTEM");
        });
    }

    @Test
    void rejectsMalformedNonArraysAndCaseMismatches() {
        assertAll(() -> {
            invalid(valid().replace("    inherits: [HELPER]", "    inherits: HELPER"), "array");
            invalid(valid().replace("      - VANISH", "      - vanish"), UNKNOWN);
            invalid(valid().replace("    grants: []", "    grants: false"), "array");
        });
    }

    private String valid() {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ranks.yml")) {
            if (input == null) {
                throw new AssertionError("Missing ranks.yml");
            }
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private RankConfigurationSnapshot parse(String raw) {
        return loader.load(new StringReader(raw));
    }

    private void invalid(String raw, String message) {
        ConfigurationValidationException exception =
                assertThrows(ConfigurationValidationException.class, () -> parse(raw));
        assertTrue(exception.getMessage().toLowerCase(java.util.Locale.ROOT)
                .contains(message.toLowerCase(java.util.Locale.ROOT)), exception::getMessage);
    }
}
