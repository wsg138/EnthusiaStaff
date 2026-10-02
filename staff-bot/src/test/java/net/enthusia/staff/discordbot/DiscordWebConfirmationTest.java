package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.*;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.moderation.*;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;
import net.enthusia.staff.domain.sanction.SanctionLength;
import org.junit.jupiter.api.Test;

class DiscordWebConfirmationTest {
    private static final String STAFF_ROLE = "staff";

    @Test
    void repeatConfirmationAndLostResponseProduceOneDurableIntentAndNoDirectEffects() {
        Fixture fixture = new Fixture();
        UUID token = fixture.service.prepareIssue(123,STAFF_ROLE,999,warning()).token();
        var first = fixture.service.confirmWebIssue(123,STAFF_ROLE,999,token);
        var replay = fixture.service.confirmWebIssue(123,STAFF_ROLE,999,token);
        assertEquals(token,first.punishmentId());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(1,fixture.writes.get());
        assertEquals(1,fixture.stored.size());
        var history = ModerationReadSnapshotMapper.discordHistory(fixture.stored.get(token).punishment());
        assertEquals("discord:" + token, history.stableKey());
        assertTrue(history.caseId().isEmpty());
        assertEquals(Optional.of("Discord WARNING"), history.punishmentType());
        assertEquals("PENDING_APPLY · Notification: NOT_ATTEMPTED", history.status());
        assertTrue(history.sanctionFamily().isEmpty());
        assertThrows(IllegalArgumentException.class,() -> fixture.service.confirmWebIssue(123,STAFF_ROLE,888,token));
        assertEquals(1,fixture.writes.get());
    }

    @Test
    void authorityLossBetweenPreparationAndConfirmationPreventsDurableIntent() {
        Fixture fixture = new Fixture();
        UUID token = fixture.service.prepareIssue(123,STAFF_ROLE,999,warning()).token();
        fixture.rank.set(Optional.empty());
        assertThrows(LinkedStaffActorResolver.MissingStaffLinkException.class,
                () -> fixture.service.confirmWebIssue(123,STAFF_ROLE,999,token));
        assertEquals(0,fixture.writes.get());
    }

    private static DiscordPunishmentIntent warning() {
        return new DiscordPunishmentIntent(DiscordConsequenceType.WARNING,SanctionLength.instant(),false,false,
                Optional.empty(),"Authorized test","Test explanation",0,true);
    }

    private static final class Fixture {
        private final Map<UUID,StoredPunishment> stored = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicReference<Optional<StaffRank>> rank = new AtomicReference<>(Optional.of(StaffRank.ADMIN));
        private final DiscordPunishmentService service;

        Fixture() {
            UUID player = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            var subject = new VersionedSubject(new ModerationSubject(new ModerationSubjectId(UUID.randomUUID()),
                    Set.of(new MinecraftIdentityRef(player),new DiscordIdentityRef(new DiscordUserId("123"))),
                    Optional.of(new MainMinecraftAccount(player,MainAccountSelectionSource.AUTOMATIC))),0);
            Clock clock = Clock.fixed(Instant.parse("2026-09-30T00:00:00Z"),ZoneOffset.UTC);
            var reads = new StaffModerationReadService(proxy(StaffModerationReadService.ReadData.class,(method,args) -> {
                if (method.equals("subjectForDiscord")) return ((DiscordUserId)args[0]).value().equals("123") ? Optional.of(subject) : Optional.empty();
                throw new AssertionError("Unexpected read: " + method);
            }),clock);
            var actors = new LinkedStaffActorResolver(reads,ignored -> rank.get());
            var repository = proxy(DiscordPunishmentRepository.class,(method,args) -> {
                if (method.equals("find")) return Optional.ofNullable(stored.get((UUID)args[0]));
                if (method.equals("create")) {
                    var punishment = (DiscordPunishment)args[0];
                    writes.incrementAndGet();
                    var result = new StoredPunishment(punishment,0,false);
                    stored.put(punishment.punishmentId(),result);
                    return result;
                }
                throw new AssertionError("Unexpected repository operation: " + method);
            });
            var gateway = proxy(DiscordPunishmentGateway.class,(method,args) -> {
                if (method.equals("preflight")) return null;
                throw new AssertionError("Confirmation must not execute external effect: " + method);
            });
            var dependencies = new DiscordPunishmentService.Dependencies(reads,actors,
                    new DiscordPunishmentAuthorization(new DiscordAuthorizationLimits(Duration.ofHours(1),Duration.ofDays(1),Duration.ofDays(7),Duration.ofDays(7))),
                    new DiscordPunishmentConfirmationStore(clock,Duration.ofMinutes(2),10),repository,
                    (user,time) -> new ModerationSubjectId(UUID.randomUUID()));
            service = new DiscordPunishmentService(dependencies,gateway,new DiscordGuildId("456"),clock);
        }
    }

    @FunctionalInterface
    private interface Stub {
        Object invoke(String method,Object[] args);
    }

    private static <T> T proxy(Class<T> type,Stub stub) {
        return type.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),new Class<?>[]{type},
                (instance,method,args) -> stub.invoke(method.getName(),args)));
    }
}
