package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.Headers;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.website.WebsiteAccountPunishment;
import net.enthusia.staff.domain.website.WebsiteAccountPunishmentPage;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;
import net.enthusia.staff.domain.website.WebsiteAppealView;
import org.junit.jupiter.api.Test;

class WebsiteLinkedAppealRouterTest {
    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final UUID PLAYER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PUNISHMENT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ACCOUNT = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final CaseId CASE = new CaseId("0000000000000001");
    private static final String DISCORD = "333333333333333333";

    @Test
    void ownedListDefaultsActiveAndIncludesBoundedPublicMetadata() {
        var store = new Store();
        var response = request(router(store), "/v1/website/appeals/linked", "{\"discordUserId\":\"" + DISCORD + "\"}");
        assertEquals("ACTIVE", store.scope);
        assertEquals(Optional.empty(), store.player);
        var items = assertInstanceOf(List.class, response.get("punishments"));
        var item = assertInstanceOf(Map.class, items.getFirst());
        assertEquals("*Bedrock Player", item.get("minecraftName"));
        assertEquals(false, item.get("active"));
        assertEquals(false, item.get("appealable"));
        assertFalse(item.containsKey("internalExplanation"));
        assertEquals(null, response.get("nextCursor"));
    }

    @Test
    void unknownFieldsAndInvalidScopesCannotReachOwnershipStore() {
        var store = new Store();
        var router = router(store);
        assertThrows(WebsiteApiException.class, () -> request(router, "/v1/website/appeals/linked", "{\"discordUserId\":\"" + DISCORD + "\",\"scope\":\"PRIVATE\"}"));
        assertThrows(WebsiteApiException.class, () -> request(router, "/v1/website/appeals/linked/submit", submission().replace("}", ",\"username\":\"Spoofed\"}")));
        assertEquals(0, store.calls);
    }

    @Test
    void submissionUsesOwnedUuidAndDoesNotRequirePunishmentCodeOrUsername() {
        var store = new Store();
        var response = request(router(store), "/v1/website/appeals/linked/submit", submission());
        assertEquals(DISCORD, store.discord);
        assertEquals(Optional.of(PLAYER), store.player);
        assertEquals(PUNISHMENT.toString(), response.get("punishmentId"));
        assertEquals("*Bedrock Player", response.get("player"));
    }

    private static String submission() {
        return "{\"discordUserId\":\"%s\",\"minecraftUuid\":\"%s\",\"punishmentId\":\"%s\",\"accountId\":\"%s\",\"reason\":\"Please review the full context of this punishment.\",\"idempotencyKey\":\"linked-test-1\"}".formatted(DISCORD, PLAYER, PUNISHMENT, ACCOUNT);
    }

    private static Map<?, ?> request(WebsiteApiRouter router, String path, String body) {
        var headers = new Headers(); headers.set("content-type", "application/json");
        return assertInstanceOf(Map.class, router.route("POST", URI.create(path), headers, body.getBytes(StandardCharsets.UTF_8)));
    }

    private static WebsiteApiRouter router(Store store) {
        AuthorizationPolicy policy = (actor, action) -> true;
        return new WebsiteApiRouter(store, policy,
                new SanctionChangeService(policy, request -> new net.enthusia.staff.domain.sanction.SanctionChangeResult.Applied(1, false)),
                () -> OperationalMode.ACTIVE, Clock.fixed(NOW, ZoneOffset.UTC),
                new WebsiteReviewerAuthority(ignored -> Optional.empty()));
    }

    private static final class Store extends WebsiteModerationStoreStub {
        private int calls;
        private String discord;
        private String scope;
        private Optional<UUID> player;
        @Override
        public WebsiteAccountPunishmentPage linkedPunishments(String discordId, Optional<UUID> minecraftId, String requestedScope, Optional<String> cursor, int limit, Instant now) {
            calls++; discord = discordId; scope = requestedScope; player = minecraftId;
            assertEquals(100, limit); assertEquals(NOW, now);
            return new WebsiteAccountPunishmentPage(List.of(new WebsiteAccountPunishment(PUNISHMENT, CASE, "WARN", "Public reason", NOW, PLAYER, "*Bedrock Player", false, NOW, NOW.plusSeconds(1209600), false)), Optional.empty());
        }
        @Override
        public WebsiteAppealSubmission submitLinkedAppeal(String discordId, UUID minecraftId, UUID punishmentId, String accountId, String reason, String key, Instant now) {
            calls++; discord = discordId; player = Optional.of(minecraftId);
            assertEquals(ACCOUNT.toString(), accountId);
            return new WebsiteAppealSubmission(new WebsiteAppealView(UUID.randomUUID(), punishmentId, CASE, "WARN", "*Bedrock Player", reason, "OPEN", 1, null, null, now, now), false);
        }
    }
}
