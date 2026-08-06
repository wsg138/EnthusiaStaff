package net.enthusia.staff.domain.website;

import java.util.OptionalLong;

public final class AppealMutationPendingOutcome {
    private static final String PREFIX = "MUTATION_PENDING_R";

    private AppealMutationPendingOutcome() {
    }

    public static String encode(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("Appeal pending revision must be nonnegative");
        }
        return PREFIX + revision;
    }

    /**
     * Parses the shared pending-mutation wire format.
     *
     * @param outcomeCode stored appeal outcome code
     * @return the encoded revision, or empty when the outcome is not pending
     * @throws NumberFormatException when the pending prefix is present but the revision is invalid
     */
    public static OptionalLong parse(String outcomeCode) {
        if (outcomeCode == null || !outcomeCode.startsWith(PREFIX)) {
            return OptionalLong.empty();
        }
        String encodedRevision = outcomeCode.substring(PREFIX.length());
        long revision = Long.parseLong(encodedRevision);
        if (revision < 0 || !Long.toString(revision).equals(encodedRevision)) {
            throw new NumberFormatException("noncanonical appeal pending revision");
        }
        return OptionalLong.of(revision);
    }
}
