package net.enthusia.staff.paper.economy;

import java.util.Objects;
import java.util.Optional;

public record CurrencyRestoreOutcome(
        Status status,
        Optional<CurrencyAccountState> accountState,
        String detail
) {
    public CurrencyRestoreOutcome {
        Objects.requireNonNull(status, "status");
        accountState = Objects.requireNonNull(accountState, "accountState");
        detail = detail == null ? "" : detail;
    }

    public enum Status {
        RESTORED,
        STALE,
        LOCK_REQUIRED,
        PLAYER_OFFLINE,
        FAILED_ROLLED_BACK,
        QUARANTINE_REQUIRED
    }
}
