package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class StaffToolSessionPolicyTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOKEN = "session-token";

    @Test
    void exactOwnerSessionSlotMaterialAndRankAreRequired() {
        StaffToolDefinition tool = StaffToolDefinition.PLAYER_INSPECTOR;
        assertEquals(
                StaffToolSessionPolicy.Status.VALID,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        tool.slot(),
                        tool,
                        tool.material(),
                        PLAYER_ID.toString(),
                        TOKEN,
                        StaffRank.MOD
                )
        );
    }

    @Test
    void staleOrSpoofedSessionCannotAuthorizeTool() {
        StaffToolDefinition tool = StaffToolDefinition.FREEZE;
        assertEquals(
                StaffToolSessionPolicy.Status.STALE_SESSION,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        null,
                        tool.slot(),
                        tool,
                        tool.material(),
                        PLAYER_ID.toString(),
                        TOKEN,
                        StaffRank.MOD
                )
        );
        assertEquals(
                StaffToolSessionPolicy.Status.OWNER_MISMATCH,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        tool.slot(),
                        tool,
                        tool.material(),
                        UUID.randomUUID().toString(),
                        TOKEN,
                        StaffRank.MOD
                )
        );
        assertEquals(
                StaffToolSessionPolicy.Status.SESSION_MISMATCH,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        tool.slot(),
                        tool,
                        tool.material(),
                        PLAYER_ID.toString(),
                        "old-session",
                        StaffRank.MOD
                )
        );
    }

    @Test
    void copiedOrMutatedToolOutsideCanonicalSlotAndMaterialFailsClosed() {
        StaffToolDefinition tool = StaffToolDefinition.REPORTS;
        assertEquals(
                StaffToolSessionPolicy.Status.SLOT_MISMATCH,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        tool.slot() + 1,
                        tool,
                        tool.material(),
                        PLAYER_ID.toString(),
                        TOKEN,
                        StaffRank.ADMIN
                )
        );
        assertEquals(
                StaffToolSessionPolicy.Status.MATERIAL_MISMATCH,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        tool.slot(),
                        tool,
                        Material.STONE,
                        PLAYER_ID.toString(),
                        TOKEN,
                        StaffRank.ADMIN
                )
        );
    }

    @Test
    void rankLossAndExcludedAdvancedToolFailClosed() {
        StaffToolDefinition menu = StaffToolDefinition.STAFF_TOOLS;
        assertEquals(
                StaffToolSessionPolicy.Status.RANK_UNAVAILABLE,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        menu.slot(),
                        menu,
                        menu.material(),
                        PLAYER_ID.toString(),
                        TOKEN,
                        StaffRank.SYSTEM
                )
        );

        StaffToolDefinition cheatTester = StaffToolDefinition.CHEAT_TESTER;
        assertEquals(
                StaffToolSessionPolicy.Status.RANK_UNAVAILABLE,
                StaffToolSessionPolicy.validate(
                        PLAYER_ID,
                        TOKEN,
                        cheatTester.slot(),
                        cheatTester,
                        cheatTester.material(),
                        PLAYER_ID.toString(),
                        TOKEN,
                        StaffRank.HELPER
                )
        );
    }
}
