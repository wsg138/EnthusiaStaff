package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class JdbcCaseLookup implements CaseLookup {
    private final DataSource dataSource;

    public JdbcCaseLookup(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<CaseId> latestCase(UUID targetId, Set<SanctionType> types, boolean activeOnly) {
        if (targetId == null || types == null || types.isEmpty() || types.size() > 32) {
            throw new IllegalArgumentException("target and bounded sanction types are required");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(types.size(), "?"));
        String active = activeOnly ? " AND s.status IN ('PENDING', 'ACTIVE', 'APPLIED')" : "";
        String sql = """
                SELECT c.case_id FROM cases c
                JOIN sanctions s ON s.case_id = c.case_id
                WHERE c.target_id = ? AND s.sanction_type IN (%s)%s
                  AND c.state <> 'FULLY_OVERTURNED'
                ORDER BY c.issued_at DESC LIMIT 1
                """.formatted(placeholders, active);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            int index = 2;
            for (SanctionType type : types) {
                statement.setString(index++, type.name());
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(new CaseId(result.getString(1))) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to resolve a player's latest case", exception);
        }
    }

    @Override
    public Optional<UUID> target(CaseId caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId must be present");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT target_id FROM cases WHERE case_id = ?"
             )) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(UuidBytes.fromBytes(result.getBytes("target_id")))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to resolve the case target", exception);
        }
    }

    @Override
    public boolean containsSanction(
            CaseId caseId,
            Set<SanctionType> types,
            boolean activeOnly
    ) {
        if (caseId == null || types == null || types.isEmpty() || types.size() > 32) {
            throw new IllegalArgumentException("case and bounded sanction types are required");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(types.size(), "?"));
        String active = activeOnly ? " AND status IN ('PENDING', 'ACTIVE', 'APPLIED')" : "";
        String sql = "SELECT 1 FROM sanctions WHERE case_id = ? AND sanction_type IN ("
                + placeholders + ')' + active + " LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId.value());
            int index = 2;
            for (SanctionType type : types) {
                statement.setString(index++, type.name());
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to resolve case sanctions", exception);
        }
    }

    @Override
    public boolean exists(CaseId caseId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM cases WHERE case_id = ?")) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to resolve case", exception);
        }
    }
}
