package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.alt.AltRelationshipState;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.moderation.CurrentLinkedMinecraftAccount;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class AltRelationshipPresentationTest {
    private static final Instant CREATED = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    void separatesCurrentVerifiedLinksFromNetworkRelationships() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID linkedId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID networkId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        List<Component> rendered = AltRelationshipPresentation.render(
                player(targetId, "Target"),
                List.of(new CurrentLinkedMinecraftAccount(linkedId, Optional.of("VerifiedLink"), CREATED)),
                true,
                List.of(new AltRelationshipSummary(networkId, AltRelationshipState.CONFIDENT, 0.75, false, CREATED))
        );

        assertEquals(List.of(
                Component.text("Alt review for Target:"),
                Component.text("Verified linked Minecraft accounts: 1"),
                Component.text("- VerifiedLink (linked 2026-09-10T12:00:00Z)"),
                Component.text("Network relationships: 1"),
                Component.text("- " + networkId + " CONFIDENT confidence=75%")
        ), rendered);
    }

    @Test
    void makesAnUnavailableLinkProjectionDistinctFromNoLinkedAccounts() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000011");

        List<Component> rendered = AltRelationshipPresentation.render(
                player(targetId, "Target"),
                List.of(),
                false,
                List.of()
        );

        assertEquals(List.of(
                Component.text("Alt review for Target:"),
                Component.text("Verified linked Minecraft accounts: unavailable."),
                Component.text("Network relationships: 0")
        ), rendered);
    }

    private static PlayerIdentity player(UUID playerId, String username) {
        return new PlayerIdentity(playerId, Optional.of(username), PlayerPlatform.JAVA, CREATED, CREATED);
    }
}
