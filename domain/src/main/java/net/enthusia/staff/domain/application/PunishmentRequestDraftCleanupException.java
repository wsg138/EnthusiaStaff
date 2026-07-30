package net.enthusia.staff.domain.application;

public final class PunishmentRequestDraftCleanupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final PunishmentRequestResult.Submitted submitted;

    public PunishmentRequestDraftCleanupException(
            PunishmentRequestResult.Submitted submitted,
            RuntimeException cause
    ) {
        super("Punishment request submitted, but its draft could not be deleted", cause);
        if (submitted == null || cause == null) {
            throw new IllegalArgumentException("submitted result and cleanup failure must be present");
        }
        this.submitted = submitted;
    }

    public PunishmentRequestResult.Submitted submitted() {
        return submitted;
    }
}
