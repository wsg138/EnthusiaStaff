package net.enthusia.staff.domain.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
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
    void enforcementTargetRequiresIdentityAndScopeOnSamePlatform() {
        EnforcementTarget target = new EnforcementTarget(
                new DiscordIdentityRef(new DiscordUserId("846729778400460871")),
                new DiscordGuildScope(new DiscordGuildId("1410303324745371709"))
        );

        assertEquals(ModerationPlatform.DISCORD, target.scope().platform());
        assertThrows(IllegalArgumentException.class, () -> new EnforcementTarget(
                new MinecraftIdentityRef(UUID.randomUUID()),
                new DiscordGuildScope(new DiscordGuildId("1410303324745371709"))
        ));
    }

    @Test
    void emptyScopeSelectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScopeSelection(Set.of()));
    }
}
