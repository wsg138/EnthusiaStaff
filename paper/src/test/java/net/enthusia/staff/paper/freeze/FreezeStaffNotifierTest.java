package net.enthusia.staff.paper.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class FreezeStaffNotifierTest {
    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void freezeAlertNamesActorTargetReasonAndProvidesTeleportAction() {
        PlayerIdentity target = new PlayerIdentity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Optional.of("Target"),
                PlayerPlatform.JAVA,
                NOW,
                NOW
        );
        Actor actor = new Actor(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Moderator",
                StaffRank.MOD
        );

        Component message = FreezeStaffNotifier.render(target, actor, "screenshare", true);
        String plain = PlainTextComponentSerializer.plainText().serialize(message);

        assertTrue(plain.contains("Target frozen by Moderator"));
        assertTrue(plain.contains("screenshare"));
        Component teleport = message.children().getLast();
        assertEquals(ClickEvent.runCommand("/tp Target"), teleport.clickEvent());
    }

    @Test
    void offlineIdentityDoesNotOfferBrokenTeleportAction() {
        PlayerIdentity target = new PlayerIdentity(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                Optional.empty(),
                PlayerPlatform.UNKNOWN,
                NOW,
                NOW
        );
        Actor actor = new Actor(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "Admin",
                StaffRank.ADMIN
        );

        Component message = FreezeStaffNotifier.render(target, actor, "release", false);

        assertTrue(message.children().stream().noneMatch(child -> child.clickEvent() != null));
    }
}
