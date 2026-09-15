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
                    SELECT desired_state_json, revision, updated_at
                    FROM discord_reconciliation_state
                    WHERE resource_type = ?
                    ORDER BY updated_at, reconciliation_key
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
        return JdbcTransactionSupport.execute(dataSource, "Unable to read linked-alt candidates", connection -> {
            List<EvasionCandidate> candidates = new ArrayList<>();
            for (DiscordPunishment punishment : activeBanPunishments(connection, limit)) {
                addOnlineLinks(connection, punishment, candidates, limit);
                if (candidates.size() >= limit) {
                    break;
                }
            }
            return List.copyOf(candidates);
        });
    }

    private List<DiscordPunishment> activeBanPunishments(Connection connection, int limit) throws SQLException {
        List<DiscordPunishment> punishments = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT desired_state_json
                FROM discord_reconciliation_state
                WHERE resource_type = ?
                  AND state IN ('APPLIED', 'PENDING_REMOVE', 'RETRY_REMOVE', 'FAILED_REMOVE')
                ORDER BY updated_at, reconciliation_key
                LIMIT ?
                """)) {
            statement.setString(1, D07_RESOURCE_TYPE);
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    DiscordPunishment punishment = punishmentCodec.decode(rows.getString("desired_state_json"));
                    if (isBanEvasionSignal(punishment)) {
                        punishments.add(punishment);
                    }
                }
            }
        }
        return punishments;
    }

    private static boolean isBanEvasionSignal(DiscordPunishment punishment) {
        return punishment.externalApplied()
                && !punishment.state().terminal()
                && punishment.intent().type() == DiscordConsequenceType.BAN;
    }

    private static void addOnlineLinks(
            Connection connection,
            DiscordPunishment punishment,
            List<EvasionCandidate> candidates,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT membership.player_id, player.current_server, player.revision
                FROM moderation_subject_minecraft_identities membership
                JOIN players player ON player.player_id = membership.player_id
                WHERE membership.subject_id = ? AND player.current_server IS NOT NULL
                ORDER BY membership.player_id
                LIMIT ?
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(punishment.subjectId().value()));
            statement.setInt(2, Math.max(1, limit - candidates.size()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next() && candidates.size() < limit) {
                    candidates.add(new EvasionCandidate(
                            punishment.subjectId(),
                            punishment.punishmentId(),
                            UuidBytes.fromBytes(rows.getBytes("player_id")),
                            rows.getString("current_server"),
                            rows.getLong("revision")
                    ));
                }
            }
        }
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
