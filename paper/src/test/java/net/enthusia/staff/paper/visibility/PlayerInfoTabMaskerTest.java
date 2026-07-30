package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

class PlayerInfoTabMaskerTest {
    private static final UUID VIEWER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void visibleStaffSpectatorIsPresentedAsCreativeWithoutLosingTabFields() {
        PlayerInfoData source = entry(true, EnumWrappers.NativeGameMode.SPECTATOR);
        PlayerInfoTabMasker masker = new PlayerInfoTabMasker(
                (viewer, target) -> true,
                target -> StaffRank.DEVELOPER,
                target -> false
        );

        PlayerInfoData masked = masker.rewrite(VIEWER_ID, List.of(source)).getFirst();

        assertEquals(TARGET_ID, masked.getProfileId());
        assertEquals(47, masked.getLatency());
        assertTrue(masked.isListed());
        assertEquals(EnumWrappers.NativeGameMode.CREATIVE, masked.getGameMode());
        assertTrue(masked.isShowHat());
        assertEquals(9, masked.getListOrder());
        assertEquals(source.getProfile(), masked.getProfile());
        assertEquals(source.getDisplayName(), masked.getDisplayName());
        assertEquals(source.getRemoteChatSessionData(), masked.getRemoteChatSessionData());
    }

    @Test
    void hiddenSpectatorCannotBeRelistedByAPlayerInfoUpdate() {
        PlayerInfoTabMasker masker = new PlayerInfoTabMasker(
                (viewer, target) -> true,
                target -> StaffRank.ADMIN,
                target -> true
        );

        PlayerInfoData masked = masker.rewrite(
                VIEWER_ID,
                List.of(entry(true, EnumWrappers.NativeGameMode.SPECTATOR))
        ).getFirst();

        assertFalse(masked.isListed());
        assertEquals(EnumWrappers.NativeGameMode.CREATIVE, masked.getGameMode());
    }

    @Test
    void unauthorizedVanishedEntryIsRemovedEntirely() {
        PlayerInfoTabMasker masker = new PlayerInfoTabMasker(
                (viewer, target) -> false,
                target -> StaffRank.FOUNDER,
                target -> false
        );

        assertTrue(masker.rewrite(
                VIEWER_ID,
                List.of(entry(true, EnumWrappers.NativeGameMode.CREATIVE))
        ).isEmpty());
    }

    @Test
    void unrelatedNonStaffEntryIsNotReallocated() {
        List<PlayerInfoData> original = List.of(entry(true, EnumWrappers.NativeGameMode.SPECTATOR));
        PlayerInfoTabMasker masker = new PlayerInfoTabMasker(
                (viewer, target) -> true,
                target -> null,
                target -> false
        );

        assertSame(original, masker.rewrite(VIEWER_ID, original));
    }

    private static PlayerInfoData entry(boolean listed, EnumWrappers.NativeGameMode gameMode) {
        return new PlayerInfoData(
                TARGET_ID,
                47,
                listed,
                gameMode,
                null,
                null,
                true,
                9,
                null
        );
    }
}
