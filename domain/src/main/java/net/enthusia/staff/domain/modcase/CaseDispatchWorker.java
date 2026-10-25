package net.enthusia.staff.domain.modcase;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Testable asynchronous dispatch. Unknown outcomes never silently retry or double-ping staff. */
public final class CaseDispatchWorker {
    public enum ResultType { CONFIRMED, NOT_SENT, POSSIBLY_SENT }
    public record Result(ResultType type, String receipt, String code) {
        public Result {
            Objects.requireNonNull(type);
            if (type != ResultType.CONFIRMED
                    && (code == null || !code.matches("[A-Z0-9_]{1,64}"))) {
                throw new IllegalArgumentException("safe delivery failure code required");
            }
            if (type == ResultType.CONFIRMED && (receipt == null || receipt.isBlank())) {
                throw new IllegalArgumentException("confirmed delivery needs a remote receipt");
            }
        }
        public static Result confirmed(String receipt) {
            return new Result(ResultType.CONFIRMED, receipt, null);
        }
        public static Result notSent(String code) {
            return new Result(ResultType.NOT_SENT, null, code);
        }
        public static Result possiblySent(String code) {
            return new Result(ResultType.POSSIBLY_SENT, null, code);
        }
    }
    @FunctionalInterface
    public interface Destination {
        /** MUST upsert by cardKey and dedupe by idempotencyKey. No public messages. */
        CompletionStage<Result> send(CaseNotice notice, CaseDelivery.Key key);
    }

    private final CaseLedger ledger;
    private final Map<CaseDelivery.Destination, Destination> destinations;
    private final Clock clock;

    public CaseDispatchWorker(CaseLedger ledger, Map<CaseDelivery.Destination, Destination> destinations,
                              Clock clock) {
        this.ledger = Objects.requireNonNull(ledger);
        this.destinations = new EnumMap<>(CaseDelivery.Destination.class);
        this.destinations.putAll(Objects.requireNonNull(destinations));
        this.clock = Objects.requireNonNull(clock);
        if (!this.destinations.keySet().containsAll(java.util.EnumSet.allOf(CaseDelivery.Destination.class))) {
            throw new IllegalArgumentException("two private destinations are required");
        }
    }

    public void runCycle(int limit) {
        for (CaseDelivery delivery : ledger.due(clock.instant(), limit)) {
            dispatch(delivery.key());
        }
    }

    private void dispatch(CaseDelivery.Key key) {
        CaseNotice notice = ledger.find(key).orElse(null);
        if (notice == null || !notice.expiresAt().isAfter(clock.instant())
                || notice.revision() != key.revision()) {
            return;
        }
        CaseDelivery leased = ledger.begin(key, clock.instant()).orElse(null);
        if (leased == null) {
            return;
        }
        CompletionStage<Result> send;
        try {
            send = destinations.get(key.destination()).send(notice, key);
            if (send == null) {
                send = CompletableFuture.completedFuture(Result.possiblySent("NO_SEND_RESULT"));
            }
        } catch (RuntimeException error) {
            send = CompletableFuture.completedFuture(Result.possiblySent("SEND_EXCEPTION"));
        }
        String token = leased.attemptToken();
        send.whenComplete((result, error) -> complete(key, token, leased.attempts(), result, error));
    }

    private void complete(CaseDelivery.Key key, String token, int attempts,
                          Result result, Throwable error) {
        if (error != null || result == null || result.type() == ResultType.POSSIBLY_SENT) {
            ledger.uncertain(key, token, "DELIVERY_UNCONFIRMED");
        } else if (result.type() == ResultType.CONFIRMED) {
            ledger.acknowledge(key, token, result.receipt());
        } else {
            long seconds = Math.min(600L, 5L << Math.min(7, Math.max(0, attempts - 1)));
            ledger.retry(key, token, result.code(),
                    clock.instant().plus(Duration.ofSeconds(seconds)));
        }
    }
}
