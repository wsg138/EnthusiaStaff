package net.enthusia.staff.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentPlan;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentResult;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.ports.CrossPlatformPunishmentStore;
import net.enthusia.staff.domain.ports.DiscordPunishmentRepository.StoredPunishment;

/** V1/V19-backed atomic intent persistence for D08. */
public final class JdbcCrossPlatformPunishmentStore implements CrossPlatformPunishmentStore {
    private final DataSource dataSource;
    private final JdbcModerationStore minecraft;
    private final JdbcDiscordPunishmentRepository discord;

    JdbcCrossPlatformPunishmentStore(
            DataSource dataSource,
            JdbcModerationStore minecraft,
            JdbcDiscordPunishmentRepository discord
    ) {
        if (dataSource == null || minecraft == null || discord == null) {
            throw new IllegalArgumentException("cross-platform store dependencies must be present");
        }
        this.dataSource = dataSource;
        this.minecraft = minecraft;
        this.discord = discord;
    }

    @Override
    public CrossPlatformPunishmentResult create(CrossPlatformPunishmentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("cross-platform punishment plan must be present");
        }
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to create cross-platform punishment intent",
                connection -> create(connection, plan)
        );
    }

    private CrossPlatformPunishmentResult create(
            Connection connection,
            CrossPlatformPunishmentPlan plan
    ) throws SQLException {
        requireMinecraftMembership(connection, plan);
        PunishmentResult.Accepted minecraftResult = minecraft.createPunishment(connection, plan.minecraft());
        requireExpectedCase(minecraftResult, plan);
        StoredPunishment discordResult = discord.create(
                connection, plan.discord(), plan.operationKey(), plan.minecraft().issuedAt()
        );
        requireConsistentReplay(minecraftResult.replayed(), discordResult.replayed());
        return new CrossPlatformPunishmentResult(
                minecraftResult.caseId(), discordResult.punishment().punishmentId(), minecraftResult.replayed()
        );
    }

    private static void requireMinecraftMembership(
            Connection connection,
            CrossPlatformPunishmentPlan plan
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM moderation_subject_minecraft_identities
                WHERE subject_id = ? AND player_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(plan.discord().subjectId().value()));
            statement.setBytes(2, UuidBytes.toBytes(plan.minecraft().targetId()));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Minecraft punishment target is not a member of the Discord subject");
                }
            }
        }
    }

    private static void requireExpectedCase(
            PunishmentResult.Accepted result,
            CrossPlatformPunishmentPlan plan
    ) throws SQLException {
        if (!result.caseId().equals(plan.minecraft().caseId())) {
            throw new SQLException("cross-platform idempotency key belongs to a different case");
        }
    }

    private static void requireConsistentReplay(boolean minecraftReplay, boolean discordReplay) throws SQLException {
        if (minecraftReplay != discordReplay) {
            throw new SQLException("cross-platform punishment state is incomplete or conflicted");
        }
    }
}
