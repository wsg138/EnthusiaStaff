package net.enthusia.staff.domain.application;

public record PunishmentRequestAlertBacklog(
        long pending,
        long leased,
        long delivered,
        long deadLetter
) {
    public PunishmentRequestAlertBacklog {
        if (pending < 0 || leased < 0 || delivered < 0 || deadLetter < 0) {
            throw new IllegalArgumentException("alert backlog counts cannot be negative");
        }
    }
}
