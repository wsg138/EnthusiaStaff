package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import org.junit.jupiter.api.Test;

final class WebsiteApiActorTest {
    @Test
    void currentAuthorityOverridesTheSiteRoleClaim() {
        UUID actorId = UUID.randomUUID();
        WebsiteReviewerAuthority authority =
                new WebsiteReviewerAuthority(ignored -> Optional.of(StaffRank.MOD));

        Actor actor = authority.resolve(actorId, "FOUNDER");

        assertEquals(actorId, actor.id());
        assertEquals(StaffRank.MOD, actor.rank());
    }

    @Test
    void permanentIdentityPrecedesLegacyRankBundles() {
        Set<String> permissions = Set.of(
                "enthusiastaff.identity.developer",
                "enthusiastaff.rank.admin"
        );

        assertEquals(
                Optional.of(StaffRank.DEVELOPER),
                WebsiteReviewerAuthority.resolveRank(permissions::contains)
        );
    }

    @Test
    void rejectsUnknownSiteRoleAndMissingCurrentAuthority() {
        UUID actorId = UUID.randomUUID();
        WebsiteReviewerAuthority currentMod =
                new WebsiteReviewerAuthority(ignored -> Optional.of(StaffRank.MOD));
        WebsiteReviewerAuthority removed =
                new WebsiteReviewerAuthority(ignored -> Optional.empty());

        assertThrows(WebsiteApiException.class, () -> currentMod.resolve(actorId, "SYSTEM"));
        assertThrows(WebsiteApiException.class, () -> currentMod.resolve(actorId, "MODERATOR"));
        assertThrows(WebsiteApiException.class, () -> removed.resolve(actorId, "MOD"));
    }

    @Test
    void lookupFailureFailsClosedAsServiceUnavailable() {
        WebsiteReviewerAuthority unavailable = new WebsiteReviewerAuthority(ignored -> {
            throw new IllegalStateException("provider unavailable");
        });

        WebsiteApiException error = assertThrows(
                WebsiteApiException.class,
                () -> unavailable.resolve(UUID.randomUUID(), "ADMIN")
        );

        assertEquals(503, error.status());
        assertEquals("REVIEW_AUTHORITY_UNAVAILABLE", error.code());
    }
}
