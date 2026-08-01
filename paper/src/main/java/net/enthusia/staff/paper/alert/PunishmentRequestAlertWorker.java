package net.enthusia.staff.paper.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestRecipientPolicy;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.PunishmentRequestAlertStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;

public final class PunishmentRequestAlertWorker {
    static final String RECIPIENT_INELIGIBLE = "RECIPIENT_INELIGIBLE";
    static final String REQUESTER_CONFLICT = "REQUESTER_CONFLICT";
    static final String VISIBILITY_DENIED = "VISIBILITY_DENIED";
    static final String PLAYER_OFFLINE = "PLAYER_OFFLINE";
    static final String PRESENTATION_UNAVAILABLE = "PRESENTATION_UNAVAILABLE";
    static final String PRESENTATION_FAILED = "PRESENTATION_FAILED";
    static final String REQUEST_LOOKUP_FAILED = "REQUEST_LOOKUP_FAILED";
    static final String REQUEST_MISSING = "REQUEST_MISSING";
    static final String INVALID_PRESENTATION_DATA = "INVALID_PRESENTATION_DATA";

    private final Clock clock;
    private final String owner;
    private final PunishmentRequestAlertWorkerSettings settings;
    private final PunishmentRequestAlertStore alerts;
    private final PunishmentRequestStore requests;
    private final PlayerDirectory players;
    private final PunishmentRequestAlertRenderer renderer;
    private final PunishmentRequestAlertPresenter presenter;
    private final PunishmentRequestRecipientPolicy recipientPolicy;
    private final Executor asynchronous;
    private final RecipientExecutor synchronous;
    private final BooleanSupplier stopping;
    private final Logger logger;

    public PunishmentRequestAlertWorker(
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            PunishmentRequestAlertRenderer renderer,
            PunishmentRequestAlertPresenter presenter,
            Executor asynchronous,
            Consumer<Runnable> synchronous,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this(
                clock,
                owner,
                settings,
                alerts,
                requests,
                players,
                renderer,
                presenter,
                asynchronous,
                globalExecutor(synchronous),
                stopping,
                logger
        );
    }

    PunishmentRequestAlertWorker(
            Clock clock,
            String owner,
            PunishmentRequestAlertWorkerSettings settings,
            PunishmentRequestAlertStore alerts,
            PunishmentRequestStore requests,
            PlayerDirectory players,
            PunishmentRequestAlertRenderer renderer,
            PunishmentRequestAlertPresenter presenter,
            Executor asynchronous,
            RecipientExecutor synchronous,
            BooleanSupplier stopping,
            Logger logger
    ) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("alert lease owner must be present and at most 128 characters");
        }
        this.owner = owner;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.players = Objects.requireNonNull(players, "players");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.recipientPolicy = new PunishmentRequestRecipientPolicy();
        this.asynchronous = Objects.requireNonNull(asynchronous, "asynchronous");
        this.synchronous = Objects.requireNonNull(synchronous, "synchronous");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private static RecipientExecutor globalExecutor(Consumer<Runnable> synchronous) {
        Objects.requireNonNull(synchronous, "synchronous");
        return (ignored, action, retired) -> {
            synchronous.accept(action);
            return true;
        };
    }

    public void deliver(
            PunishmentRequestAlertRecipient snapshot,
            ClaimBudget claimBudget,
            Runnable completion
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(claimBudget, "claimBudget");
        Objects.requireNonNull(completion, "completion");
        Completion once = new Completion(completion);
        if (stopping.getAsBoolean()) {
            once.run();
            return;
        }
        try {
            asynchronous.execute(() -> claimAndRender(snapshot, claimBudget, once));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert worker submission failed", exception);
            once.run();
        }
    }

    private void claimAndRender(
            PunishmentRequestAlertRecipient snapshot,
            ClaimBudget claimBudget,
            Completion completion
    ) {
        if (stopping.getAsBoolean()) {
            completion.run();
            return;
        }
        List<PunishmentRequestAlertClaim> claims;
        try {
            claims = claim(snapshot, claimBudget);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Punishment request alert claim cycle failed", exception);
            completion.run();
            return;
        }
        List<PunishmentRequestAlertPresentation> presentations = new ArrayList<>();
        for (PunishmentRequestAlertClaim claim : claims) {
            if (stopping.getAsBoolean() || presentations.size() >= settings.presentationLimit()) {
                break;
            }
            Optional<PunishmentApprovalRequest> loaded;
            try {
                loaded = requests.find(claim.intent().requestId());
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Punishment request alert lookup failed for alert "
                        + claim.intent().alertId(), exception);
                retryableFailure(claim, REQUEST_LOOKUP_FAILED);
                continue;
            }
            if (loaded.isEmpty()) {
                logger.severe("Punishment request alert references a missing request: alert="
                        + claim.intent().alertId() + " request=" + claim.intent().requestId()
                        + " recipient=" + claim.deliveryId().recipientId()
                        + " attempt=" + claim.attemptCount());
                permanentFailure(claim, REQUEST_MISSING);
                continue;
            }
            PunishmentApprovalRequest request = loaded.orElseThrow();
            try {
                presentations.add(renderer.render(
                        claim,
                        request,
                        displayName(request.proposal().targetId()),
                        displayName(claim.intent().occurrence().actorId())
                ));
            } catch (RuntimeException exception) {
                logger.log(Level.SEVERE, "Punishment request alert cannot be rendered safely: alert="
                        + claim.intent().alertId() + " request=" + claim.intent().requestId()
                        + " recipient=" + claim.deliveryId().recipientId()
                        + " attempt=" + claim.attemptCount(), exception);
                permanentFailure(claim, INVALID_PRESENTATION_DATA);
            }
        }
        if (presentations.isEmpty()) {
            completion.run();
            return;
        }
        handoff(snapshot.playerId(), presentations, completion);
    }

    private void handoff(
            UUID recipientId,
            List<PunishmentRequestAlertPresentation> presentations,
            Completion completion
    ) {
        AtomicBoolean resolved = new AtomicBoolean();
        Runnable retired = () -> retryHandoff(
                resolved,
                presentations,
                PLAYER_OFFLINE,
                completion
        );
        try {
            boolean scheduled = synchronous.execute(
                    recipientId,
                    () -> {
                        if (resolved.compareAndSet(false, true)) {
                            present(recipientId, presentations, completion);
                        }
                    },
                    retired
            );
            if (!scheduled) {
                retired.run();
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert recipient handoff failed", exception);
            retryHandoff(resolved, presentations, PRESENTATION_UNAVAILABLE, completion);
        }
    }

    private void retryHandoff(
            AtomicBoolean resolved,
            List<PunishmentRequestAlertPresentation> presentations,
            String code,
            Completion completion
    ) {
        if (!resolved.compareAndSet(false, true)) {
            return;
        }
        queueOutcomes(
                presentations.stream()
                        .map(value -> Outcome.retry(value.claim(), code))
                        .toList(),
                completion
        );
    }

    private List<PunishmentRequestAlertClaim> claim(
            PunishmentRequestAlertRecipient recipient,
            ClaimBudget budget
    ) {
        Instant now = clock.instant();
        List<PunishmentRequestAlertClaim> claimed = new ArrayList<>();
        claimDirect(recipient, budget, now, claimed);
        StaffRank rank = recipient.rank();
        if (rank != null && rank.canApprovePunishmentRequests()) {
            claimAudience(
                    PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                    recipient,
                    rank,
                    settings.reviewerBatch(),
                    budget,
                    now,
                    claimed
            );
        }
        if (rank == StaffRank.ADMIN || rank == StaffRank.FOUNDER) {
            claimAudience(
                    PunishmentRequestAlertAudience.OPERATIONAL_ADMINISTRATORS,
                    recipient,
                    rank,
                    settings.operationalBatch(),
                    budget,
                    now,
                    claimed
            );
        }
        return List.copyOf(claimed);
    }

    private void claimDirect(
            PunishmentRequestAlertRecipient recipient,
            ClaimBudget budget,
            Instant now,
            List<PunishmentRequestAlertClaim> claimed
    ) {
        int reserved = budget.acquire(settings.directBatch());
        if (reserved == 0) {
            return;
        }
        try {
            List<PunishmentRequestAlertClaim> direct = alerts.claimDirect(
                    recipient.playerId(), owner, reserved, settings.leaseDuration(), now);
            claimed.addAll(direct);
            budget.release(reserved - direct.size());
        } catch (RuntimeException exception) {
            budget.release(reserved);
            throw exception;
        }
    }

    private void claimAudience(
            PunishmentRequestAlertAudience audience,
            PunishmentRequestAlertRecipient recipient,
            StaffRank rank,
            int configuredBatch,
            ClaimBudget budget,
            Instant now,
            List<PunishmentRequestAlertClaim> claimed
    ) {
        int reserved = budget.acquire(configuredBatch);
        if (reserved == 0) {
            return;
        }
        try {
            List<PunishmentRequestAlertClaim> audienceClaims = alerts.claimAudience(
                    audience,
                    recipient.playerId(),
                    rank,
                    owner,
                    reserved,
                    settings.leaseDuration(),
                    now
            );
            claimed.addAll(audienceClaims);
            budget.release(reserved - audienceClaims.size());
        } catch (RuntimeException exception) {
            budget.release(reserved);
            throw exception;
        }
    }

    private void present(
            UUID expectedRecipient,
            List<PunishmentRequestAlertPresentation> presentations,
            Completion completion
    ) {
        if (stopping.getAsBoolean()) {
            // Leases deliberately remain unresolved and will be recovered after expiry.
            completion.run();
            return;
        }
        Optional<PunishmentRequestAlertRecipient> current;
        try {
            current = presenter.current(expectedRecipient);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert recipient lookup failed", exception);
            queueOutcomes(presentations.stream()
                    .map(value -> Outcome.retry(value.claim(), PRESENTATION_UNAVAILABLE))
                    .toList(), completion);
            return;
        }
        if (current.isEmpty()) {
            queueOutcomes(presentations.stream()
                    .map(value -> Outcome.retry(value.claim(), PLAYER_OFFLINE))
                    .toList(), completion);
            return;
        }
        PunishmentRequestAlertRecipient recipient = current.orElseThrow();
        List<Outcome> outcomes = new ArrayList<>(presentations.size());
        for (PunishmentRequestAlertPresentation presentation : presentations) {
            Outcome authorization = authorize(recipient, presentation);
            if (authorization != null) {
                outcomes.add(authorization);
                continue;
            }
            try {
                outcomes.add(presenter.present(recipient, presentation)
                        ? Outcome.delivered(presentation.claim())
                        : Outcome.retry(presentation.claim(), PRESENTATION_UNAVAILABLE));
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Punishment request alert presentation failed", exception);
                outcomes.add(Outcome.retry(presentation.claim(), PRESENTATION_FAILED));
            }
        }
        queueOutcomes(outcomes, completion);
    }

    private Outcome authorize(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertPresentation presentation
    ) {
        PunishmentRequestAlertClaim claim = presentation.claim();
        if (!claim.deliveryId().recipientId().equals(recipient.playerId())) {
            return Outcome.cancel(claim, RECIPIENT_INELIGIBLE);
        }
        return switch (claim.intent().audience()) {
            case DIRECT_RECIPIENT -> claim.intent().recipientId().equals(recipient.playerId())
                    ? null : Outcome.cancel(claim, RECIPIENT_INELIGIBLE);
            case ELIGIBLE_REVIEWERS -> authorizeReviewer(recipient, presentation);
            case OPERATIONAL_ADMINISTRATORS -> authorizeOperational(recipient, claim);
        };
    }

    private Outcome authorizeReviewer(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertPresentation presentation
    ) {
        PunishmentRequestAlertClaim claim = presentation.claim();
        PunishmentApprovalRequest request = presentation.request();
        if (request.proposal().requester().id().equals(recipient.playerId())) {
            return Outcome.cancel(claim, REQUESTER_CONFLICT);
        }
        if (!visibilityPermits(request.proposal().visibility(), recipient.rank())) {
            return Outcome.cancel(claim, VISIBILITY_DENIED);
        }
        if (recipient.rank() == null) {
            return Outcome.cancel(claim, RECIPIENT_INELIGIBLE);
        }
        Actor actor = new Actor(recipient.playerId(), recipient.playerName(), recipient.rank());
        if (!recipientPolicy.mayReceiveReviewerAlert(
                actor,
                claim.intent().excludedRecipientId(),
                claim.intent().minimumRank()
        ) || !recipientPolicy.mayReceiveReviewerAlert(actor, request)) {
            return Outcome.cancel(claim, RECIPIENT_INELIGIBLE);
        }
        // This is the freshest asynchronous request snapshot available. A database transition can
        // still race the final Bukkit packet presentation; the delivery model is intentionally
        // at-least-once and does not claim atomic state verification across that boundary.
        if (!request.pendingAt(clock.instant())) {
            return Outcome.cancel(claim, RECIPIENT_INELIGIBLE);
        }
        return null;
    }

    private Outcome authorizeOperational(
            PunishmentRequestAlertRecipient recipient,
            PunishmentRequestAlertClaim claim
    ) {
        if (recipient.rank() != StaffRank.ADMIN && recipient.rank() != StaffRank.FOUNDER) {
            return Outcome.cancel(claim, RECIPIENT_INELIGIBLE);
        }
        return null;
    }

    private static boolean visibilityPermits(CaseVisibility visibility, StaffRank rank) {
        if (visibility == null || rank == null) {
            return false;
        }
        // Private requests remain visible only inside the authorized moderation audience; they are
        // never redirected to direct/public recipients by this worker.
        return rank.canApprovePunishmentRequests();
    }

    private void queueOutcomes(List<Outcome> outcomes, Completion completion) {
        if (stopping.getAsBoolean()) {
            completion.run();
            return;
        }
        try {
            asynchronous.execute(() -> {
                try {
                    outcomes.forEach(this::recordOutcome);
                } finally {
                    completion.run();
                }
            });
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert outcome submission failed", exception);
            completion.run();
        }
    }

    private void recordOutcome(Outcome outcome) {
        Instant now = clock.instant();
        try {
            switch (outcome.kind()) {
                case DELIVERED -> alerts.delivered(outcome.claim().deliveryId(), owner, now);
                case CANCELLED -> alerts.cancel(
                        outcome.claim().deliveryId(), owner, outcome.code(), now);
                case RETRY -> alerts.failed(
                        outcome.claim().deliveryId(),
                        owner,
                        outcome.code(),
                        now.plus(settings.retryDelay(outcome.claim().attemptCount())),
                        now,
                        settings.maximumAttempts()
                );
            }
        } catch (RuntimeException exception) {
            // Never issue a competing outcome. The fenced lease is left for normal recovery.
            logger.log(Level.WARNING, "Punishment request alert outcome could not be recorded", exception);
        }
    }

    private void retryableFailure(PunishmentRequestAlertClaim claim, String code) {
        Instant now = clock.instant();
        try {
            alerts.failed(
                    claim.deliveryId(), owner, code,
                    now.plus(settings.retryDelay(claim.attemptCount())),
                    now,
                    settings.maximumAttempts()
            );
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert retry outcome could not be recorded", exception);
        }
    }

    private void permanentFailure(PunishmentRequestAlertClaim claim, String code) {
        Instant now = clock.instant();
        try {
            alerts.failed(claim.deliveryId(), owner, code, now, now, claim.attemptCount());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Punishment request alert dead-letter outcome could not be recorded", exception);
        }
    }

    private String displayName(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        try {
            return players.find(playerId.toString())
                    .flatMap(PlayerIdentity::currentUsername)
                    .orElse(playerId.toString());
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Player display-name lookup failed; UUID fallback will be used", exception);
            return playerId.toString();
        }
    }

    private enum OutcomeKind {
        DELIVERED,
        CANCELLED,
        RETRY
    }

    private record Outcome(OutcomeKind kind, PunishmentRequestAlertClaim claim, String code) {
        private static Outcome delivered(PunishmentRequestAlertClaim claim) {
            return new Outcome(OutcomeKind.DELIVERED, claim, null);
        }

        private static Outcome cancel(PunishmentRequestAlertClaim claim, String code) {
            return new Outcome(OutcomeKind.CANCELLED, claim, code);
        }

        private static Outcome retry(PunishmentRequestAlertClaim claim, String code) {
            return new Outcome(OutcomeKind.RETRY, claim, code);
        }
    }

    @FunctionalInterface
    interface RecipientExecutor {
        boolean execute(UUID playerId, Runnable action, Runnable retired);
    }

    public static final class ClaimBudget {
        private final int maximum;
        private final AtomicInteger remaining;

        public ClaimBudget(int maximum) {
            if (maximum < 1) {
                throw new IllegalArgumentException("claim budget must be positive");
            }
            this.maximum = maximum;
            this.remaining = new AtomicInteger(maximum);
        }

        int acquire(int requested) {
            if (requested < 1) {
                return 0;
            }
            while (true) {
                int current = remaining.get();
                if (current == 0) {
                    return 0;
                }
                int granted = Math.min(current, requested);
                if (remaining.compareAndSet(current, current - granted)) {
                    return granted;
                }
            }
        }

        void release(int unused) {
            if (unused <= 0) {
                return;
            }
            remaining.updateAndGet(current -> Math.min(maximum, current + unused));
        }

        public int remaining() {
            return remaining.get();
        }
    }

    private static final class Completion implements Runnable {
        private final Runnable delegate;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Completion(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            if (completed.compareAndSet(false, true)) {
                delegate.run();
            }
        }
    }
}
