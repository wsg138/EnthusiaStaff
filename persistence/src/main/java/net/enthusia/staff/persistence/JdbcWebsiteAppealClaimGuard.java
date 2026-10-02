package net.enthusia.staff.persistence;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class JdbcWebsiteAppealClaimGuard {
    private final DataSource dataSource;

    JdbcWebsiteAppealClaimGuard(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("Website appeal claim guard data source is required");
        }
        this.dataSource = dataSource;
    }

    void requireOwnedClaim(UUID appealId, UUID reviewerAccountId) {
        if (appealId == null || reviewerAccountId == null) {
            throw invalid("INVALID_APPEAL_REVIEW_ACTION", "The appeal review action is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT reviewer_account_id
                     FROM website_appeal_requests
                     WHERE appeal_id = ?
                     LIMIT 1
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(appealId));
            try (ResultSet result = statement.executeQuery()) {
                requireMatchingReviewer(result, reviewerAccountId);
            }
        } catch (SQLException exception) {
            throw persistence("Unable to verify the website appeal claim", exception);
        }
    }

    private static void requireMatchingReviewer(ResultSet result, UUID reviewerAccountId) throws SQLException {
        if (!result.next()) {
            throw notFound("APPEAL_NOT_FOUND", "The appeal could not be found");
        }
        byte[] claimedBy = result.getBytes("reviewer_account_id");
        if (claimedBy == null) {
            throw conflict("APPEAL_NOT_CLAIMED", "Claim the appeal before recording a decision");
        }
        byte[] reviewer = UuidBytes.toBytes(reviewerAccountId);
        if (!MessageDigest.isEqual(claimedBy, reviewer)) {
            throw conflict("APPEAL_CLAIM_OWNED_BY_OTHER", "Another staff member owns this appeal claim");
        }
    }

    private static WebsiteModerationException invalid(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INVALID, code, message);
    }

    private static WebsiteModerationException notFound(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.NOT_FOUND, code, message);
    }

    private static WebsiteModerationException conflict(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.CONFLICT, code, message);
    }

    private static ModerationPersistenceException persistence(String message, Exception exception) {
        return exception instanceof ModerationPersistenceException persistenceException
                ? persistenceException
                : new ModerationPersistenceException(message, exception);
    }
}
