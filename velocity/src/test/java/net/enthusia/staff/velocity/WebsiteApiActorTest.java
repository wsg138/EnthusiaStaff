package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

final class WebsiteApiActorTest {
    @Test
    void mapsEverySupportedWebsiteRankWithoutEscalatingDeveloper() {
        UUID actorId = UUID.randomUUID();

        assertEquals(StaffRank.MOD, actor(actorId, "MOD").rank());
        assertEquals(StaffRank.DEVELOPER, actor(actorId, "DEVELOPER").rank());
        assertEquals(StaffRank.ADMIN, actor(actorId, "ADMIN").rank());
        assertEquals(StaffRank.FOUNDER, actor(actorId, "FOUNDER").rank());
        assertEquals(actorId, actor(actorId, "DEVELOPER").id());
    }

    @Test
    void rejectsServiceAndUnmappedRoleNames() {
        UUID actorId = UUID.randomUUID();

        assertThrows(WebsiteApiException.class, () -> actor(actorId, "SYSTEM"));
        assertThrows(WebsiteApiException.class, () -> actor(actorId, "MODERATOR"));
        assertThrows(WebsiteApiException.class, () -> actor(actorId, ""));
    }

    private static Actor actor(UUID actorId, String rank) {
        return WebsiteApiServer.websiteActor(actorId, rank);
    }
}
