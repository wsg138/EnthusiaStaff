package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import org.junit.jupiter.api.Test;

class RetryingPunishmentRequestAlertStoreTest {
    private static final UUID RECIPIENT = UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_RECIPIENT = UUID.fromString("72000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-01T20:00:00Z");

    @Test
    void emptyIdlePollsUseAtMostOneFallbackPerRecipientAndAudienceInterval() {
        AtomicInteger fallbacks = new AtomicInteger();
        RetryingPunishmentRequestAlertStore store = new RetryingPunishmentRequestAlertStore(
                delegate(new AtomicReference<>(List.of())),
                Duration.ofSeconds(5),
                (audience, recipientId, rank, owner, limit, lease, now) -> {
                    fallbacks.incrementAndGet();
                    return List.of();
                }
        );

        claim(store, PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, RECIPIENT, NOW);
        claim(store, PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, RECIPIENT, NOW.plusSeconds(1));
        claim(store, PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, OTHER_RECIPIENT, NOW.plusSeconds(1));
        claim(store, PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS, RECIPIENT, NOW.plusSeconds(1));
        claim(store, PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, RECIPIENT, NOW.plusSeconds(5));

        assertEquals(4, fallbacks.get());
    }

    @Test
    void classifiedContentionFallbackCanAdvanceAnIndependentRecipient() {
        AtomicReference<UUID> fallbackRecipient = new AtomicReference<>();
        RetryingPunishmentRequestAlertStore store = new RetryingPunishmentRequestAlertStore(
                delegate(new AtomicReference<>(List.of())),
                Duration.ofSeconds(1),
                (audience, recipientId, rank, owner, limit, lease, now) -> {
                    fallbackRecipient.set(recipientId);
                    return Collections.singletonList(null);
                }
        );

        List<PunishmentRequestAlertClaim> claims = claim(
                store,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                OTHER_RECIPIENT,
                NOW
        );

        assertEquals(1, claims.size());
        assertEquals(OTHER_RECIPIENT, fallbackRecipient.get());
    }

    @Test
    void nonEmptyPrimaryResultNeverStartsFallback() {
        AtomicReference<List<PunishmentRequestAlertClaim>> primary =
                new AtomicReference<>(Collections.singletonList(null));
        AtomicInteger fallbacks = new AtomicInteger();
        RetryingPunishmentRequestAlertStore store = new RetryingPunishmentRequestAlertStore(
                delegate(primary),
                Duration.ofMinutes(1),
                (audience, recipientId, rank, owner, limit, lease, now) -> {
                    fallbacks.incrementAndGet();
                    return List.of();
                }
        );

        assertEquals(1, claim(
                store,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                RECIPIENT,
                NOW
        ).size());
        assertEquals(0, fallbacks.get());
    }

    @Test
    void successfulPrimaryClaimReopensTheFallbackGate() {
        AtomicReference<List<PunishmentRequestAlertClaim>> primary = new AtomicReference<>(List.of());
        AtomicInteger fallbacks = new AtomicInteger();
        RetryingPunishmentRequestAlertStore store = new RetryingPunishmentRequestAlertStore(
                delegate(primary),
                Duration.ofMinutes(1),
                (audience, recipientId, rank, owner, limit, lease, now) -> {
                    fallbacks.incrementAndGet();
                    return List.of();
                }
        );

        claim(store, PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, RECIPIENT, NOW);
        primary.set(Collections.singletonList(null));
        assertEquals(1, claim(
                store,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                RECIPIENT,
                NOW.plusSeconds(1)
        ).size());
        primary.set(List.of());
        claim(store, PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS, RECIPIENT, NOW.plusSeconds(2));

        assertEquals(2, fallbacks.get());
    }

    @Test
    void fallbackPersistenceFailureIsPropagatedWithoutReclassification() {
        ModerationPersistenceException failure = new ModerationPersistenceException("fallback failed");
        RetryingPunishmentRequestAlertStore store = new RetryingPunishmentRequestAlertStore(
                delegate(new AtomicReference<>(List.of())),
                Duration.ofSeconds(1),
                (audience, recipientId, rank, owner, limit, lease, now) -> { throw failure; }
        );

        ModerationPersistenceException thrown = assertThrows(
                ModerationPersistenceException.class,
                () -> claim(
                        store,
                        PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                        RECIPIENT,
                        NOW
                )
        );

        assertSame(failure, thrown);
    }

    @Test
    void recordChangedConflictRetriesPrimaryClaimBeforeFallback() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger fallbacks = new AtomicInteger();
        PunishmentRequestAlertStore delegate = (PunishmentRequestAlertStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{PunishmentRequestAlertStore.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("claimAudience")) {
                        if (attempts.getAndIncrement() == 0) {
                            throw new ModerationPersistenceException(
                                    "claim conflict",
                                    new SQLException(
                                            "Record has changed since last read",
                                            "HY000",
                                            1020
                                    )
                            );
                        }
                        return Collections.singletonList(null);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        RetryingPunishmentRequestAlertStore store = new RetryingPunishmentRequestAlertStore(
                delegate,
                Duration.ofSeconds(1),
                (audience, recipientId, rank, owner, limit, lease, now) -> {
                    fallbacks.incrementAndGet();
                    return List.of();
                },
                ignored -> { }
        );

        assertEquals(1, claim(
                store,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                RECIPIENT,
                NOW
        ).size());
        assertEquals(2, attempts.get());
        assertEquals(0, fallbacks.get());
    }

    private static List<PunishmentRequestAlertClaim> claim(
            RetryingPunishmentRequestAlertStore store,
            PunishmentRequestAlertAudience audience,
            UUID recipient,
            Instant now
    ) {
        return store.claimAudience(
                audience,
                recipient,
                audience == PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS
                        ? StaffRank.ADMIN : StaffRank.MOD,
                "worker",
                4,
                Duration.ofSeconds(30),
                now
        );
    }

    private static PunishmentRequestAlertStore delegate(
            AtomicReference<List<PunishmentRequestAlertClaim>> primary
    ) {
        return (PunishmentRequestAlertStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{PunishmentRequestAlertStore.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("claimAudience")) {
                        return primary.get();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        return null;
    }
}
