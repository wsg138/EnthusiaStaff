package net.enthusia.staff.paper.command;

/**
 * Storage acknowledgements are not transactional proof of a rollback.
 * A database write can commit before the caller observes an exception.
 */
final class PunishmentOutcomeUnknownNotice {
    private PunishmentOutcomeUnknownNotice() {
    }

    static String directPunishment() {
        return "Punishment operation failed; a draft, punishment, or approval request "
                + "may have been saved. Check case history, pending requests and saved "
                + "drafts before retrying.";
    }

    static String requestDecision() {
        return "Punishment request operation failed; an approval or denial may have been "
                + "committed. Check request status and case history before retrying.";
    }
}
