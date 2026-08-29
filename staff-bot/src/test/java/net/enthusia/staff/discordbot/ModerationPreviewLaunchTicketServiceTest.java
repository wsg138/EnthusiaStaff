package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ModerationPreviewLaunchTicketServiceTest {
    @Test
    void ticketBindsActorGuildAndTargetAndCanBeConsumedOnce() {
        var clock = new MutableClock(Instant.parse("2026-08-29T22:00:00Z"));
        var service = new ModerationPreviewLaunchTicketService(8, Duration.ofMinutes(2), clock, new SecureRandom());

        String token = service.issue(1234L, 5678L, "sample-river-ash");
        var accepted = service.consume(token);

        assertEquals(ModerationPreviewLaunchTicketService.Status.ACCEPTED, accepted.status());
        var claims = accepted.claims().orElseThrow();
        assertEquals(1234L, claims.actorId());
        assertEquals(5678L, claims.guildId());
        assertEquals("sample-river-ash", claims.targetKey());
        assertEquals(ModerationPreviewLaunchTicketService.Status.REPLAYED, service.consume(token).status());
    }

    @Test
    void systemClockPrecisionDoesNotInvalidateFreshTicket() {
        var clock = new MutableClock(Instant.parse("2026-08-29T22:00:00.987654321Z"));
        var service = new ModerationPreviewLaunchTicketService(8, Duration.ofMinutes(2), clock, new SecureRandom());

        String token = service.issue(9L, 10L, "sample-target");

        assertEquals(ModerationPreviewLaunchTicketService.Status.ACCEPTED, service.consume(token).status());
    }

    @Test
    void expiredMalformedAndTamperedTicketsAreRejected() {
        var clock = new MutableClock(Instant.parse("2026-08-29T22:00:00Z"));
        var service = new ModerationPreviewLaunchTicketService(8, Duration.ofSeconds(30), clock, new SecureRandom());
        String token = service.issue(1L, 2L, "sample-target");

        assertEquals(ModerationPreviewLaunchTicketService.Status.MALFORMED, service.consume("not-a-ticket").status());
        assertEquals(ModerationPreviewLaunchTicketService.Status.INVALID, service.consume(token + "x").status());

        clock.advance(Duration.ofSeconds(31));
        assertEquals(ModerationPreviewLaunchTicketService.Status.EXPIRED, service.consume(token).status());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            assertTrue(ZoneOffset.UTC.equals(zone));
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
