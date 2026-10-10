package net.enthusia.staff.paper.aireview;

/** Treat lost acknowledgements as uncertain; the API may have committed a write. */
final class AiReviewWriteFeedback {
    private AiReviewWriteFeedback() {
    }

    static boolean outcomeUncertain(String issue) {
        if (issue == null) {
            return true;
        }
        return issue.startsWith("central review timeout;")
                || issue.startsWith("central review network;")
                || issue.startsWith("central review unavailable;")
                || issue.startsWith("central review malformed;")
                || issue.startsWith("central review oversized;")
                || issue.startsWith("central review http;")
                || issue.startsWith("central review internal error;");
    }

    static String message(String issue) {
        if (outcomeUncertain(issue)) {
            return "Correction status UNKNOWN: " + issue
                    + ". Check the central event before submitting another vote.";
        }
        return "Correction was not confirmed: " + issue + ".";
    }
}
