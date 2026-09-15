package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishment;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.EvasionCandidate;
import net.enthusia.staff.domain.ports.DiscordInvestigationStore.PunishmentObservation;

final class JdbcDiscordInvestigationSource {
    private static final String D07_RESOURCE_TYPE = "D07_DISCORD_PUNISHMENT";

    private final DataSource dataSource;
    private final DiscordPunishmentJsonCodec punishmentCodec = new DiscordPunishmentJsonCodec();

    JdbcDiscordInvestigationSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    List<PunishmentObservation> punishmentObservations(int limit) {
        validateLimit(limit);
        return JdbcTransactionSupport.execute(dataSource, "Unable to read Discord punishment observations", connection -> {
            List<PunishmentObservation> observations = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT r.desired_state_json, r.revision, r.updated_at
                    FROM discord_reconciliation_state r
                    LEFT JOIN discord_investigation_cases investigation
                      ON investigation.punishment_id = UNHEX(REPLACE(r.resource_id, '-', ''))
                    WHERE r.resource_type = ?
                      AND (investigation.case_id IS NULL OR investigation.source_revision < r.revision)
                    ORDER BY r.updated_at, r.reconciliation_key
                    LIMIT ?
                    """)) {
                statement.setString(1, D07_RESOURCE_TYPE);
                statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        DiscordPunishment punishment = punishmentCodec.decode(rows.getString("desired_state_json"));
                        observations.add(observation(punishment, rows));
                    }
                }
            }
            return List.copyOf(observations);
        });
    }

    List<EvasionCandidate> evasionCandidates(int limit) {
        validateLimit(limit);
        return JdbcTransactionSupport.execute(dataSource, "Unable to read linked-alt candidates", connection ->
                readEvasionCandidates(connection, limit));
    }

    private List<EvasionCandidate> readEvasionCandidates(Connection connection, int limit) throws SQLException {
        List<EvasionCandidate> candidates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT r.desired_state_json, membership.player_id, player.current_server, player.revision
                FROM discord_reconciliation_state r
                JOIN moderation_enforcement_targets target
                  ON target.target_id = UNHEX(REPLACE(r.resource_id, '-', ''))
                JOIN moderation_subject_minecraft_identities membership
                  ON membership.subject_id = target.subject_id
                JOIN players player ON player.player_id = membership.player_id
                LEFT JOIN discord_evasion_alerts alert
                  ON alert.punishment_id = target.target_id
                 AND alert.triggering_minecraft_player_id = membership.player_id
                 AND alert.player_revision = player.revision
                WHERE r.resource_type = ?
                  AND r.state IN ('APPLIED', 'PENDING_REMOVE', 'RETRY_REMOVE', 'FAILED_REMOVE')
                  AND JSON_UNQUOTE(JSON_EXTRACT(r.desired_state_json, '$.type')) = 'BAN'
                  AND JSON_UNQUOTE(JSON_EXTRACT(r.desired_state_json, '$.externalApplied')) = 'true'
                  AND player.current_server IS NOT NULL
                  AND alert.alert_id IS NULL
                ORDER BY r.updated_at, r.reconciliation_key, membership.player_id
                LIMIT ?
                """)) {
            statement.setString(1, D07_RESOURCE_TYPE);
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    addCandidate(rows, candidates);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private void addCandidate(ResultSet rows, List<EvasionCandidate> candidates) throws SQLException {
        DiscordPunishment punishment = punishmentCodec.decode(rows.getString("desired_state_json"));
        if (!isBanEvasionSignal(punishment)) {
            return;
        }
        candidates.add(new EvasionCandidate(
                punishment.subjectId(),
                punishment.punishmentId(),
                UuidBytes.fromBytes(rows.getBytes("player_id")),
                rows.getString("current_server"),
                rows.getLong("revision")
        ));
    }

    private static boolean isBanEvasionSignal(DiscordPunishment punishment) {
        return punishment.externalApplied()
                && !punishment.state().terminal()
                && punishment.intent().type() == DiscordConsequenceType.BAN;
    }

    private static PunishmentObservation observation(DiscordPunishment punishment, ResultSet rows) throws SQLException {
        return new PunishmentObservation(
                punishment.punishmentId(),
                punishment.subjectId(),
                punishment.issuer().id(),
                punishment.intent().publicReason(),
                punishment.state(),
                punishment.expiresAt(),
                rows.getLong("revision"),
                rows.getTimestamp("updated_at").toInstant()
        );
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("investigation source query limit is invalid");
        }
    }
}
