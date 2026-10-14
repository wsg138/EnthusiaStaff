package net.enthusia.staff.domain.modcase;

import java.time.Clock;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Staff commands forwarded to the canonical API authority, never applied to the local inbox.
 * This is an isolated contract; no production HTTP or JDA binding is included.
 */
public final class AuthorityBackedCaseActions {
    private static final Pattern SAFE_VALUE = Pattern.compile("[a-zA-Z0-9_:/.-]{8,128}");

    public enum Outcome {
        SUBMITTED_AWAITING_EVENT, STALE, DENIED, EXPIRED, NOT_APPLICABLE, NOT_FOUND, RETRYABLE
    }

    public enum TransportStatus { ACCEPTED, STALE, DENIED, RETRYABLE }

    /**
     * A gateway MUST reauthenticate the staff actor, authorize origin/scope, reject stale
     * expected revisions, and durably deduplicate the command ID at the canonical API.
     */
    @FunctionalInterface
    public interface Gateway {
        TransportStatus send(Command command);
    }

    public record Command(CaseNotice.Origin origin, String scope, java.util.UUID caseId,
                          long expectedRevision, String actor, CaseNotice.Action action,
                          String commandId) {
        public Command {
            if (origin == null || scope == null || scope.isBlank() || caseId == null
                    || expectedRevision < 1 || actor == null || actor.isBlank()
                    || action == null || commandId == null
                    || !SAFE_VALUE.matcher(commandId).matches()) {
                throw new IllegalArgumentException("invalid canonical staff command");
            }
        }
    }

    private final CaseLedger replica;
    private final CaseLedger.StaffGate gate;
    private final Clock clock;
    private final Gateway gateway;

    public AuthorityBackedCaseActions(CaseLedger replica, CaseLedger.StaffGate gate,
                                      Clock clock, Gateway gateway) {
        this.replica = Objects.requireNonNull(replica);
        this.gate = Objects.requireNonNull(gate);
        this.clock = Objects.requireNonNull(clock);
        this.gateway = Objects.requireNonNull(gateway);
    }

    public Outcome claim(CaseDelivery.Key key, long expectedRevision, String actor,
                         String commandId) {
        return submit(key, expectedRevision, actor, CaseNotice.Action.CLAIM, commandId);
    }

    public Outcome resolve(CaseDelivery.Key key, long expectedRevision, String actor,
                           String commandId) {
        return submit(key, expectedRevision, actor, CaseNotice.Action.RESOLVE, commandId);
    }

    private Outcome submit(CaseDelivery.Key key, long expectedRevision, String actor,
                           CaseNotice.Action action, String commandId) {
        if (key == null || actor == null || actor.isBlank()) {
            return Outcome.DENIED;
        }
        CaseNotice notice = replica.find(key).orElse(null);
        if (notice == null) {
            return Outcome.NOT_FOUND;
        }
        if (key.revision() != expectedRevision || notice.revision() != expectedRevision) {
            return Outcome.STALE;
        }
        if (!notice.expiresAt().isAfter(clock.instant())) {
            return Outcome.EXPIRED;
        }
        if (!gate.permits(actor, notice, action)) {
            return Outcome.DENIED;
        }
        if (!applicable(notice, actor, action)) {
            return Outcome.NOT_APPLICABLE;
        }
        Command command = new Command(notice.origin(), notice.scope(), notice.caseId(),
                expectedRevision, actor, action, commandId);
        return map(gateway.send(command));
    }

    private static boolean applicable(CaseNotice notice, String actor, CaseNotice.Action action) {
        return action == CaseNotice.Action.CLAIM && notice.state() == CaseNotice.State.OPEN
                || action == CaseNotice.Action.RESOLVE
                && notice.state() == CaseNotice.State.CLAIMED && actor.equals(notice.claimant());
    }

    private static Outcome map(TransportStatus status) {
        if (status == null) {
            return Outcome.RETRYABLE;
        }
        return switch (status) {
            case ACCEPTED -> Outcome.SUBMITTED_AWAITING_EVENT;
            case STALE -> Outcome.STALE;
            case DENIED -> Outcome.DENIED;
            case RETRYABLE -> Outcome.RETRYABLE;
        };
    }
}
