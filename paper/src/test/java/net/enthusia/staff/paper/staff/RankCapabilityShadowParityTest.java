package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.config.RankCapabilityShadowAudit;
import net.enthusia.staff.paper.config.RankConfigurationLoader;
import net.enthusia.staff.paper.config.RankConfigurationSnapshot;
import net.enthusia.staff.paper.config.StaffCapability;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankCapabilityShadowParityTest {
    @TempDir
    Path tempDirectory;

    @Test
    void shippedPlayerRankCandidateMatchesTheCurrentRankOnlyGates() {
        List<RankCapabilityShadowAudit.Mismatch> mismatches =
                RankCapabilityShadowAudit.comparePlayerRanks(shipped(), this::legacyRankOnly);

        assertTrue(mismatches.isEmpty(), () -> "Preview/legacy rank mismatches: " + mismatches);
    }

    @Test
    void intentionalRankCandidateChangeIsReportedWithoutGrantingAuthority() throws IOException {
        String text = shippedText();
        String altered = text.replace(
                "      - VANISH\n  DEVELOPER:",
                "  DEVELOPER:"
        );
        assertFalse(text.equals(altered), "Fixture must actually change the candidate");
        Path candidatePath = tempDirectory.resolve("ranks.yml");
        Files.writeString(candidatePath, altered, StandardCharsets.UTF_8);

        RankConfigurationSnapshot candidate = new RankConfigurationLoader().load(candidatePath);
        List<RankCapabilityShadowAudit.Mismatch> mismatches =
                RankCapabilityShadowAudit.comparePlayerRanks(candidate, this::legacyRankOnly);

        assertEquals(3, mismatches.size(), () -> "Expected Mod and its inherited Admin/Founder grants: " + mismatches);
        for (RankCapabilityShadowAudit.Mismatch mismatch : mismatches) {
            assertEquals(StaffCapability.VANISH, mismatch.capability());
            assertTrue(mismatch.legacyAllowed());
            assertFalse(mismatch.candidateAllowed());
        }
        assertEquals(List.of(StaffRank.MOD, StaffRank.ADMIN, StaffRank.FOUNDER),
                mismatches.stream().map(RankCapabilityShadowAudit.Mismatch::rank).toList());
        // The actual code-owned authorization is unchanged by a parsed candidate.
        assertTrue(StaffToolDefinition.VANISH.availableFor(StaffRank.MOD));
    }

    @Test
    void nonPlayerSystemRankAndUnresolvedIdentityAreNeverPreviewPlayerGrants() {
        RankConfigurationSnapshot candidate = shipped();
        assertTrue(candidate.effectiveCapabilities(StaffRank.SYSTEM).isEmpty());
        assertTrue(candidate.effectiveCapabilities(null).isEmpty());
        assertFalse(StaffToolDefinition.VANISH.availableFor(StaffRank.SYSTEM));
        assertFalse(StaffToolDefinition.VANISH.availableFor(null));
        // Legacy internal fallback exists, but not a player Staff Mode capability.
        assertTrue(StaffModeAccessPolicy.allowsGameMode(StaffRank.SYSTEM, GameMode.SPECTATOR));
        assertFalse(candidate.proposes(StaffRank.SYSTEM, StaffCapability.STAFF_MODE_GAMEMODE_SPECTATOR));
    }

    private boolean legacyRankOnly(StaffRank rank, StaffCapability capability) {
        GameMode mode = gameModeFor(capability);
        if (mode != null) {
            return StaffModeAccessPolicy.allowsGameMode(rank, mode);
        }
        return switch (capability) {
            case STAFF_MODE_ADVANCED_TOOLS -> StaffModeAccessPolicy.hasAdvancedStaffTools(rank);
            case STAFF_MODE_INVENTORY_EDIT -> !StaffModeAccessPolicy.blocksAllInventoryMutation(rank);
            case STAFF_MODE_ENDER_CHEST_OPEN -> !StaffModeAccessPolicy.blocksEnderChestOpen(rank);
            case STAFF_MODE_ENDER_CHEST_EDIT -> !StaffModeAccessPolicy.blocksEnderChestMutation(rank);
            case STAFF_MODE_COMBAT_TEST -> StaffModeAccessPolicy.allowsCombatTesting(rank);
            case VANISH -> StaffToolDefinition.VANISH.availableFor(rank);
            default -> throw new IllegalArgumentException("Not a non-game-mode capability " + capability);
        };
    }

    private static GameMode gameModeFor(StaffCapability capability) {
        return switch (capability) {
            case STAFF_MODE_GAMEMODE_SURVIVAL -> GameMode.SURVIVAL;
            case STAFF_MODE_GAMEMODE_SPECTATOR -> GameMode.SPECTATOR;
            case STAFF_MODE_GAMEMODE_CREATIVE -> GameMode.CREATIVE;
            case STAFF_MODE_GAMEMODE_ADVENTURE -> GameMode.ADVENTURE;
            default -> null;
        };
    }

    private RankConfigurationSnapshot shipped() {
        try (InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ranks.yml")) {
            return new RankConfigurationLoader().load(resource);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private String shippedText() throws IOException {
        try (InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ranks.yml")) {
            if (resource == null) {
                throw new IOException("Missing shipped ranks.yml");
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        }
    }
}
