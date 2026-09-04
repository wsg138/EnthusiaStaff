package net.enthusia.staff.discordbot;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;

/** Resolves current Enthusia staff rank from an authoritative non-Discord source. */
interface StaffAuthorityClient {
    Optional<StaffRank> rank(UUID playerId);

    final class UnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnavailableException(String message) {
            super(message);
        }

        UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
