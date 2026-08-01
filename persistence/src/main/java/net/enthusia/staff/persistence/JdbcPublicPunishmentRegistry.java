package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.website.PublicPunishment;
import net.enthusia.staff.domain.website.PublicPunishmentFilter;
import net.enthusia.staff.domain.website.PublicPunishmentPage;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import net.enthusia.staff.domain.website.WebsiteModerationException;

final class JdbcPublicPunishmentRegistry {
    private static final int MIN_PUBLIC_LIMIT = 1;
    private static final int MAX_PUBLIC_LIMIT = 100;
    private static final int CURSOR_LOOKAHEAD = 1;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_LENGTH = 80;
    private static final String SEARCH_PATTERN = "[A-Za-z0-9_-]+";
    private static final String PUBLIC_TYPE_CONDITION = """
              AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN', 'MUTE', 'WARNING')
              AND s.status IN ('ACTIVE', 'APPLIED', 'EXPIRED', 'ENDED_EARLY', 'REVOKED')
              AND CHAR_LENGTH(p.current_username) BETWEEN 3 AND 16
              AND p.current_username REGEXP '^[A-Za-z0-9_]{3,16}$'
            """;
    private static final String PUBLIC_SELECT = """
            SELECT s.sanction_id, s.case_id, s.sanction_type, s.status, s.issued_at,
                   s.expiration_at, c.public_reason, c.sanction_family, p.current_username,
                   pc.status AS code_status
            FROM public_sanctions s
            JOIN public_cases c ON c.case_id = s.case_id
            JOIN public_player_names p ON p.player_id = s.target_id
            LEFT JOIN punishment_codes pc ON pc.sanction_id = s.sanction_id
            WHERE 1 = 1
            """ + PUBLIC_TYPE_CONDITION;
    private static final String SEARCH_SQL = PUBLIC_SELECT + """
              AND (
                    c.case_id = ?
                    OR p.lowercase_username = ?
                    OR EXISTS (
                        SELECT 1 FROM public_player_name_history history
                        WHERE history.player_id = s.target_id
                          AND history.lowercase_username = ?
                    )
              )
            ORDER BY s.issued_at DESC, s.sanction_id DESC
            LIMIT ?
            """;
    private static final String PUBLIC_CASE_SQL = PUBLIC_SELECT + """
              AND c.case_id = ?
            ORDER BY s.issued_at DESC, s.sanction_id DESC
            LIMIT 1
            """;

    private final DataSource dataSource;

    JdbcPublicPunishmentRegistry(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("A data source is required");
        }
        this.dataSource = dataSource;
    }

    PublicPunishmentPage listPublic(
            PublicPunishmentFilter filter,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        validatePublicQuery(filter, encodedCursor, limit, now);
        Optional<WebsitePunishmentProjection.Cursor> cursor =
                WebsitePunishmentProjection.decodeCursor(encodedCursor);
        String sql = publicListSql(filter, cursor.isPresent());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int nextIndex = bindCursor(statement, cursor);
            statement.setInt(nextIndex, Math.addExact(limit, CURSOR_LOOKAHEAD));
            return page(readRows(statement, now), limit);
        } catch (SQLException | IllegalArgumentException exception) {
            throw persistence("Unable to read the public punishment registry", exception);
        }
    }

    List<PublicPunishment> searchPublic(String query, int limit, Instant now) {
        String normalized = normalizeSearch(query, limit, now);
        String lower = normalized.toLowerCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH_SQL)) {
            statement.setString(1, normalized.toUpperCase(Locale.ROOT));
            statement.setString(2, lower);
            statement.setString(3, lower);
            statement.setInt(4, limit);
            return readRows(statement, now).stream().map(PublicRow::punishment).toList();
        } catch (SQLException | IllegalArgumentException exception) {
            throw persistence("Unable to search the public punishment registry", exception);
        }
    }

    Optional<PublicPunishment> publicCase(CaseId caseId, Instant now) {
        if (caseId == null || now == null) {
            throw invalid("INVALID_CASE_ID", "The case ID is invalid");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PUBLIC_CASE_SQL)) {
            statement.setString(1, caseId.value());
            List<PublicRow> rows = readRows(statement, now);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().punishment());
        } catch (SQLException | IllegalArgumentException exception) {
            throw persistence("Unable to read the public case", exception);
        }
    }

    private static void validatePublicQuery(
            PublicPunishmentFilter filter,
            Optional<String> encodedCursor,
            int limit,
            Instant now
    ) {
        if (filter == null || encodedCursor == null || now == null) {
            throw invalid("INVALID_PUBLIC_QUERY", "The public punishment query is invalid");
        }
        if (limit < MIN_PUBLIC_LIMIT || limit > MAX_PUBLIC_LIMIT) {
            throw invalid("INVALID_PUBLIC_QUERY", "The public punishment query is invalid");
        }
    }

    private static String normalizeSearch(String query, int limit, Instant now) {
        if (query == null || now == null || limit < MIN_PUBLIC_LIMIT || limit > MAX_PUBLIC_LIMIT) {
            throw invalid("INVALID_SEARCH", "The punishment search is invalid");
        }
        String normalized = query.trim();
        if (normalized.length() < MIN_SEARCH_LENGTH || normalized.length() > MAX_SEARCH_LENGTH
                || !normalized.matches(SEARCH_PATTERN)) {
            throw invalid("INVALID_SEARCH", "Search for a username or case ID");
        }
        return normalized;
    }

    private static String publicListSql(PublicPunishmentFilter filter, boolean hasCursor) {
        String filterCondition = switch (filter) {
            case ALL -> "";
            case BAN -> " AND s.sanction_type IN ('BAN', 'NETWORK_BAN', 'NETWORK_IDENTITY_BAN')";
            case MUTE -> " AND s.sanction_type = 'MUTE'";
            case WARNING -> " AND s.sanction_type = 'WARNING'";
        };
        String cursorCondition = hasCursor
                ? " AND (s.issued_at < ? OR (s.issued_at = ? AND s.sanction_id < ?))"
                : "";
        return PUBLIC_SELECT + filterCondition + cursorCondition
                + " ORDER BY s.issued_at DESC, s.sanction_id DESC LIMIT ?";
    }

    private static int bindCursor(
            PreparedStatement statement,
            Optional<WebsitePunishmentProjection.Cursor> cursor
    ) throws SQLException {
        if (cursor.isEmpty()) {
            return 1;
        }
        WebsitePunishmentProjection.Cursor value = cursor.orElseThrow();
        Timestamp issuedAt = Timestamp.from(value.issuedAt());
        statement.setTimestamp(1, issuedAt);
        statement.setTimestamp(2, issuedAt);
        statement.setBytes(3, UuidBytes.toBytes(value.sanctionId()));
        return 4;
    }

    private static List<PublicRow> readRows(PreparedStatement statement, Instant now) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<PublicRow> rows = new ArrayList<>();
            while (result.next()) {
                rows.add(readPublicRow(result, now));
            }
            return List.copyOf(rows);
        }
    }

    private static PublicPunishmentPage page(List<PublicRow> rows, int limit) {
        if (rows.size() <= limit) {
            return new PublicPunishmentPage(
                    rows.stream().map(PublicRow::punishment).toList(),
                    Optional.empty()
            );
        }
        List<PublicRow> visibleRows = rows.subList(0, limit);
        PublicRow last = visibleRows.getLast();
        String cursor = WebsitePunishmentProjection.encodeCursor(last.issuedAt(), last.sanctionId());
        return new PublicPunishmentPage(
                visibleRows.stream().map(PublicRow::punishment).toList(),
                Optional.of(cursor)
        );
    }

    private static PublicRow readPublicRow(ResultSet result, Instant now) throws SQLException {
        UUID sanctionId = UuidBytes.fromBytes(result.getBytes("sanction_id"));
        Instant issuedAt = result.getTimestamp("issued_at").toInstant();
        Timestamp expirationValue = result.getTimestamp("expiration_at");
        Instant expiration = expirationValue == null ? null : expirationValue.toInstant();
        PublicPunishmentState state = WebsitePunishmentProjection.publicState(
                result.getString("status"), expiration, now
        );
        String sanctionType = result.getString("sanction_type");
        PublicPunishment punishment = new PublicPunishment(
                result.getString("current_username"),
                WebsitePunishmentProjection.publicType(sanctionType),
                result.getString("sanction_family"),
                result.getString("public_reason"),
                issuedAt,
                Optional.ofNullable(expiration),
                remainingSeconds(expiration, state, now),
                state,
                new CaseId(result.getString("case_id")),
                WebsitePunishmentProjection.appealAvailable(
                        state,
                        sanctionType,
                        result.getString("code_status")
                )
        );
        return new PublicRow(sanctionId, issuedAt, punishment);
    }

    private static OptionalLong remainingSeconds(
            Instant expiration,
            PublicPunishmentState state,
            Instant now
    ) {
        if (expiration == null) {
            return OptionalLong.empty();
        }
        long seconds = state == PublicPunishmentState.ACTIVE
                ? Math.max(0, Duration.between(now, expiration).toSeconds())
                : 0;
        return OptionalLong.of(seconds);
    }

    private static WebsiteModerationException invalid(String code, String message) {
        return new WebsiteModerationException(WebsiteModerationException.Kind.INVALID, code, message);
    }

    private static ModerationPersistenceException persistence(String message, Exception exception) {
        return exception instanceof ModerationPersistenceException persistenceException
                ? persistenceException
                : new ModerationPersistenceException(message, exception);
    }

    private record PublicRow(UUID sanctionId, Instant issuedAt, PublicPunishment punishment) {
    }
}
