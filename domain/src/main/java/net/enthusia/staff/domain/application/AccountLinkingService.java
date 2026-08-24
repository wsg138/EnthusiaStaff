package net.enthusia.staff.domain.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.AccountLinkingStore;
import net.enthusia.staff.domain.ports.AccountLinkingStore.Direction;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Two-direction self-service linking. Raw codes exist only in the returned challenge. */
public final class AccountLinkingService {
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 10;

    private final Clock clock;
    private final SecureRandom random;
    private final DiscordModerationPersistenceStore identities;
    private final AccountLinkingStore codes;
    private final MinecraftOnlineVerifier online;
    private final MainAccountSelectionService mainAccounts;

    public AccountLinkingService(
            Clock clock,
            SecureRandom random,
            DiscordModerationPersistenceStore identities,
            AccountLinkingStore codes,
            MinecraftOnlineVerifier online,
            MainAccountSelectionService mainAccounts
    ) {
        this.clock = require(clock, "clock");
        this.random = require(random, "random");
        this.identities = require(identities, "identities");
        this.codes = require(codes, "codes");
        this.online = require(online, "online");
        this.mainAccounts = require(mainAccounts, "mainAccounts");
    }

    public IssuedCode issueFromDiscord(DiscordUserId discordUserId) {
        require(discordUserId, "discordUserId");
        Instant now = clock.instant();
        identities.ensureDiscordSubject(discordUserId, now);
        String raw = generateCode();
        Instant expiresAt = now.plus(CODE_TTL);
        codes.issueFromDiscord(discordUserId, hash(raw), now, expiresAt);
        return new IssuedCode(raw, expiresAt, Direction.DISCORD_TO_MINECRAFT);
    }

    public IssuedCode issueFromMinecraft(UUID minecraftPlayerId) {
        requireOnline(minecraftPlayerId);
        Instant now = clock.instant();
        identities.ensureMinecraftSubject(minecraftPlayerId, now);
        String raw = generateCode();
        Instant expiresAt = now.plus(CODE_TTL);
        codes.issueFromMinecraft(minecraftPlayerId, hash(raw), now, expiresAt);
        return new IssuedCode(raw, expiresAt, Direction.MINECRAFT_TO_DISCORD);
    }

    public VersionedLink completeFromMinecraft(String rawCode, UUID minecraftPlayerId) {
        requireOnline(minecraftPlayerId);
        String codeHash = hash(normalize(rawCode));
        String operationKey = operationKey("mc", codeHash, minecraftPlayerId.toString());
        VersionedLink linked = codes.completeFromMinecraft(
                codeHash, minecraftPlayerId, operationKey, clock.instant());
        mainAccounts.evaluate(linked.link().discordUserId());
        return linked;
    }

    public VersionedLink completeFromDiscord(String rawCode, DiscordUserId discordUserId) {
        require(discordUserId, "discordUserId");
        String codeHash = hash(normalize(rawCode));
        UUID minecraftPlayerId = codes.minecraftInitiatorForCode(codeHash, clock.instant());
        requireOnline(minecraftPlayerId);
        String operationKey = operationKey("discord", codeHash, discordUserId.value());
        VersionedLink linked = codes.completeFromDiscord(
                codeHash, discordUserId, operationKey, clock.instant());
        mainAccounts.evaluate(discordUserId);
        return linked;
    }

    public boolean unlinkFromMinecraft(UUID minecraftPlayerId, boolean confirmed) {
        requireOnline(minecraftPlayerId);
        requireConfirmed(confirmed);
        Optional<VersionedLink> current = identities.currentLink(minecraftPlayerId);
        if (current.isEmpty()) {
            return false;
        }
        VersionedLink link = current.orElseThrow();
        DiscordUserId discordUserId = link.link().discordUserId();
        mainAccounts.prepareForUnlink(discordUserId, minecraftPlayerId);
        identities.unlink(
                discordUserId,
                minecraftPlayerId,
                link.revision(),
                "self-unlink:mc:" + link.linkId(),
                clock.instant()
        );
        mainAccounts.evaluate(discordUserId);
        return true;
    }

    public boolean unlinkFromDiscord(
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            boolean confirmed
    ) {
        require(discordUserId, "discordUserId");
        require(minecraftPlayerId, "minecraftPlayerId");
        requireConfirmed(confirmed);
        Optional<VersionedLink> current = identities.currentLink(minecraftPlayerId);
        if (current.isEmpty()) {
            return false;
        }
        VersionedLink link = current.orElseThrow();
        if (!link.link().discordUserId().equals(discordUserId)) {
            throw new IllegalStateException("Minecraft account is not linked to this Discord identity");
        }
        mainAccounts.prepareForUnlink(discordUserId, minecraftPlayerId);
        identities.unlink(
                discordUserId,
                minecraftPlayerId,
                link.revision(),
                "self-unlink:discord:" + link.linkId(),
                clock.instant()
        );
        mainAccounts.evaluate(discordUserId);
        return true;
    }

    private String generateCode() {
        char[] value = new char[CODE_LENGTH];
        for (int index = 0; index < value.length; index++) {
            value[index] = CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)];
        }
        return new String(value);
    }

    static String hash(String rawCode) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(rawCode).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String normalize(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("link code must be present");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("link code is too long");
        }
        return normalized;
    }

    private static String operationKey(String side, String codeHash, String completingIdentity) {
        return "d04-code:" + side + ":" + codeHash.substring(0, 32) + ":" + completingIdentity;
    }

    private void requireOnline(UUID minecraftPlayerId) {
        require(minecraftPlayerId, "minecraftPlayerId");
        if (!online.isOnline(minecraftPlayerId)) {
            throw new IllegalStateException("Minecraft account must be online to prove control");
        }
    }

    private static void requireConfirmed(boolean confirmed) {
        if (!confirmed) {
            throw new IllegalArgumentException("unlink must be explicitly confirmed");
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }

    @FunctionalInterface
    public interface MinecraftOnlineVerifier {
        boolean isOnline(UUID minecraftPlayerId);
    }

    public record IssuedCode(String code, Instant expiresAt, Direction direction) {
        public IssuedCode {
            if (code == null || code.isBlank() || expiresAt == null || direction == null) {
                throw new IllegalArgumentException("issued code fields must be present");
            }
        }
    }
}
