package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class JdbcSanctionLookup implements SanctionLookup {
    private static final int MAX_TYPES = 16;

    private final DataSource dataSource;

    public JdbcSanctionLookup(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must be present");
        }
        this.dataSource = dataSource;
    }

    @Override
    public List<ActiveSanction> activeFor(UUID playerId, Set<SanctionType> types, Instant now) {
        if (playerId == null || types == null || types.isEmpty() || types.size() > MAX_TYPES || now == null) {
            throw new IllegalArgumentException("player, bounded sanction types, and time must be present");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(types.size(), "?"));
        String sql = """
                SELECT s.sanction_id, s.case_id, s.target_id, s.sanction_type, c.public_reason,
                       s.issued_at, s.expiration_at, s.inherited_from
                FROM sanctions s
                JOIN cases c ON c.case_id = s.case_id
                WHERE s.target_id = ?
                  AND s.status = 'ACTIVE'
                  AND (s.expiration_at IS NULL OR s.expiration_at > ?)
                  AND s.sanction_type IN (%s)
                  AND c.state <> 'FULLY_OVERTURNED'
                ORDER BY s.issued_at DESC
                """.formatted(placeholders);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(playerId));
            statement.setTimestamp(2, Timestamp.from(now));
            int index = 3;
            for (SanctionType type : types) {
                statement.setString(index++, type.name());
            }
            try (ResultSet results = statement.executeQuery()) {
                List<ActiveSanction> sanctions = new ArrayList<>();
                while (results.next()) {
                    sanctions.add(read(results));
                }
                return List.copyOf(sanctions);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to read active sanctions", exception);
        }
    }

    private static ActiveSanction read(ResultSet result) throws SQLException {
        Timestamp expiration = result.getTimestamp("expiration_at");
        byte[] inherited = result.getBytes("inherited_from");
        return new ActiveSanction(
                UuidBytes.fromBytes(result.getBytes("sanction_id")),
                new CaseId(result.getString("case_id")),
                UuidBytes.fromBytes(result.getBytes("target_id")),
                SanctionType.valueOf(result.getString("sanction_type")),
                result.getString("public_reason"),
                result.getTimestamp("issued_at").toInstant(),
                expiration == null ? Optional.empty() : Optional.of(expiration.toInstant()),
                inherited == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(inherited))
        );
    }
}
