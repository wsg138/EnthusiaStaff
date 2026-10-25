package net.enthusia.staff.domain.modcase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Atomic case and per-destination outbox boundary. The implementation owns CAS and persistence. */
public interface CaseLedger {
    enum Ingest { CREATED, ADVANCED, REPLAY, STALE, CONFLICT }
    enum Change { APPLIED, STALE, DENIED, EXPIRED, NOT_APPLICABLE, NOT_FOUND }

    Ingest accept(CaseNotice event);
    Optional<CaseNotice> find(CaseDelivery.Key key);
    List<CaseDelivery> due(Instant now, int limit);
    Optional<CaseDelivery> begin(CaseDelivery.Key key, Instant now);
    boolean acknowledge(CaseDelivery.Key key, String token, String remoteReceipt);
    boolean retry(CaseDelivery.Key key, String token, String code, Instant retryAt);
    boolean uncertain(CaseDelivery.Key key, String token, String code);
    boolean reconcile(CaseDelivery.Key key, String receipt, boolean confirmedAbsent, Instant now);
    List<CaseDelivery> unresolved();
    List<CaseNotice> active(Instant now, int limit);
    Optional<String> previousReceipt(CaseDelivery.Key key);
    Change action(CaseDelivery.Key key, long expectedRevision, String actor,
                  CaseNotice.Action action, Instant now, StaffGate gate);

    @FunctionalInterface
    interface StaffGate {
        /** Must check CURRENT authority (Discord guild/role or Paper rank/duty), never a cached click. */
        boolean permits(String actor, CaseNotice notice, CaseNotice.Action action);
    }
}
