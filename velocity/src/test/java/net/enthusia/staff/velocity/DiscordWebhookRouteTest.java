package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DiscordWebhookRouteTest {
    @Test
    void stagingRequiresExactApprovedHttpsHost() {
        DiscordWebhookRoute route = DiscordWebhookRoute.approvedStaging(
                "reports",
                URI.create("https://discord-staging.example.test/webhook/reports"),
                Set.of("discord-staging.example.test")
        );

        assertEquals(DiscordRouteEnvironment.STAGING, route.environment());
        assertEquals("discord-staging.example.test", route.endpoint().getHost());

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        "reports",
                        URI.create("https://unapproved.example.test/webhook/reports"),
                        Set.of("discord-staging.example.test")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        "reports",
                        URI.create("http://discord-staging.example.test/webhook/reports"),
                        Set.of("discord-staging.example.test")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        "reports",
                        URI.create("https://discord.com/api/webhooks/123/token"),
                        Set.of("discord.com")
                )
        );
    }

    @Test
    void unsafeUriComponentsAreRejected() {
        Set<String> hosts = Set.of("discord-staging.example.test");

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        "reports",
                        URI.create("https://user@discord-staging.example.test/webhook/reports"),
                        hosts
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        "reports",
                        URI.create("https://discord-staging.example.test/webhook/reports?secret=1"),
                        hosts
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        "reports",
                        URI.create("https://discord-staging.example.test/webhook/reports#fragment"),
                        hosts
                )
        );
    }

    @Test
    void productionAcceptsOnlyDiscordWebhookEndpoints() {
        DiscordWebhookRoute route = DiscordWebhookRoute.approvedProduction(
                "punishments",
                URI.create("https://discord.com/api/webhooks/123/token")
        );
        assertEquals(DiscordRouteEnvironment.PRODUCTION, route.environment());

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedProduction(
                        "punishments",
                        URI.create("https://example.test/api/webhooks/123/token")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedProduction(
                        "punishments",
                        URI.create("https://discord.com/channels/123")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedProduction(
                        "punishments",
                        URI.create("https://discord.com:8443/api/webhooks/123/token")
                )
        );
    }

    @Test
    void routeEnvironmentParsingIsStrict() {
        assertEquals(DiscordRouteEnvironment.STAGING, DiscordRouteEnvironment.parse("staging"));
        assertEquals(DiscordRouteEnvironment.PRODUCTION, DiscordRouteEnvironment.parse("PRODUCTION"));
        assertThrows(IllegalArgumentException.class, () -> DiscordRouteEnvironment.parse("preview"));
        assertThrows(IllegalArgumentException.class, () -> DiscordRouteEnvironment.parse(" "));
    }
}
