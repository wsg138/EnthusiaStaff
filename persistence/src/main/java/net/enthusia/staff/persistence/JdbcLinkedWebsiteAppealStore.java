package net.enthusia.staff.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.website.WebsiteAccountPunishment;
import net.enthusia.staff.domain.website.WebsiteAccountPunishmentPage;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;
import net.enthusia.staff.domain.website.WebsiteModerationException;

/** Reads only currently owned sanctions; never grants ownership from a browser-provided name. */
final class JdbcLinkedWebsiteAppealStore {
    static final Duration APPEAL_INTERVAL = Duration.ofDays(14);
    private static final String SELECT = """
            SELECT s.sanction_id, s.case_id, s.sanction_type, s.status, s.issued_at,
                   s.expiration_at, s.target_id, p.current_username,
                   COALESCE(NULLIF(c.public_reason, ''), 'No public reason provided') AS public_reason,
                   (SELECT MAX(GREATEST(a.created_at, COALESCE(e.created_at, a.created_at)))
                    FROM website_appeal_requests a
                    LEFT JOIN website_appeal_events e ON e.appeal_id = a.appeal_id
                      AND e.event_type IN ('SUBMITTED','RESUBMITTED')
                    WHERE a.punishment_id = s.sanction_id) AS last_submission
            FROM sanctions s
            JOIN cases c ON c.case_id = s.case_id
            JOIN players p ON p.player_id = s.target_id
            JOIN discord_minecraft_links link ON link.minecraft_player_id = s.target_id
            WHERE link.discord_user_id = ? AND link.unlinked_at IS NULL
              AND s.status IN ('ACTIVE','APPLIED','EXPIRED','ENDED_EARLY') AND c.state <> 'FULLY_OVERTURNED'
            """;
    private final DataSource dataSource;
    private final JdbcWebsiteAppealWorkflowStore workflow;

    JdbcLinkedWebsiteAppealStore(DataSource dataSource, JdbcWebsiteAppealWorkflowStore workflow) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        this.workflow = java.util.Objects.requireNonNull(workflow);
    }

    WebsiteAccountPunishmentPage list(String discordId, Optional<UUID> minecraftId,
            String scope, Optional<String> rawCursor, int limit, Instant now) {
        validate(discordId, now);
        if (minecraftId == null || rawCursor == null || limit < 1 || limit > 100
                || scope == null || !java.util.Set.of("ACTIVE", "EXPIRED", "ALL").contains(scope)) throw invalid();
        Optional<Cursor> cursor = rawCursor.map(JdbcLinkedWebsiteAppealStore::decodeCursor);
        String sql = SELECT
                + (minecraftId.isPresent() ? " AND s.target_id = ?" : "")
                + switch (scope) {
                    case "ACTIVE" -> " AND s.status IN ('ACTIVE','APPLIED') AND (s.expiration_at IS NULL OR s.expiration_at > ?)";
                    case "EXPIRED" -> " AND (s.status IN ('EXPIRED','ENDED_EARLY') OR s.expiration_at <= ?)";
                    default -> "";
                }
                + (cursor.isPresent() ? " AND (s.issued_at < ? OR (s.issued_at = ? AND s.sanction_id < ?))" : "")
                + " ORDER BY s.issued_at DESC, s.sanction_id DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, discordId);
            if (minecraftId.isPresent()) statement.setBytes(index++, UuidBytes.toBytes(minecraftId.orElseThrow()));
            if (!"ALL".equals(scope)) statement.setTimestamp(index++, Timestamp.from(now));
            if (cursor.isPresent()) {
                Cursor value = cursor.orElseThrow();
                statement.setTimestamp(index++, Timestamp.from(value.issuedAt()));
                statement.setTimestamp(index++, Timestamp.from(value.issuedAt()));
                statement.setBytes(index++, UuidBytes.toBytes(value.punishmentId()));
            }
            statement.setInt(index, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                List<WebsiteAccountPunishment> items = new ArrayList<>();
                while (result.next()) items.add(read(result, now));
                boolean more = items.size() > limit;
                if (more) items.removeLast();
                Optional<String> next = more ? Optional.of(encodeCursor(items.getLast())) : Optional.empty();
                return new WebsiteAccountPunishmentPage(items, next);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Owned punishments are unavailable", exception);
        }
    }

    WebsiteAppealSubmission submit(String discordId, UUID minecraftId, UUID punishmentId,
            String accountId, String reason, String idempotencyKey, Instant now) {
        validate(discordId, now);
        if (minecraftId == null || punishmentId == null) throw invalid();
        return JdbcTransactionSupport.execute(dataSource, "Unable to submit linked punishment appeal", connection -> {
            // All submission paths lock this sanction before checking the per-punishment interval.
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT + " AND s.target_id = ? AND s.sanction_id = ? FOR UPDATE")) {
                statement.setString(1, discordId);
                statement.setBytes(2, UuidBytes.toBytes(minecraftId));
                statement.setBytes(3, UuidBytes.toBytes(punishmentId));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new WebsiteModerationException(
                            WebsiteModerationException.Kind.NOT_FOUND, "PUNISHMENT_NOT_FOUND",
                            "That punishment does not belong to a currently linked account");
                    WebsiteAccountPunishment owned = read(result, now);
                    return workflow.submitVerifiedPunishment(connection, owned.punishmentId(), owned.caseId(),
                            accountId, owned.minecraftName(), reason, idempotencyKey, now);
                }
            }
        });
    }

    private static WebsiteAccountPunishment read(ResultSet result, Instant now) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        Timestamp last = result.getTimestamp("last_submission");
        Instant expiresAt = expiration == null ? null : expiration.toInstant();
        Instant next = last == null ? null : last.toInstant().plus(APPEAL_INTERVAL);
        boolean active = java.util.Set.of("ACTIVE", "APPLIED").contains(result.getString("status"))
                && (expiresAt == null || expiresAt.isAfter(now));
        return new WebsiteAccountPunishment(UuidBytes.fromBytes(result.getBytes("sanction_id")),
                new CaseId(result.getString("case_id")), result.getString("sanction_type"),
                result.getString("public_reason"), result.getTimestamp("issued_at").toInstant(),
                UuidBytes.fromBytes(result.getBytes("target_id")), result.getString("current_username"),
                active, expiresAt, next, next == null || !next.isAfter(now));
    }

    private static void validate(String discordId, Instant now) {
        if (discordId == null || !discordId.matches("[0-9]{16,22}") || now == null) throw invalid();
    }

    private static WebsiteModerationException invalid() {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INVALID,
                "INVALID_LINKED_APPEAL", "Linked appeal parameters are invalid");
    }

    private static String encodeCursor(WebsiteAccountPunishment item) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (item.issuedAt() + "|" + item.punishmentId()).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        try {
            if (encoded.length() > 256) throw invalid();
            String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2 || !parts[1].matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw invalid();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw invalid();
        }
    }

    private record Cursor(Instant issuedAt, UUID punishmentId) { }
}
