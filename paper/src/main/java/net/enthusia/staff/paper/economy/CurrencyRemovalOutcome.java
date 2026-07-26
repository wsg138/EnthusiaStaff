package net.enthusia.staff.paper.economy;

import java.util.Objects;
import java.util.Optional;

public record CurrencyRemovalOutcome(
        Status status,
        long amountRemoved,
        long finalTotal,
        Optional<CurrencyAccountState> accountState,
        String detail
) {
    public CurrencyRemovalOutcome {
        Objects.requireNonNull(status, "status");
        if (amountRemoved < 0L || finalTotal < 0L) {
            throw new IllegalArgumentException("currency removal outcome values cannot be negative");
        }
        accountState = Objects.requireNonNull(accountState, "accountState");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        COMMITTED,
        STALE,
        INVALID_PLAN,
        LOCK_REQUIRED,
        PLAYER_OFFLINE,
        FAILED_ROLLED_BACK,
        QUARANTINE_REQUIRED
    }
}
