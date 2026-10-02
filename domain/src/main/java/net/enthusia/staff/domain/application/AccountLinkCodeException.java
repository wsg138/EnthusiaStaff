package net.enthusia.staff.domain.application;

import java.util.Objects;

/** Expected account-link code rejection that is safe to present without logging as a server fault. */
public final class AccountLinkCodeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public AccountLinkCodeException(Reason reason) {
        super(messageFor(reason));
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    private static String messageFor(Reason reason) {
        return switch (Objects.requireNonNull(reason, "reason")) {
            case INVALID -> "account-link code is invalid";
            case EXPIRED -> "account-link code expired";
            case REPLACED -> "account-link code was replaced";
            case ALREADY_USED -> "account-link code was already used";
        };
    }

    public enum Reason {
        INVALID,
        EXPIRED,
        REPLACED,
        ALREADY_USED
    }
}
