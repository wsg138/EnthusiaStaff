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
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.AccountLinkingStore;
import net.enthusia.staff.domain.ports.AccountLinkingStore.CodeClaim;
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

    public AccountLinkingService(
            Clock clock,
            SecureRandom random,
            DiscordModerationPersistenceStore identities,
            AccountLinkingStore codes,
            MinecraftOnlineVerifier online
    ) {
        this.clock = require(clock, "clock");
        this.random = require(random, "random");
        this.identities = require(identities, "identities");
        this.codes = require(codes, "codes");
        this.online = require(online, "online");
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
        CodeClaim claim = codes.claim(codeHash, Direction.DISCORD_TO_MINECRAFT, operationKey, clock.instant());
        DiscordUserId discordUserId = claim.discordInitiator().orElseThrow();
        return finish(claim, discordUserId, minecraftPlayerId, DiscordMinecraftLinkSource.DISCORD_CODE);
    }

    public VersionedLink completeFromDiscord(String rawCode, DiscordUserId discordUserId) {
        require(discordUserId, "discordUserId");
        String codeHash = hash(normalize(rawCode));
        String operationKey = operationKey("discord", codeHash, discordUserId.value());
        CodeClaim claim = codes.claim(codeHash, Direction.MINECRAFT_TO_DISCORD, operationKey, clock.instant());
        UUID minecraftPlayerId = claim.minecraftInitiator().orElseThrow();
        return finish(claim, discordUserId, minecraftPlayerId, DiscordMinecraftLinkSource.MINECRAFT_CODE);
    }

    public boolean unlinkFromMinecraft(UUID minecraftPlayerId, boolean confirmed) {
        requireOnline(minecraftPlayerId);
        requireConfirmed(confirmed);
        Optional<VersionedLink> current = identities.currentLink(minecraftPlayerId);
        if (current.isEmpty()) {
            return false;
        }
        VersionedLink link = current.orElseThrow();
        identities.unlink(
                link.link().discordUserId(),
                minecraftPlayerId,
                link.revision(),
                "self-unlink:mc:" + link.linkId(),
                clock.instant()
        );
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
        identities.unlink(
                discordUserId,
                minecraftPlayerId,
                link.revision(),
                "self-unlink:discord:" + link.linkId(),
                clock.instant()
        );
        return true;
    }

    private VersionedLink finish(
            CodeClaim claim,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            DiscordMinecraftLinkSource source
    ) {
        if (claim.consumed()) {
            return requireCurrentPair(discordUserId, minecraftPlayerId);
        }

        Optional<VersionedLink> current = identities.currentLink(minecraftPlayerId);
        if (current.isPresent()) {
            VersionedLink linked = current.orElseThrow();
            if (!linked.link().discordUserId().equals(discordUserId)) {
                codes.release(claim.codeId(), claim.operationKey(), clock.instant());
                throw new IllegalStateException("Minecraft account already has a different Discord owner");
            }
            // The authoritative link may have committed immediately before a crash. A retry using
            // the same deterministic claim is allowed to finish consumption even after code expiry.
            codes.consume(claim.codeId(), claim.operationKey(), clock.instant());
            return linked;
        }

        if (!clock.instant().isBefore(claim.expiresAt())) {
            codes.release(claim.codeId(), claim.operationKey(), clock.instant());
            throw new IllegalStateException("link code expired");
        }

        boolean linkCommitted = false;
        try {
            VersionedLink linked = identities.link(
                    discordUserId,
                    minecraftPlayerId,
                    source,
                    claim.operationKey(),
                    clock.instant()
            );
            linkCommitted = true;
            codes.consume(claim.codeId(), claim.operationKey(), clock.instant());
            return linked;
        } catch (RuntimeException failure) {
            if (!linkCommitted) {
                codes.release(claim.codeId(), claim.operationKey(), clock.instant());
            }
            throw failure;
        }
    }

    private VersionedLink requireCurrentPair(DiscordUserId discordUserId, UUID minecraftPlayerId) {
        VersionedLink current = identities.currentLink(minecraftPlayerId)
                .orElseThrow(() -> new IllegalStateException("consumed link code has no current link"));
        if (!current.link().discordUserId().equals(discordUserId)) {
            throw new IllegalStateException("consumed link code no longer matches the current owner");
        }
        return current;
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
