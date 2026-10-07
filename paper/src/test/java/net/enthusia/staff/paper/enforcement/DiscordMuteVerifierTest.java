package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class DiscordMuteVerifierTest {
    private static final UUID SENDER = new UUID(0, 42);

    @Test
    void authoritativeMuteAndPublicMuteAreRetained() {
        var publicMute = sanction(SanctionType.PUBLIC_MUTE);
        var mute = sanction(SanctionType.MUTE);
        assertEquals(MuteEnforcementListener.CachedMuteStatus.PUBLIC_MUTED,
                DiscordMuteVerifier.status(List.of(publicMute)));
        assertEquals(MuteEnforcementListener.CachedMuteStatus.MUTED,
                DiscordMuteVerifier.status(List.of(publicMute, mute)));
    }

    @Test
    void unavailableStorageRemainsBlocked() {
        assertBlocked(() -> null);
    }

    @Test
    void throwingStorageRemainsBlocked() {
        assertBlocked(() -> (id, types, now) -> { throw new IllegalStateException("unavailable"); });
    }

    private static void assertBlocked(Supplier<SanctionLookup> storage) {
        try (var verifier = new DiscordMuteVerifier(() -> OperationalMode.ACTIVE, storage,
                Runnable::run, Clock.systemUTC(), Logger.getAnonymousLogger(), Duration.ofSeconds(2), 2)) {
            assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED,
                    verifier.verify(SENDER).toCompletableFuture().join());
        }
    }

    private static ActiveSanction sanction(SanctionType type) {
        return new ActiveSanction(new UUID(0, 1), new CaseId("0000000000000001"), SENDER,
                type, "test", Clock.systemUTC().instant(), Optional.empty(), Optional.empty());
    }

    @Test
    void offlineSenderIsVerifiedWithoutJoinCacheOrCallerThreadLookup() {
        ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        try (DiscordMuteVerifier verifier = new DiscordMuteVerifier(
                () -> OperationalMode.ACTIVE, () -> (id, types, now) -> {
                    assertEquals(SENDER, id);
                    assertEquals(java.util.Set.of(SanctionType.MUTE, SanctionType.PUBLIC_MUTE), types);
                    return List.of();
                }, tasks::add, Clock.systemUTC(), Logger.getAnonymousLogger(), Duration.ofSeconds(2), 2)) {
            var result = verifier.verify(SENDER).toCompletableFuture();
            assertFalse(result.isDone());
            tasks.remove().run();
            assertEquals(MuteEnforcementListener.CachedMuteStatus.CLEAR, result.join());
        }
    }

    @Test
    void rejectionAndUnavailableStorageFailClosed() {
        try (DiscordMuteVerifier verifier = new DiscordMuteVerifier(
                () -> OperationalMode.ACTIVE, () -> null, action -> {
                    throw new RejectedExecutionException();
                }, Clock.systemUTC(), Logger.getAnonymousLogger(), Duration.ofSeconds(2), 2)) {
            assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED,
                    verifier.verify(SENDER).toCompletableFuture().join());
        }
    }

    @Test
    void timeoutDoesNotReleaseAdmissionUntilUnderlyingWorkActuallyFinishes() {
        ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        try (DiscordMuteVerifier verifier = new DiscordMuteVerifier(
                () -> OperationalMode.ACTIVE, () -> (id, types, now) -> List.of(), tasks::add,
                Clock.systemUTC(), Logger.getAnonymousLogger(), Duration.ofMillis(20), 1)) {
            var first = verifier.verify(SENDER).toCompletableFuture();
            assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED, first.join());
            assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED,
                    verifier.verify(SENDER).toCompletableFuture().join());
            assertEquals(1, tasks.size());
            tasks.remove().run();
            var next = verifier.verify(SENDER).toCompletableFuture();
            tasks.remove().run();
            assertEquals(MuteEnforcementListener.CachedMuteStatus.CLEAR, next.join());
            assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED, first.join());
        }
    }

    @Test
    void shutdownClosesPendingAndRejectsLateAllow() {
        ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        DiscordMuteVerifier verifier = new DiscordMuteVerifier(
                () -> OperationalMode.ACTIVE, () -> (id, types, now) -> List.of(), tasks::add,
                Clock.systemUTC(), Logger.getAnonymousLogger(), Duration.ofSeconds(2), 1);
        CompletableFuture<MuteEnforcementListener.CachedMuteStatus> pending =
                verifier.verify(SENDER).toCompletableFuture();
        verifier.close();
        tasks.remove().run();
        assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED, pending.join());
        assertEquals(MuteEnforcementListener.CachedMuteStatus.UNVERIFIED,
                verifier.verify(SENDER).toCompletableFuture().join());
    }
}
