package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.ReconciliationState;
import org.junit.jupiter.api.Test;

final class ManagedRoleShadowServiceTest {
    private static final String SYNTHETIC_DISCORD_A = "synthetic-discord-a";
    private static final String VALID_JSON = """
            {
              "version": 1,
              "namespace": "playtime-numerals",
              "localKey": "tier:xii",
              "displayName": "Playtime XII",
              "existingDiscordRoleId": "1552390213500928122",
              "desiredMinecraftAccounts": [],
              "delete": false
            }
            """;

    @Test
    void malformedClaimDoesNotAbortValidShadowClaims() {
        ReconciliationState valid = state("managed-role:valid", "resource-valid", VALID_JSON);
        ReconciliationState invalid = state("managed-role:invalid", "sensitive-resource-name", "{");

        ManagedRoleShadowService.Snapshot snapshot = ManagedRoleShadowService.decodeStates(
                List.of(valid, invalid),
                10,
                new ObjectMapper()
        );

        assertEquals(1, snapshot.claims().size());
        assertEquals(1, snapshot.invalidResourceRefs().size());
        assertFalse(snapshot.invalidResourceRefs().getFirst().contains("sensitive"));
        assertTrue(snapshot.incomplete());
        assertFalse(snapshot.truncated());
    }

    @Test
    void claimLimitMarksSnapshotIncompleteWithoutDecodingOverflowRow() {
        ReconciliationState valid = state("managed-role:valid", "resource-valid", VALID_JSON);
        ReconciliationState overflow = state("managed-role:overflow", "resource-overflow", "{");

        ManagedRoleShadowService.Snapshot snapshot = ManagedRoleShadowService.decodeStates(
                List.of(valid, overflow),
                1,
                new ObjectMapper()
        );

        assertEquals(1, snapshot.claims().size());
        assertEquals(0, snapshot.invalidResourceRefs().size());
        assertTrue(snapshot.truncated());
        assertTrue(snapshot.incomplete());
    }

    @Test
    void twoMinecraftAccountsLinkedToOneDiscordUserProduceOneRoleMember() {
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var result = ManagedRoleShadowService.resolveClaim(
                claim(Set.of(first, second)), ignored -> Set.of(SYNTHETIC_DISCORD_A));

        assertEquals(Set.of(SYNTHETIC_DISCORD_A), result.desiredDiscordUserIds());
        assertEquals(0, result.unlinkedMinecraftAccounts());
    }

    @Test
    void unlinkedAccountKeepsOtherLinkedIdentitiesInDesiredSet() {
        UUID first = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID second = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID third = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Map<UUID, Set<String>> currentLinks = Map.of(
                first, Set.of(SYNTHETIC_DISCORD_A),
                second, Set.of(),
                third, Set.of(SYNTHETIC_DISCORD_A, "synthetic-discord-b"));
        var result = ManagedRoleShadowService.resolveClaim(
                claim(Set.of(first, second, third)), currentLinks::get);

        assertEquals(Set.of(SYNTHETIC_DISCORD_A, "synthetic-discord-b"),
                result.desiredDiscordUserIds());
        assertEquals(1, result.unlinkedMinecraftAccounts());
    }

    private static ManagedRoleShadowService.Claim claim(Set<UUID> minecraftIds) {
        return new ManagedRoleShadowService.Claim(
                "managed-role:synthetic", "synthetic-resource", "playtime-numerals",
                "tier:xii", "Playtime XII", Optional.empty(), minecraftIds, false);
    }

    private static ReconciliationState state(String key, String resourceId, String desiredJson) {
        return new ReconciliationState(
                key,
                "MANAGED_ROLE",
                resourceId,
                desiredJson,
                Optional.empty(),
                "PENDING",
                0,
                Optional.empty(),
                Optional.empty(),
                0
        );
    }
}
