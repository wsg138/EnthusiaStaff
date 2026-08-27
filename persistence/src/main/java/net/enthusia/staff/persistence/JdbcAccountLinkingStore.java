package net.enthusia.staff.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.AccountLinkingStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Durable hashed link-code state. Code consumption and authoritative linking commit atomically. */
public final class JdbcAccountLinkingStore implements AccountLinkingStore {
    private final DataSource dataSource;
    private final JdbcDeadlockRetry deadlockRetry;

    public JdbcAccountLinkingStore(DataSource dataSource) {
        this.dataSource = require(dataSource, "dataSource");
        this.deadlockRetry = new JdbcDeadlockRetry();
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
    public UUID minecraftInitiatorForCode(String codeHash, Instant now) {
        validateHash(codeHash);
        require(now, "now");
        CodeLookup lookup = deadlockRetry.execute(
                "Interrupted while retrying account-link code lookup",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to resolve Minecraft link-code initiator",
                        connection -> lookupMinecraftInitiator(connection, codeHash, now)
                )
        );
        if (lookup.expired()) {
            throw new ModerationPersistenceException("account-link code expired");
        }
        return lookup.minecraftPlayerId().orElseThrow();
    }

    private static CodeLookup lookupMinecraftInitiator(Connection connection, String codeHash, Instant now)
            throws SQLException {
        StoredCode stored = codeByHash(connection, codeHash, true);
        if (markExpiredIfNecessary(connection, stored, Direction.MINECRAFT_TO_DISCORD, now)) {
            return CodeLookup.expiredResult();
        }
        return CodeLookup.available(stored.minecraftInitiator().orElseThrow());
    }

    @Override
    public VersionedLink completeFromMinecraft(
            String codeHash,
            UUID minecraftPlayerId,
            String operationKey,
            Instant now
    ) {
        require(minecraftPlayerId, "minecraftPlayerId");
        return complete(new CompletionRequest(
                codeHash,
                Direction.DISCORD_TO_MINECRAFT,
                null,
                minecraftPlayerId,
                DiscordMinecraftLinkSource.DISCORD_CODE,
                operationKey,
                now
        ));
    }

    @Override
    public VersionedLink completeFromDiscord(
            String codeHash,
            DiscordUserId discordUserId,
            String operationKey,
            Instant now
    ) {
        require(discordUserId, "discordUserId");
        return complete(new CompletionRequest(
                codeHash,
                Direction.MINECRAFT_TO_DISCORD,
                discordUserId,
                null,
                DiscordMinecraftLinkSource.MINECRAFT_CODE,
                operationKey,
                now
        ));
    }

    @Override
    public List<VersionedLink> historyForMinecraft(UUID minecraftPlayerId) {
        require(minecraftPlayerId, "minecraftPlayerId");
        return JdbcAccountLinkHistoryReader.historyForMinecraft(dataSource, minecraftPlayerId);
    }

    @Override
    public List<VersionedLink> historyForDiscord(DiscordUserId discordUserId) {
        require(discordUserId, "discordUserId");
        return JdbcAccountLinkHistoryReader.historyForDiscord(dataSource, discordUserId);
    }

    private VersionedLink complete(CompletionRequest request) {
        validateHash(request.codeHash());
        validateKey(request.operationKey());
        require(request.now(), "now");
        CompletionResult result = deadlockRetry.execute(
                "Interrupted while retrying account-link code completion",
                () -> JdbcTransactionSupport.execute(
                        dataSource,
                        "Unable to complete account linking",
                        connection -> completeLocked(connection, request)
                )
        );
        if (result.expired()) {
            throw new ModerationPersistenceException("account-link code expired");
        }
        return result.link().orElseThrow();
    }

    private static CompletionResult completeLocked(Connection connection, CompletionRequest request)
            throws SQLException {
        StoredCode stored = codeByHash(connection, request.codeHash(), true);
        if (markExpiredIfNecessary(connection, stored, request.expectedDirection(), request.now())) {
            return CompletionResult.expiredResult();
        }
        CompletionParties parties = resolveCompletionParties(stored, request);
        if (stored.state().equals("CONSUMED")) {
            return replayConsumedCompletion(connection, stored, parties, request);
        }
        VersionedLink linked = linkCompletion(connection, parties, request);
        consumeCode(connection, stored.codeId(), request.operationKey(), request.now());
        return CompletionResult.linked(linked);
    }

    private static CompletionParties resolveCompletionParties(StoredCode stored, CompletionRequest request) {
        DiscordUserId discordUserId = request.expectedDirection() == Direction.DISCORD_TO_MINECRAFT
                ? stored.discordInitiator().orElseThrow()
                : request.completingDiscordUserId();
        UUID minecraftPlayerId = request.expectedDirection() == Direction.MINECRAFT_TO_DISCORD
                ? stored.minecraftInitiator().orElseThrow()
                : request.completingMinecraftPlayerId();
        return new CompletionParties(discordUserId, minecraftPlayerId);
    }

    private static CompletionResult replayConsumedCompletion(
            Connection connection,
            StoredCode stored,
            CompletionParties parties,
            CompletionRequest request
    ) throws SQLException {
        if (!request.operationKey().equals(stored.consumedOperationKey())) {
            throw new SQLException("account-link code was already consumed by a different completion");
        }
        return CompletionResult.linked(linkCompletion(connection, parties, request));
    }

    private static VersionedLink linkCompletion(
            Connection connection,
            CompletionParties parties,
            CompletionRequest request
    ) throws SQLException {
        return JdbcDiscordLinkRepository.link(
                connection,
                parties.discordUserId(),
                parties.minecraftPlayerId(),
                request.source(),
                request.operationKey(),
                request.now()
        );
    }

    private static void consumeCode(
            Connection connection,
            UUID codeId,
            String operationKey,
            Instant now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_link_codes
                SET state = 'CONSUMED', consumed_operation_key = ?, consumed_at = ?,
                    revision = revision + 1
                WHERE code_id = ? AND state = 'ACTIVE'
                """)) {
            statement.setString(1, operationKey);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setBytes(3, UuidBytes.toBytes(codeId));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "account-link code was not atomically consumed"
            );
        }
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
        deadlockRetry.execute(
                "Interrupted while retrying account-link code issuance",
                () -> JdbcTransactionSupport.execute(dataSource, "Unable to issue account-link code", connection -> {
                    lockInitiator(connection, direction, discordUserId, minecraftPlayerId);
                    supersedePrior(connection, direction, discordUserId, minecraftPlayerId, createdAt);
                    insertCode(connection, direction, discordUserId, minecraftPlayerId, codeHash, createdAt, expiresAt);
                    return null;
                })
        );
    }

    private static void insertCode(
            Connection connection,
            Direction direction,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt
    ) throws SQLException {
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
            setOptionalDiscordId(statement, 4, discordUserId);
            setOptionalMinecraftId(statement, 5, minecraftPlayerId);
            statement.setTimestamp(6, Timestamp.from(createdAt));
            statement.setTimestamp(7, Timestamp.from(expiresAt));
            JdbcTransactionSupport.requireSingleUpdate(statement.executeUpdate(), "account-link code was not inserted");
        }
    }

    private static void setOptionalDiscordId(PreparedStatement statement, int index, DiscordUserId discordUserId)
            throws SQLException {
        if (discordUserId == null) {
            statement.setNull(index, java.sql.Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, discordId(discordUserId));
        }
    }

    private static void setOptionalMinecraftId(PreparedStatement statement, int index, UUID minecraftPlayerId)
            throws SQLException {
        if (minecraftPlayerId == null) {
            statement.setNull(index, java.sql.Types.BINARY);
        } else {
            statement.setBytes(index, UuidBytes.toBytes(minecraftPlayerId));
        }
    }

    private static boolean markExpiredIfNecessary(
            Connection connection,
            StoredCode stored,
            Direction expectedDirection,
            Instant now
    ) throws SQLException {
        validateAvailableCode(stored, expectedDirection);
        if (stored.state().equals("CONSUMED")) {
            return false;
        }
        if (!now.isBefore(stored.expiresAt())) {
            expire(connection, stored.codeId());
            return true;
        }
        return false;
    }

    private static void validateAvailableCode(StoredCode stored, Direction expectedDirection) throws SQLException {
        if (stored == null || stored.direction() != expectedDirection) {
            throw new SQLException("account-link code is invalid");
        }
        if (stored.state().equals("SUPERSEDED")) {
            throw new SQLException("account-link code was replaced");
        }
        if (stored.state().equals("EXPIRED")) {
            throw new SQLException("account-link code expired");
        }
        if (!stored.state().equals("ACTIVE") && !stored.state().equals("CONSUMED")) {
            throw new SQLException("account-link code is unavailable");
        }
    }

    private static void lockInitiator(
            Connection connection,
            Direction direction,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId
    ) throws SQLException {
        if (direction == Direction.DISCORD_TO_MINECRAFT) {
            lockDiscordInitiator(connection, discordUserId);
        } else {
            lockMinecraftInitiator(connection, minecraftPlayerId);
        }
    }

    private static void lockDiscordInitiator(Connection connection, DiscordUserId discordUserId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT subject_id
                FROM moderation_subject_discord_identities
                WHERE discord_user_id = ?
                FOR UPDATE
                """)) {
            statement.setBigDecimal(1, discordId(discordUserId));
            requireInitiatorRow(statement, "Discord initiator has no moderation identity");
        }
    }

    private static void lockMinecraftInitiator(Connection connection, UUID minecraftPlayerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT subject_id
                FROM moderation_subject_minecraft_identities
                WHERE player_id = ?
                FOR UPDATE
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(minecraftPlayerId));
            requireInitiatorRow(statement, "Minecraft initiator has no moderation identity");
        }
    }

    private static void requireInitiatorRow(PreparedStatement statement, String error) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException(error);
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
        if (direction == Direction.DISCORD_TO_MINECRAFT) {
            supersedeDiscordCodes(connection, discordUserId, now);
        } else {
            supersedeMinecraftCodes(connection, minecraftPlayerId, now);
        }
    }

    private static void supersedeDiscordCodes(Connection connection, DiscordUserId discordUserId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_link_codes
                SET state = 'SUPERSEDED', superseded_at = ?, revision = revision + 1
                WHERE direction = 'DISCORD_TO_MINECRAFT'
                  AND initiator_discord_user_id = ?
                  AND state = 'ACTIVE'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBigDecimal(2, discordId(discordUserId));
            statement.executeUpdate();
        }
    }

    private static void supersedeMinecraftCodes(Connection connection, UUID minecraftPlayerId, Instant now)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_link_codes
                SET state = 'SUPERSEDED', superseded_at = ?, revision = revision + 1
                WHERE direction = 'MINECRAFT_TO_DISCORD'
                  AND initiator_minecraft_player_id = ?
                  AND state = 'ACTIVE'
                """)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setBytes(2, UuidBytes.toBytes(minecraftPlayerId));
            statement.executeUpdate();
        }
    }

    private static void expire(Connection connection, UUID codeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE discord_link_codes
                SET state = 'EXPIRED', revision = revision + 1
                WHERE code_id = ? AND state = 'ACTIVE'
                """)) {
            statement.setBytes(1, UuidBytes.toBytes(codeId));
            JdbcTransactionSupport.requireSingleUpdate(
                    statement.executeUpdate(),
                    "account-link code expiration lost its row-state race"
            );
        }
    }

    private static StoredCode codeByHash(Connection connection, String hash, boolean lock) throws SQLException {
        String sql = "SELECT code_id, direction, initiator_discord_user_id, initiator_minecraft_player_id, "
                + "state, expires_at, consumed_operation_key FROM discord_link_codes WHERE code_hash = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readCode(result) : null;
            }
        }
    }

    private static StoredCode readCode(ResultSet result) throws SQLException {
        BigDecimal discord = result.getBigDecimal("initiator_discord_user_id");
        byte[] minecraft = result.getBytes("initiator_minecraft_player_id");
        return new StoredCode(
                UuidBytes.fromBytes(result.getBytes("code_id")),
                Direction.valueOf(result.getString("direction")),
                discord == null ? Optional.empty() : Optional.of(discordUserId(discord)),
                minecraft == null ? Optional.empty() : Optional.of(UuidBytes.fromBytes(minecraft)),
                result.getString("state"),
                result.getTimestamp("expires_at").toInstant(),
                result.getString("consumed_operation_key")
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

    private record CompletionRequest(
            String codeHash,
            Direction expectedDirection,
            DiscordUserId completingDiscordUserId,
            UUID completingMinecraftPlayerId,
            DiscordMinecraftLinkSource source,
            String operationKey,
            Instant now
    ) {
    }

    private record CompletionParties(DiscordUserId discordUserId, UUID minecraftPlayerId) {
    }

    private record StoredCode(
            UUID codeId,
            Direction direction,
            Optional<DiscordUserId> discordInitiator,
            Optional<UUID> minecraftInitiator,
            String state,
            Instant expiresAt,
            String consumedOperationKey
    ) {
    }

    private record CompletionResult(Optional<VersionedLink> link, boolean expired) {
        private static CompletionResult linked(VersionedLink link) {
            return new CompletionResult(Optional.of(link), false);
        }

        private static CompletionResult expiredResult() {
            return new CompletionResult(Optional.empty(), true);
        }
    }

    private record CodeLookup(Optional<UUID> minecraftPlayerId, boolean expired) {
        private static CodeLookup available(UUID minecraftPlayerId) {
            return new CodeLookup(Optional.of(minecraftPlayerId), false);
        }

        private static CodeLookup expiredResult() {
            return new CodeLookup(Optional.empty(), true);
        }
    }
}
