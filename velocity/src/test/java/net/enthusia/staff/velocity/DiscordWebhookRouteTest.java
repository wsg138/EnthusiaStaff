package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DiscordWebhookRouteTest {
    private static final String REPORTS = "reports";
    private static final String PUNISHMENTS = "punishments";
    private static final String STAGING_HOST = "discord-staging.example.test";
    private static final Set<String> STAGING_HOSTS = Set.of(STAGING_HOST);

    @Test
    void stagingRequiresExactApprovedHttpsHost() {
        DiscordWebhookRoute route = DiscordWebhookRoute.approvedStaging(
                REPORTS,
                URI.create("https://" + STAGING_HOST + "/webhook/reports"),
                STAGING_HOSTS
        );

        assertEquals(DiscordRouteEnvironment.STAGING, route.environment());
        assertEquals(STAGING_HOST, route.endpoint().getHost());

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(REPORTS, null, STAGING_HOSTS)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("https://unapproved.example.test/webhook/reports"),
                        STAGING_HOSTS
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("http://" + STAGING_HOST + "/webhook/reports"),
                        STAGING_HOSTS
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("https://discord.com/api/webhooks/123/token"),
                        Set.of("discord.com")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("https://canary.discord.com/api/webhooks/123/token"),
                        Set.of("canary.discord.com")
                )
        );
    }

    @Test
    void unsafeUriComponentsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("https://user@" + STAGING_HOST + "/webhook/reports"),
                        STAGING_HOSTS
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("https://" + STAGING_HOST + "/webhook/reports?secret=1"),
                        STAGING_HOSTS
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedStaging(
                        REPORTS,
                        URI.create("https://" + STAGING_HOST + "/webhook/reports#fragment"),
                        STAGING_HOSTS
                )
        );
    }

    @Test
    void productionAcceptsOnlyDiscordWebhookEndpoints() {
        DiscordWebhookRoute route = DiscordWebhookRoute.approvedProduction(
                PUNISHMENTS,
                URI.create("https://discord.com/api/webhooks/123/token")
        );
        assertEquals(DiscordRouteEnvironment.PRODUCTION, route.environment());

        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedProduction(
                        PUNISHMENTS,
                        URI.create("https://example.test/api/webhooks/123/token")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedProduction(
                        PUNISHMENTS,
                        URI.create("https://discord.com/channels/123")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiscordWebhookRoute.approvedProduction(
                        PUNISHMENTS,
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
