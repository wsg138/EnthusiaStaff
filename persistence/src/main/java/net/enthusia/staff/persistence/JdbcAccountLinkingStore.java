package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLink;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.AccountLinkingStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

public final class JdbcAccountLinkingStore implements AccountLinkingStore {
    private static final long CLAIM_GRACE_SECONDS = 60L;
    private final DataSource dataSource;

    public JdbcAccountLinkingStore(DataSource dataSource) {
        this.dataSource = require(dataSource, "dataSource");
    }

    @Override
    public void issueFromDiscord(
            DiscordUserId discordUserId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        require(discordUserId, "discordUserId");
        issue(Direction.DISCORD_TO_MINECRAFT, discordUserId, null, codeHash, createdAt, expiresAt);
    }

    @Override
    public void issueFromMinecraft(
            UUID minecraftPlayerId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        require(minecraftPlayerId, "minecraftPlayerId");
        issue(Direction.MINECRAFT_TO_DISCORD, null, minecraftPlayerId, codeHash, createdAt, expiresAt);
    }

    @Override
    public CodeClaim claim(
            String codeHash,
            Direction expectedDirection,
            String operationKey,
            Instant now
    ) {
        validateHash(codeHash);
        require(expectedDirection, "expectedDirection");
        validateKey(operationKey);
        require(now, "now");
        return JdbcTransactionSupport.execute(
                dataSource,
                "Unable to claim account-link code",
                connection -> claim(connection, codeHash, expectedDirection, operationKey, now)
        );
    }

    @Override
    public void consume(UUID codeId, String operationKey, Instant consumedAt) {
        require(codeId, "codeId");
        validateKey(operationKey);
        require(consumedAt, "consumedAt");
        JdbcTransactionSupport.execute(dataSource, "Unable to consume account-link code", connection -> {
            StoredCode stored = codeById(connection, codeId, true);
            if (stored == null) {
                throw new SQLException("account-link code does not exist");
            }
            if (stored.state().equals("CONSUMED")) {
                if (!operationKey.equals(stored.consumedOperationKey())) {
                    throw new SQLException("account-link code was consumed by a different operation");
                }
                return null;
            }
            if (!stored.state().equals("CLAIMED") || !operationKey.equals(stored.claimOperationKey())) {
                throw new SQLException("account-link code is not claimed by this operation");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_link_codes
                    SET state = 'CONSUMED', consumed_operation_key = ?, consumed_at = ?,
                        claim_operation_key = NULL, claim_until = NULL, revision = revision + 1
                    WHERE code_id = ? AND state = 'CLAIMED' AND claim_operation_key = ?
                    """)) {
                statement.setString(1, operationKey);
                statement.setTimestamp(2, Timestamp.from(consumedAt));
                statement.setBytes(3, UuidBytes.toBytes(codeId));
                statement.setString(4, operationKey);
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "account-link code was not consumed");
            }
            return null;
        });
    }

    @Override
    public void release(UUID codeId, String operationKey, Instant now) {
        require(codeId, "codeId");
        validateKey(operationKey);
        require(now, "now");
        JdbcTransactionSupport.execute(dataSource, "Unable to release account-link code", connection -> {
            StoredCode stored = codeById(connection, codeId, true);
            if (stored == null || !stored.state().equals("CLAIMED")
                    || !operationKey.equals(stored.claimOperationKey())) {
                return null;
            }
            String next = now.isBefore(stored.expiresAt()) ? "ACTIVE" : "EXPIRED";
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE discord_link_codes
                    SET state = ?, claim_operation_key = NULL, claim_until = NULL, revision = revision + 1
                    WHERE code_id = ? AND state = 'CLAIMED' AND claim_operation_key = ?
                    """)) {
                statement.setString(1, next);
                statement.setBytes(2, UuidBytes.toBytes(codeId));
                statement.setString(3, operationKey);
                JdbcTransactionSupport.requireOptionalSingleUpdate(statement.executeUpdate(), "unexpected account-link release count");
            }
            return null;
        });
    }

    @Override
    public List<VersionedLink> historyForMinecraft(UUID minecraftPlayerId) {
        require(minecraftPlayerId, "minecraftPlayerId");
        return history("minecraft_player_id = ?", statement -> statement.setBytes(1, UuidBytes.toBytes(minecraftPlayerId)));
    }

    @Override
    public List<VersionedLink> historyForDiscord(DiscordUserId discordUserId) {
        require(discordUserId, "discordUserId");
        return history("discord_user_id = ?", statement -> statement.setBigDecimal(1, discordId(discordUserId)));
    }

    private void issue(
            Direction direction,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        validateHash(codeHash);
        require(createdAt, "createdAt");
        require(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("link-code expiration must follow creation");
        }
        JdbcTransactionSupport.execute(dataSource, "Unable to issue account-link code", connection -> {
            lockInitiator(connection, direction, discordUserId, minecraftPlayerId);
            StoredCode claimed = activeClaimForInitiator(connection, direction, discordUserId, minecraftPlayerId);
            if (claimed != null && claimed.claimUntil() != null && claimed.claimUntil().isAfter(createdAt)) {
                throw new SQLException("an account-link completion is already in progress");
            }
            supersedePrior(connection, direction, discordUserId, minecraftPlayerId, createdAt);
            UUID codeId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO discord_link_codes(
                        code_id, code_hash, direction, initiator_discord_user_id,
                        initiator_minecraft_player_id, state, created_at, expires_at, revision
                    ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0)
                    """)) {
                statement.setBytes(1, UuidBytes.toBytes(codeId));
                statement.setString(2, codeHash);
                statement.setString(3, direction.name());
                if (discordUserId == null) {
                    statement.setNull(4, java.sql.Types.DECIMAL);
                } else {
                    statement.setBigDecimal(4, discordId(discordUserId));
                }
                if (minecraftPlayerId == null) {
                    statement.setNull(5, java.sql.Types.BINARY);
                } else {
                    statement.setBytes(5, UuidBytes.toBytes(minecraftPlayerId));
                }
                statement.setTimestamp(6, Timestamp.from(createdAt));
                statement.setTimestamp(7, Timestamp.from(expiresAt));
                JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "account-link code was not inserted");
            }
            return null;
        });
    }

    private static CodeClaim claim(
            Connection connection,
            String codeHash,
            Direction expectedDirection,
            String operationKey,
            Instant now
    ) throws SQLException {
        StoredCode stored = codeByHash(connection, codeHash, true);
        if (stored == null || stored.direction() != expectedDirection) {
            throw new SQLException("account-link code is invalid");
        }
        if (stored.state().equals("CONSUMED")) {
            if (!operationKey.equals(stored.consumedOperationKey())) {
                throw new SQLException("account-link code was already consumed");
            }
            return stored.toClaim(operationKey, true);
        }
        if (stored.state().equals("SUPERSEDED")) {
            throw new SQLException("account-link code was replaced");
        }
        if (stored.state().equals("CLAIMED") && operationKey.equals(stored.claimOperationKey())) {
            // Same deterministic completion may repair a crash that occurred after the link commit,
            // even if the five-minute issuance window has elapsed in the meantime.
            return stored.toClaim(operationKey, false);
        }
        if (stored.state().equals("CLAIMED")
                && stored.claimUntil() != null
                && stored.claimUntil().isAfter(now)) {
            throw new SQLException("account-link code completion is already in progress");
        }
        if (stored.state().equals("EXPIRED") || !now.isBefore(stored.expiresAt())) {
            expire(connection, stored.codeId());
            throw new SQLException("account-link code expired");
        }
        if (!stored.state().equals("ACTIVE")) {
            throw new SQLException("account-link code is unavailable");
        }
        Instant claimUntil = stored.expiresAt().plusSeconds(CLAIM_GRACE_SECONDS);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_link_codes
                SET state = 'CLAIMED', claim_operation_key = ?, claim_until = ?, revision = revision + 1
                WHERE code_id = ? AND state = 'ACTIVE'
                """)) {
            statement.setString(1, operationKey);
            statement.setTimestamp(2, Timestamp.from(claimUntil));
            statement.setBytes(3, UuidBytes.toBytes(stored.codeId()));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "account-link code was not claimed");
        }
        return stored.toClaim(operationKey, false);
    }

    private static void lockInitiator(
            Connection connection,
            Direction direction,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId
    ) throws SQLException {
        if (direction == Direction.DISCORD_TO_MINECRAFT) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT subject_id FROM moderation_subject_discord_identities
                    WHERE discord_user_id = ? FOR UPDATE
                    """)) {
                statement.setBigDecimal(1, discordId(discordUserId));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Discord initiator has no moderation identity");
                    }
                }
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT subject_id FROM moderation_subject_minecraft_identities
                WHERE player_id = ? FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(minecraftPlayerId));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Minecraft initiator has no moderation identity");
                }
            }
        }
    }

    private static StoredCode activeClaimForInitiator(
            Connection connection,
            Direction direction,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId
    ) throws SQLException {
        String owner = direction == Direction.DISCORD_TO_MINECRAFT
                ? "initiator_discord_user_id = ?" : "initiator_minecraft_player_id = ?";
        String sql = "SELECT * FROM discord_link_codes WHERE direction = ? AND " + owner
                + " AND state = 'CLAIMED' ORDER BY created_at DESC LIMIT 1 FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, direction.name());
            if (direction == Direction.DISCORD_TO_MINECRAFT) {
                statement.setBigDecimal(2, discordId(discordUserId));
            } else {
                statement.setBytes(2, UuidBytes.toBytes(minecraftPlayerId));
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCode(result) : null;
            }
        }
    }

    private static void supersedePrior(
            Connection connection,
            Direction direction,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            Instant now
    ) throws SQLException {
        String owner = direction == Direction.DISCORD_TO_MINECRAFT
                ? "initiator_discord_user_id = ?" : "initiator_minecraft_player_id = ?";
        String sql = "UPDATE discord_link_codes SET state = 'SUPERSEDED', superseded_at = ?, "
                + "claim_operation_key = NULL, claim_until = NULL, revision = revision + 1 "
                + "WHERE direction = ? AND " + owner + " AND state IN ('ACTIVE','CLAIMED')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setString(2, direction.name());
            if (direction == Direction.DISCORD_TO_MINECRAFT) {
                statement.setBigDecimal(3, discordId(discordUserId));
            } else {
                statement.setBytes(3, UuidBytes.toBytes(minecraftPlayerId));
            }
            statement.executeUpdate();
        }
    }

    private static void expire(Connection connection, UUID codeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_link_codes
                SET state = 'EXPIRED', claim_operation_key = NULL, claim_until = NULL, revision = revision + 1
                WHERE code_id = ? AND state IN ('ACTIVE','CLAIMED')
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(codeId));
            statement.executeUpdate();
        }
    }

    private List<VersionedLink> history(String predicate, SqlBinder binder) {
        return JdbcTransactionSupport.execute(dataSource, "Unable to read account-link history", connection -> {
            String sql = "SELECT link_id, subject_id, discord_user_id, minecraft_player_id, linked_at, "
                    + "unlinked_at, source, revision FROM discord_minecraft_links WHERE " + predicate
                    + " ORDER BY linked_at DESC, link_id DESC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet result = statement.executeQuery()) {
                    List<VersionedLink> links = new ArrayList<>();
                    while (result.next()) {
                        links.add(readLink(result));
                    }
                    return List.copyOf(links);
                }
            }
        });
    }

    private static StoredCode codeByHash(Connection connection, String hash, boolean lock) throws SQLException {
        String sql = "SELECT * FROM discord_link_codes WHERE code_hash = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCode(result) : null;
            }
        }
    }

    private static StoredCode codeById(Connection connection, UUID codeId, boolean lock) throws SQLException {
        String sql = "SELECT * FROM discord_link_codes WHERE code_id = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, UuidBytes.toBytes(codeId));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCode(result) : null;
            }
        }
    }

    private static StoredCode readCode(ResultSet result) throws SQLException {
        BigDecimal discord = result.getBigDecimal("initiator_discord_user_id");
        byte[] minecraft = result.getBytes("initiator_minecraft_player_id");
        Timestamp claimUntil = result.getTimestamp("claim_until");
        return new StoredCode(
                UuidBytes.fromBytes(result.getBytes("code_id")),
                Direction.valueOf(result.getString("direction")),
                discord == null ? Optional.empty() : Optional.of(discordUserId(discord)),
                minecraft == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(minecraft)),
                result.getString("state"),
                result.getTimestamp("expires_at").toInstant(),
                result.getString("claim_operation_key"),
                claimUntil == null ? null : claimUntil.toInstant(),
                result.getString("consumed_operation_key")
        );
    }

    private static VersionedLink readLink(ResultSet result) throws SQLException {
        Timestamp unlinked = result.getTimestamp("unlinked_at");
        return new VersionedLink(
                UuidBytes.fromBytes(result.getBytes("link_id")),
                new ModerationSubjectId(UuidBytes.fromBytes(result.getBytes("subject_id"))),
                new DiscordMinecraftLink(
                        discordUserId(result.getBigDecimal("discord_user_id")),
                        UuidBytes.fromBytes(result.getBytes("minecraft_player_id")),
                        result.getTimestamp("linked_at").toInstant(),
                        unlinked == null ? Optional.empty() : Optional.of(unlinked.toInstant()),
                        DiscordMinecraftLinkSource.valueOf(result.getString("source"))
                ),
                result.getLong("revision"),
                false
        );
    }

    private static BigDecimal discordId(DiscordUserId userId) {
        return new BigDecimal(userId.value());
    }

    private static DiscordUserId discordUserId(BigDecimal value) {
        return new DiscordUserId(value.toBigIntegerExact().toString());
    }

    private static void validateHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("codeHash must be a lowercase SHA-256 hex digest");
        }
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("operationKey must be nonblank and at most 128 characters");
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record StoredCode(
            UUID codeId,
            Direction direction,
            Optional<DiscordUserId> discordInitiator,
            Optional<UUID> minecraftInitiator,
            String state,
            Instant expiresAt,
            String claimOperationKey,
            Instant claimUntil,
            String consumedOperationKey
    ) {
        CodeClaim toClaim(String operationKey, boolean consumed) {
            return new CodeClaim(
                    codeId, direction, discordInitiator, minecraftInitiator,
                    expiresAt, operationKey, consumed);
        }
    }
}
