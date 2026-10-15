package net.enthusia.staff.domain.modcase;

import java.time.Instant;
import java.util.UUID;

/** One destination per case revision, with an explicit ambiguity barrier after a possible send. */
public record CaseDelivery(Key key, State state, int attempts, String attemptToken,
                           Instant nextAttempt, String remoteReceipt, String failureCode) {
    public enum Destination { DISCORD, MINECRAFT }
    public enum State { PENDING, IN_FLIGHT, RETRY, UNCERTAIN, DELIVERED }

    public record Key(CaseNotice.Origin origin, String scope, UUID caseId,
                      long revision, Destination destination) {
        public Key {
            if (origin == null || scope == null || scope.isBlank() || caseId == null || revision < 0
                    || destination == null) {
                throw new IllegalArgumentException("invalid case delivery key");
            }
        }
        public String idempotencyKey() {
            return origin + ":" + scope + ":" + caseId + ":" + revision + ":" + destination;
        }
        public String cardKey() {
            return origin + ":" + scope + ":" + caseId;
        }
    }

    public CaseDelivery {
        if (key == null || state == null || attempts < 0
                || (state == State.IN_FLIGHT) != (attemptToken != null)
                || (state == State.DELIVERED) != (remoteReceipt != null)
                || ((state == State.PENDING || state == State.RETRY) != (nextAttempt != null))) {
            throw new IllegalArgumentException("invalid delivery state");
        }
    }

    public static CaseDelivery pending(Key key, Instant now) {
        return new CaseDelivery(key, State.PENDING, 0, null, now, null, null);
    }

    public boolean due(Instant now) {
        return nextAttempt != null && !nextAttempt.isAfter(now);
    }

    public CaseDelivery start(String token) {
        return new CaseDelivery(key, State.IN_FLIGHT, attempts + 1, token, null, null, null);
    }

    public CaseDelivery sent(String receipt) {
        return new CaseDelivery(key, State.DELIVERED, attempts, null, null, receipt, null);
    }

    public CaseDelivery retry(String code, Instant at) {
        return new CaseDelivery(key, State.RETRY, attempts, null, at, null, code);
    }

    public CaseDelivery uncertain(String code) {
        return new CaseDelivery(key, State.UNCERTAIN, attempts, null, null, null, code);
    }
}
