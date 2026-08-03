package net.enthusia.staff.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.CaseState;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.casefile.OverturnRequestReview;
import net.enthusia.staff.domain.casefile.PunishmentStepReview;
import net.enthusia.staff.domain.casefile.SanctionReview;
import net.enthusia.staff.domain.ports.CaseReviewStore;
import net.enthusia.staff.domain.sanction.SanctionStatus;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class JdbcCaseReviewStore implements CaseReviewStore {
    private final DataSource dataSource;
    private final Clock clock;
    private final JdbcPunishmentRecommendationCodec recommendations;

    public JdbcCaseReviewStore(DataSource dataSource, Clock clock, ObjectMapper json) {
        if (dataSource == null || clock == null || json == null) {
            throw new IllegalArgumentException("case review dependencies must be present");
        }
        this.dataSource = dataSource;
        this.clock = clock;
        this.recommendations = new JdbcPunishmentRecommendationCodec(json);
    }

    @Override
    public Optional<CaseReview> find(CaseId caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId must be present");
        }
        try (Connection connection = dataSource.getConnection()) {
            return Optional.ofNullable(read(connection, caseId));
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read case review", exception);
        }
    }

    @Override
    public List<CaseReview> recent(UUID targetId, int limit) {
        if (targetId == null || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("target and a limit from 1 to 100 are required");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT case_id FROM cases WHERE target_id = ?
                     ORDER BY issued_at DESC LIMIT ?
                     """)) {
            statement.setBytes(1, UuidBytes.toBytes(targetId));
            statement.setInt(2, limit);
            List<CaseId> identifiers = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    identifiers.add(new CaseId(result.getString("case_id")));
                }
            }
            List<CaseReview> cases = new ArrayList<>();
            for (CaseId identifier : identifiers) {
                CaseReview review = read(connection, identifier);
                if (review != null) {
                    cases.add(review);
                }
            }
            return List.copyOf(cases);
        } catch (SQLException exception) {
            throw new ModerationPersistenceException("Unable to read recent case reviews", exception);
        }
    }

    private CaseReview read(Connection connection, CaseId caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.case_id, c.target_id, c.actor_id, c.actor_name, c.actor_rank,
                    c.public_reason, c.exact_reason_id, c.sanction_family,
                    c.internal_explanation, c.configuration_version, c.visibility,
                    c.state, c.issued_at, c.revision, p.raw_ordinal, p.effective_ordinal,
                    p.selected_ordinal, p.recency_bonus, p.step_label,
                    p.recommended_sanctions_json, p.escalation_contributes
                FROM cases c
                LEFT JOIN punishment_steps p ON p.case_id = c.case_id
                WHERE c.case_id = ?
                """)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new CaseReview(
                        caseId,
                        UuidBytes.fromBytes(result.getBytes("target_id")),
                        UuidBytes.fromBytes(result.getBytes("actor_id")),
                        result.getString("actor_name"),
                        result.getString("actor_rank"),
                        result.getString("public_reason"),
                        result.getString("exact_reason_id"),
                        result.getString("sanction_family"),
                        result.getString("internal_explanation"),
                        result.getString("configuration_version"),
                        CaseVisibility.valueOf(result.getString("visibility")),
                        CaseState.valueOf(result.getString("state")),
                        result.getTimestamp("issued_at").toInstant(),
                        result.getLong("revision"),
                        step(result),
                        sanctions(connection, caseId),
                        openOverturnRequest(connection, caseId, clock.instant())
                );
            }
        }
    }

    private Optional<PunishmentStepReview> step(ResultSet result) throws SQLException {
        Integer raw = result.getObject("raw_ordinal", Integer.class);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(new PunishmentStepReview(
                raw,
                result.getInt("effective_ordinal"),
                Optional.ofNullable(result.getObject("selected_ordinal", Integer.class)),
                result.getInt("recency_bonus"),
                result.getString("step_label"),
                recommendations.read(result.getString("recommended_sanctions_json")),
                result.getBoolean("escalation_contributes")
        ));
    }

    private static List<SanctionReview> sanctions(Connection connection, CaseId caseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sanction_id, sanction_type, status, issued_at, expiration_at, ended_at, revision
                FROM sanctions WHERE case_id = ? ORDER BY issued_at, sanction_id
                """)) {
            statement.setString(1, caseId.value());
            try (ResultSet result = statement.executeQuery()) {
                List<SanctionReview> sanctions = new ArrayList<>();
                while (result.next()) {
                    sanctions.add(new SanctionReview(
                            UuidBytes.fromBytes(result.getBytes("sanction_id")),
                            SanctionType.valueOf(result.getString("sanction_type")),
                            SanctionStatus.valueOf(result.getString("status")),
                            result.getTimestamp("issued_at").toInstant(),
                            optionalInstant(result.getTimestamp("expiration_at")),
                            optionalInstant(result.getTimestamp("ended_at")),
                            result.getLong("revision")
                    ));
                }
                return List.copyOf(sanctions);
            }
        }
    }

    private static Optional<OverturnRequestReview> openOverturnRequest(
            Connection connection,
            CaseId caseId,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT request_id, requested_by, explanation, requested_at, expires_at
                FROM punishment_overturn_requests
                WHERE case_id = ? AND state = 'OPEN' AND expires_at > ?
                ORDER BY requested_at DESC LIMIT 1
                """)) {
            statement.setString(1, caseId.value());
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OverturnRequestReview(
                        UuidBytes.fromBytes(result.getBytes("request_id")),
                        UuidBytes.fromBytes(result.getBytes("requested_by")),
                        result.getString("explanation"),
                        result.getTimestamp("requested_at").toInstant(),
                        result.getTimestamp("expires_at").toInstant()
                ));
            }
        }
    }

    private static Optional<Instant> optionalInstant(Timestamp timestamp) {
        return Optional.ofNullable(timestamp).map(Timestamp::toInstant);
    }
}
