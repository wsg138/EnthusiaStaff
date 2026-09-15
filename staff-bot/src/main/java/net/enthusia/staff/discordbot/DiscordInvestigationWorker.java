package net.enthusia.staff.discordbot;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.investigation.EvasionAlert;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionAlertDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionCandidate;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionDeliveryChannel;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionDeliveryUpdate;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.PunishmentCaseDraft;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.PunishmentObservation;

/** Restart-safe D09 maintenance and dual-channel alert orchestration. */
final class DiscordInvestigationWorker {
    private static final Duration CASE_INACTIVITY = Duration.ofDays(30);
    private static final int BATCH_LIMIT = 100;
    private static final int MAX_RETRY_SHIFT = 10;

    private final DiscordInvestigationStore store;
    private final DiscordInvestigationAlertSink discordAlerts;
    private final DiscordInvestigationAlertSink minecraftAlerts;
    private final Clock clock;
    private final Duration retryBase;
    private final Duration retryMaximum;

    DiscordInvestigationWorker(
            DiscordInvestigationStore store,
            DiscordInvestigationAlertSink discordAlerts,
            DiscordInvestigationAlertSink minecraftAlerts,
            Clock clock,
            Duration retryBase,
            Duration retryMaximum
    ) {
        if (store == null || discordAlerts == null || minecraftAlerts == null || clock == null
                || retryBase == null || retryBase.isZero() || retryBase.isNegative()
                || retryMaximum == null || retryMaximum.compareTo(retryBase) < 0) {
            throw new IllegalArgumentException("investigation worker configuration is invalid");
        }
        this.store = store;
        this.discordAlerts = discordAlerts;
        this.minecraftAlerts = minecraftAlerts;
        this.clock = clock;
        this.retryBase = retryBase;
        this.retryMaximum = retryMaximum;
    }

    void runCycle() {
        Instant now = clock.instant();
        reconcilePunishmentCases();
        store.closeInactiveCases(now.minus(CASE_INACTIVITY), now, BATCH_LIMIT);
        store.purgeEligibleEvidence(now, BATCH_LIMIT);
        discoverEvasionAlerts(now);
        deliverAlerts(now);
    }

    private void reconcilePunishmentCases() {
        for (PunishmentObservation observation : store.punishmentObservations(BATCH_LIMIT)) {
            store.ensurePunishmentCase(new PunishmentCaseDraft(
                    punishmentOperation(observation.punishmentId()),
                    observation.punishmentId(),
                    observation.subjectId(),
                    observation.issuerId(),
                    observation.summary(),
                    observation.state(),
                    observation.expiresAt(),
                    observation.revision(),
                    observation.observedAt()
            ));
        }
    }

    private void discoverEvasionAlerts(Instant now) {
        for (EvasionCandidate candidate : store.evasionCandidates(BATCH_LIMIT)) {
            String operationKey = evasionOperation(candidate);
            store.createEvasionAlert(new EvasionAlertDraft(
                    deterministicId(operationKey),
                    operationKey,
                    candidate.subjectId(),
                    candidate.punishmentId(),
                    candidate.minecraftPlayerId(),
                    candidate.currentServer(),
                    candidate.playerRevision(),
                    now
            ));
        }
    }

    private void deliverAlerts(Instant now) {
        for (EvasionAlert alert : store.pendingEvasionAlerts(now, BATCH_LIMIT)) {
            deliverAlert(alert, now);
        }
    }

    private void deliverAlert(EvasionAlert alert, Instant now) {
        EvasionAlert current = alert;
        if (due(current.discordDelivery(), current.discordNextAttemptAt(), now)) {
            current = attempt(current, EvasionDeliveryChannel.DISCORD, discordAlerts, now);
        }
        if (due(current.minecraftDelivery(), current.minecraftNextAttemptAt(), now)) {
            attempt(current, EvasionDeliveryChannel.MINECRAFT, minecraftAlerts, now);
        }
    }

    private EvasionAlert attempt(
            EvasionAlert alert,
            EvasionDeliveryChannel channel,
            DiscordInvestigationAlertSink sink,
            Instant now
    ) {
        DiscordInvestigationAlertSink.Delivery delivery = safeDelivery(sink, alert, channel);
        int attempts = channel == EvasionDeliveryChannel.DISCORD
                ? alert.discordAttempts() : alert.minecraftAttempts();
        Optional<Instant> retryAt = delivery.delivered()
                ? Optional.empty() : Optional.of(now.plus(retryDelay(attempts)));
        return store.updateEvasionDelivery(new EvasionDeliveryUpdate(
                alert.alertId(),
                channel,
                delivery.delivered(),
                Optional.ofNullable(delivery.errorCode()),
                retryAt,
                alert.revision(),
                now
        ));
    }

    private static DiscordInvestigationAlertSink.Delivery safeDelivery(
            DiscordInvestigationAlertSink sink,
            EvasionAlert alert,
            EvasionDeliveryChannel channel
    ) {
        try {
            return sink.deliver(alert);
        } catch (RuntimeException exception) {
            String code = channel == EvasionDeliveryChannel.DISCORD
                    ? "DISCORD_DELIVERY_EXCEPTION" : "MINECRAFT_DELIVERY_EXCEPTION";
            return DiscordInvestigationAlertSink.Delivery.retry(code);
        }
    }

    private Duration retryDelay(int attempts) {
        long multiplier = 1L << Math.min(attempts, MAX_RETRY_SHIFT);
        Duration candidate = retryBase.multipliedBy(multiplier);
        return candidate.compareTo(retryMaximum) > 0 ? retryMaximum : candidate;
    }

    private static boolean due(
            EvasionAlert.DeliveryState state,
            Optional<Instant> nextAttemptAt,
            Instant now
    ) {
        return state != EvasionAlert.DeliveryState.DELIVERED
                && nextAttemptAt.isPresent()
                && !nextAttemptAt.orElseThrow().isAfter(now);
    }

    private static String punishmentOperation(UUID punishmentId) {
        return "d09:punishment:" + punishmentId;
    }

    private static String evasionOperation(EvasionCandidate candidate) {
        return "d09:evasion:" + candidate.punishmentId() + ':' + candidate.minecraftPlayerId()
                + ':' + candidate.playerRevision();
    }

    private static UUID deterministicId(String operationKey) {
        return UUID.nameUUIDFromBytes(operationKey.getBytes(StandardCharsets.UTF_8));
    }
}
