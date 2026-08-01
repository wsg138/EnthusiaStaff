package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.security.PunishmentCodeProtector;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PunishmentCodeBinding;
import net.enthusia.staff.domain.website.PunishmentCodeDisplay;
import net.enthusia.staff.domain.website.WebsiteModerationException;

public final class JdbcWebsiteModerationStore implements WebsiteModerationStore {
    private static final int MAX_BATCH = 5_000;

    private final DataSource dataSource;
    private final JdbcPublicPunishmentRegistry publicRegistry;
    private final JdbcPunishmentCodeStore punishmentCodes;
    private final JdbcWebsiteAppealStore appeals;

    public JdbcWebsiteModerationStore(
            DataSource dataSource,
            PunishmentCodeProtector codeProtector,
            ObjectMapper json
    ) {
        if (dataSource == null || codeProtector == null || json == null) {
            throw new IllegalArgumentException("Website moderation store dependencies are required");
        }
        this.dataSource = dataSource;
        this.publicRegistry = new JdbcPublicPunishmentRegistry(dataSource);
        JdbcPunishmentCodeRepository punishmentCodeRepository = new JdbcPunishmentCodeRepository();
        this.punishmentCodes = new JdbcPunishmentCodeStore(
                dataSource,
                codeProtector,
                punishmentCodeRepository,
                new JdbcWebsiteAuditWriter(json)
        );
        this.appeals = new JdbcWebsiteAppealStore(
                dataSource,
                codeProtector,
                punishmentCodeRepository
        );
    }

    @Override
    public PublicPunishmentPage listPublic(
            PublicPunishmentFilter filter,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        return publicRegistry.listPublic(filter, encodedCursor, limit, now);
    }

    @Override
    public List<PublicPunishment> searchPublic(String query, int limit, Instant now) {
        return publicRegistry.searchPublic(query, limit, now);
    }

    @Override
    public Optional<PublicPunishment> publicCase(CaseId caseId, Instant now) {
        return publicRegistry.publicCase(caseId, now);
    }

    @Override
    public PunishmentCodeBinding claimCode(String code, String accountId, String username, Instant now) {
        return punishmentCodes.claimCode(code, accountId, username, now);
    }

    @Override
    public PunishmentCodeBinding revalidateCode(
            UUID punishmentId,
            int codeGeneration,
            String accountId,
            Instant now
    ) {
        return punishmentCodes.revalidateCode(punishmentId, codeGeneration, accountId, now);
    }

    @Override
    public Optional<PunishmentCodeDisplay> codeForSanction(UUID punishmentId, Instant now) {
        return punishmentCodes.codeForSanction(punishmentId, now);
    }

    @Override
    public List<PunishmentCodeDisplay> codesForCase(CaseId caseId, Instant now) {
        return punishmentCodes.codesForCase(caseId, now);
    }

    @Override
    public int ensureEligibleCodes(Instant now, int limit) {
        return punishmentCodes.ensureEligibleCodes(now, limit);
    }

    @Override
    public PunishmentCodeDisplay rotateCode(UUID punishmentId, UUID actorId, Instant now) {
        return punishmentCodes.rotateCode(punishmentId, actorId, now);
    }

    @Override
    public boolean revokeCode(UUID punishmentId, UUID actorId, Instant now) {
        return punishmentCodes.revokeCode(punishmentId, actorId, now);
    }

    @Override
    public boolean recordApiNonce(byte[] nonceHash, Instant expiresAt) {
        if (nonceHash == null || nonceHash.length != 32 || expiresAt == null) {
            throw invalid("INVALID_NONCE", "The API nonce is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO website_api_nonces(nonce_hash, expires_at) VALUES (?, ?)
                     """)) {
            statement.setBytes(1, nonceHash.clone());
            statement.setTimestamp(2, Timestamp.from(expiresAt));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw persistence("Unable to record the API nonce", exception);
        }
    }

    @Override
    public int purgeExpiredApiNonces(Instant now, int limit) {
        if (now == null || limit < 1 || limit > MAX_BATCH) {
            throw invalid("INVALID_NONCE_BATCH", "The API nonce cleanup batch is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM website_api_nonces
                     WHERE expires_at <= ?
                     ORDER BY expires_at
                     LIMIT ?
                     """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setInt(2, limit);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw persistence("Unable to purge expired API nonces", exception);
        }
    }

    @Override
    public AppealAcceptancePreparation prepareAppealAcceptance(
            UUID appealId,
            UUID punishmentId,
            CaseId caseId,
            String accountId,
            String idempotencyKey,
            Instant now
    ) {
        return appeals.prepare(
                appealId,
                punishmentId,
                caseId,
                accountId,
                idempotencyKey,
                now
        );
    }

    @Override
    public void completeAppealAcceptance(UUID appealId, String state, String outcomeCode, Instant now) {
        appeals.complete(appealId, state, outcomeCode, now);
    }

    private static WebsiteModerationException invalid(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INVALID, code, message);
    }

    private static ModerationPersistenceException persistence(String message, Exception exception) {
        return exception instanceof ModerationPersistenceException persistenceException
                ? persistenceException
                : new ModerationPersistenceException(message, exception);
    }

}
