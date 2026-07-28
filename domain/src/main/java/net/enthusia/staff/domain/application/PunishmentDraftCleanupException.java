package net.enthusia.staff.domain.application;

public final class PunishmentDraftCleanupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String caseId;
    private final boolean replayed;

    public PunishmentDraftCleanupException(PunishmentResult.Accepted accepted, RuntimeException cause) {
        super("Punishment committed, but its draft could not be deleted", cause);
        if (accepted == null || cause == null) {
            throw new IllegalArgumentException("accepted result and cleanup failure must be present");
        }
        this.caseId = accepted.caseId().value();
        this.replayed = accepted.replayed();
    }

    public PunishmentResult.Accepted accepted() {
        return new PunishmentResult.Accepted(new net.enthusia.staff.common.CaseId(caseId), replayed);
    }
}
