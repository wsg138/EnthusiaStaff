package net.enthusia.staff.paper.sanction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class SanctionChangeAccessTypeScopeTest {
    @Test
    void shortcutFamiliesAreAlwaysNarrowedAndCentralCommandIsCaseWide() {
        assertEquals(Set.of(SanctionType.BAN, SanctionType.NETWORK_BAN,
                        SanctionType.NETWORK_IDENTITY_BAN),
                SanctionChangeAccess.typeScopeForCommand("unban").orElseThrow());
        assertEquals(Set.of(SanctionType.MUTE, SanctionType.PUBLIC_MUTE),
                SanctionChangeAccess.typeScopeForCommand("unmute").orElseThrow());
        assertEquals(Set.of(SanctionType.WARNING),
                SanctionChangeAccess.typeScopeForCommand("unwarn").orElseThrow());
        assertEquals(Set.of(SanctionType.WARNING),
                SanctionChangeAccess.typeScopeForCommand("removewarning").orElseThrow());
        assertTrue(SanctionChangeAccess.typeScopeForCommand("removepunishment").isEmpty());
    }

    @Test
    void unknownCommandCannotSilentlyBroadenScopeToAllSanctions() {
        assertThrows(IllegalArgumentException.class,
                () -> SanctionChangeAccess.typeScopeForCommand("unban-other"));
    }
}
