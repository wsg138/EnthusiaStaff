package net.enthusia.staff.domain.modcase;

import java.time.Clock;
import java.util.Objects;

/** Same case authority for Discord component actions and Paper GUI actions. No sanctions. */
public final class CaseStaffActions {
    private final CaseLedger ledger;
    private final CaseLedger.StaffGate gate;
    private final Clock clock;

    public CaseStaffActions(CaseLedger ledger, CaseLedger.StaffGate gate, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger);
        this.gate = Objects.requireNonNull(gate);
        this.clock = Objects.requireNonNull(clock);
    }

    public CaseLedger.Change claim(CaseDelivery.Key key, long expectedRevision, String actor) {
        return ledger.action(key, expectedRevision, actor, CaseNotice.Action.CLAIM, clock.instant(), gate);
    }

    public CaseLedger.Change resolve(CaseDelivery.Key key, long expectedRevision, String actor) {
        return ledger.action(key, expectedRevision, actor, CaseNotice.Action.RESOLVE, clock.instant(), gate);
    }
}