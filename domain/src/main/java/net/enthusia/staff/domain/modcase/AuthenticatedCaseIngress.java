package net.enthusia.staff.domain.modcase;

import java.util.Objects;

/** Preproduction adapter: only a separately authenticated API peer may submit scoped snapshots. */
public final class AuthenticatedCaseIngress {
    @FunctionalInterface
    public interface Authenticator {
        /** The peer is resolved by trusted transport, not from fields in an untrusted JSON body. */
        boolean permits(String authenticatedPeer, CaseNotice.Origin origin, String exactScope);
    }

    public enum Result { DENIED, CREATED, ADVANCED, REPLAY, STALE, CONFLICT }

    private final CaseLedger ledger;
    private final Authenticator authenticator;

    public AuthenticatedCaseIngress(CaseLedger ledger, Authenticator authenticator) {
        this.ledger = Objects.requireNonNull(ledger);
        this.authenticator = Objects.requireNonNull(authenticator);
    }

    public Result ingest(String authenticatedPeer, CaseNotice notice) {
        if (notice == null || authenticatedPeer == null || authenticatedPeer.isBlank()
                || !authenticator.permits(authenticatedPeer, notice.origin(), notice.scope())) {
            return Result.DENIED;
        }
        return Result.valueOf(ledger.accept(notice).name());
    }
}
