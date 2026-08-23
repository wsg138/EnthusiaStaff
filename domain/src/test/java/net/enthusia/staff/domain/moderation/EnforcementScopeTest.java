package net.enthusia.staff.domain.moderation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EnforcementScopeTest {
    @Test
    void crossPlatformSelectionIsExplicitSetOfIndependentScopes() {
        ScopeSelection selection = ScopeSelection.of(
                new DiscordGuildScope(new DiscordGuildId("1410303324745371709")),
                new MinecraftNetworkScope()
        );

        assertTrue(selection.includesPlatform(ModerationPlatform.DISCORD));
        assertTrue(selection.includesPlatform(ModerationPlatform.MINECRAFT));
        assertTrue(selection.crossPlatform());
    }

    @Test
    void aMinecraftOnlySelectionIsNotCrossPlatform() {
        ScopeSelection selection = new ScopeSelection(Set.of(
                new MinecraftServerScope("smp"),
                new MinecraftNetworkScope()
        ));

        assertFalse(selection.crossPlatform());
    }

    @Test
    void emptyScopeSelectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScopeSelection(Set.of()));
    }
}
