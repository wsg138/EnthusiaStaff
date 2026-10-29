package net.enthusia.staff.domain.staff;

/** Safe failure codes for private console diagnostics; contains no provider error or snapshot. */
public final class StaffSessionInspectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        AMBIGUOUS_SESSION,
        MISSING_SNAPSHOT,
        INVALID_SNAPSHOT_LENGTH,
        CHECKSUM_MISMATCH,
        INVALID_METADATA
    }

    private final Reason reason;

    public StaffSessionInspectionException(Reason reason) {
        super(java.util.Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
