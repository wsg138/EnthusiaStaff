package net.enthusia.staff.domain.application;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.moderation.AccountLinkAudit;
import net.enthusia.staff.domain.moderation.AccountLinkAuditAction;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainAccountSelectionSource;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.ports.AccountLinkAuditStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

/** Chooses a linked main account from lifetime active playtime with 25% hysteresis. */
public final class MainAccountSelectionService {
    private static final String MAIN_OVERRIDE_SET_DETAIL = "Staff set the authoritative main Minecraft account";
    private static final String MAIN_OVERRIDE_CLEAR_DETAIL =
            "Staff removed the main-account override; automatic selection may resume";

    private final Clock clock;
    private final DiscordModerationPersistenceStore identities;
    private final ActivePlaytimeProvider playtime;
    private final AuthorizationPolicy authorization;
    private final AccountLinkAuditStore audits;

    public MainAccountSelectionService(
            Clock clock,
            DiscordModerationPersistenceStore identities,
            ActivePlaytimeProvider playtime,
            AuthorizationPolicy authorization,
            AccountLinkAuditStore audits
    ) {
        this.clock = require(clock, "clock");
        this.identities = require(identities, "identities");
        this.playtime = require(playtime, "playtime");
        this.authorization = require(authorization, "authorization");
        this.audits = require(audits, "audits");
    }

    public Optional<MainMinecraftAccount> evaluate(DiscordUserId discordUserId) {
        VersionedSubject versioned = identities.subjectForDiscord(discordUserId).orElse(null);
        if (versioned == null) {
            return Optional.empty();
        }
        ModerationSubject subject = versioned.subject();
        if (subject.minecraftAccountIds().isEmpty()) {
            return Optional.empty();
        }
        Optional<MainMinecraftAccount> currentOptional = subject.mainMinecraftAccount();
        if (currentOptional.isEmpty()) {
            return establishMissingMain(versioned);
        }
        MainMinecraftAccount current = currentOptional.orElseThrow();
        if (current.source() == MainAccountSelectionSource.STAFF_OVERRIDE) {
            return Optional.of(current);
        }

        Map<UUID, Long> minutes = allMinutes(subject.minecraftAccountIds());
        if (minutes.size() != subject.minecraftAccountIds().size()) {
            // A partial provider view cannot safely rank the linked accounts. Preserve current.
            return Optional.of(current);
        }
        long currentMinutes = minutes.get(current.playerId());
        Map.Entry<UUID, Long> best = minutes.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(current.playerId()))
                .max(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().toString()))
                .orElse(null);
        if (best == null || !shouldSwitch(currentMinutes, best.getValue())) {
            return Optional.of(current);
        }
        VersionedSubject changed = identities.setMainMinecraftAccount(
                subject.subjectId(),
                new MainMinecraftAccount(best.getKey(), MainAccountSelectionSource.AUTOMATIC),
                versioned.revision(),
                clock.instant()
        );
        return changed.subject().mainMinecraftAccount();
    }

    /**
     * Ensures unlinking the current main cannot commit a linked multi-account subject without a main.
     * If the provider cannot rank every remaining account, deterministic UUID order is used rather
     * than treating missing playtime as zero.
     */
    public Optional<MainMinecraftAccount> prepareForUnlink(
            DiscordUserId discordUserId,
            UUID removingPlayerId
    ) {
        VersionedSubject versioned = identities.subjectForDiscord(discordUserId).orElse(null);
        if (versioned == null || !versioned.subject().minecraftAccountIds().contains(removingPlayerId)) {
            return Optional.empty();
        }
        MainMinecraftAccount current = versioned.subject().mainMinecraftAccount().orElse(null);
        if (current == null || !current.playerId().equals(removingPlayerId)) {
            return Optional.ofNullable(current);
        }
        Set<UUID> remaining = versioned.subject().minecraftAccountIds().stream()
                .filter(playerId -> !playerId.equals(removingPlayerId))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (remaining.isEmpty()) {
            return Optional.empty();
        }
        UUID replacement = chooseReplacement(remaining);
        MainMinecraftAccount automatic = new MainMinecraftAccount(
                replacement, MainAccountSelectionSource.AUTOMATIC);
        VersionedSubject changed = identities.setMainMinecraftAccount(
                versioned.subject().subjectId(), automatic, versioned.revision(), clock.instant());
        return changed.subject().mainMinecraftAccount();
    }

    public MainMinecraftAccount setStaffOverride(
            Actor actor,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String operationKey
    ) {
        requireAuthorized(actor);
        validateAuditOperationKey(operationKey);
        Instant now = clock.instant();
        AccountLinkAudit requestedAudit = new AccountLinkAudit(
                operationKey, actor, AccountLinkAuditAction.MAIN_OVERRIDE_SET,
                Optional.of(discordUserId), Optional.of(minecraftPlayerId),
                MAIN_OVERRIDE_SET_DETAIL, now
        );
        AccountLinkAudit replay = audits.findByOperationKey(operationKey).orElse(null);
        if (replay != null) {
            requireMatchingSetAudit(replay, actor, discordUserId, minecraftPlayerId);
            return new MainMinecraftAccount(minecraftPlayerId, MainAccountSelectionSource.STAFF_OVERRIDE);
        }

        VersionedSubject versioned = identities.subjectForDiscord(discordUserId)
                .orElseThrow(() -> new IllegalStateException("Discord identity has no moderation subject"));
        if (!versioned.subject().minecraftAccountIds().contains(minecraftPlayerId)) {
            throw new IllegalArgumentException("main account must belong to the linked Discord subject");
        }
        MainMinecraftAccount main = new MainMinecraftAccount(
                minecraftPlayerId, MainAccountSelectionSource.STAFF_OVERRIDE);
        identities.setMainMinecraftAccount(
                versioned.subject().subjectId(), main, versioned.revision(), now);
        audits.append(requestedAudit);
        return main;
    }

    public MainMinecraftAccount clearStaffOverride(
            Actor actor,
            DiscordUserId discordUserId,
            String operationKey
    ) {
        requireAuthorized(actor);
        validateAuditOperationKey(operationKey);
        AccountLinkAudit replay = audits.findByOperationKey(operationKey).orElse(null);
        if (replay != null) {
            requireMatchingClearAudit(replay, actor, discordUserId);
            UUID replayedPlayerId = replay.minecraftPlayerId()
                    .orElseThrow(() -> new IllegalStateException("clear-override audit is missing its Minecraft account"));
            return new MainMinecraftAccount(replayedPlayerId, MainAccountSelectionSource.AUTOMATIC);
        }

        VersionedSubject versioned = identities.subjectForDiscord(discordUserId)
                .orElseThrow(() -> new IllegalStateException("Discord identity has no moderation subject"));
        MainMinecraftAccount current = versioned.subject().mainMinecraftAccount()
                .orElseThrow(() -> new IllegalStateException("subject has no main Minecraft account"));
        MainMinecraftAccount automatic;
        if (current.source() == MainAccountSelectionSource.STAFF_OVERRIDE) {
            MainMinecraftAccount unlocked = new MainMinecraftAccount(
                    current.playerId(), MainAccountSelectionSource.AUTOMATIC);
            identities.setMainMinecraftAccount(
                    versioned.subject().subjectId(), unlocked, versioned.revision(), clock.instant());
            automatic = evaluate(discordUserId).orElse(unlocked);
        } else {
            // This also repairs the audit on a retry where the prior clear committed before its
            // separate audit append completed. Clearing an already-automatic main is an idempotent no-op.
            automatic = current;
        }
        Instant auditedAt = clock.instant();
        audits.append(new AccountLinkAudit(
                operationKey, actor, AccountLinkAuditAction.MAIN_OVERRIDE_CLEAR,
                Optional.of(discordUserId), Optional.of(automatic.playerId()),
                MAIN_OVERRIDE_CLEAR_DETAIL, auditedAt
        ));
        return automatic;
    }

    private Optional<MainMinecraftAccount> establishMissingMain(VersionedSubject versioned) {
        UUID selected = chooseReplacement(versioned.subject().minecraftAccountIds());
        MainMinecraftAccount main = new MainMinecraftAccount(selected, MainAccountSelectionSource.AUTOMATIC);
        VersionedSubject changed = identities.setMainMinecraftAccount(
                versioned.subject().subjectId(), main, versioned.revision(), clock.instant());
        return changed.subject().mainMinecraftAccount();
    }

    private UUID chooseReplacement(Set<UUID> candidates) {
        Map<UUID, Long> minutes = allMinutes(candidates);
        if (minutes.size() == candidates.size()) {
            return minutes.entrySet().stream()
                    .max(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue)
                            .thenComparing(entry -> entry.getKey().toString()))
                    .orElseThrow()
                    .getKey();
        }
        return candidates.stream().min(Comparator.comparing(UUID::toString)).orElseThrow();
    }

    private Map<UUID, Long> allMinutes(Set<UUID> playerIds) {
        Map<UUID, Long> values = new LinkedHashMap<>();
        for (UUID playerId : playerIds) {
            OptionalLong value = safeMinutes(playerId);
            if (value.isEmpty()) {
                return values;
            }
            values.put(playerId, value.orElseThrow());
        }
        return values;
    }

    private OptionalLong safeMinutes(UUID playerId) {
        try {
            OptionalLong value = playtime.lifetimeActiveMinutes(playerId);
            if (value == null || value.isEmpty() || value.orElseThrow() < 0L) {
                return OptionalLong.empty();
            }
            return value;
        } catch (RuntimeException providerFailure) {
            return OptionalLong.empty();
        }
    }

    static boolean shouldSwitch(long currentMinutes, long candidateMinutes) {
        if (currentMinutes < 0L || candidateMinutes < 0L || candidateMinutes <= currentMinutes) {
            return false;
        }
        return BigInteger.valueOf(candidateMinutes).multiply(BigInteger.valueOf(4L))
                .compareTo(BigInteger.valueOf(currentMinutes).multiply(BigInteger.valueOf(5L))) >= 0;
    }

    private static void requireMatchingSetAudit(
            AccountLinkAudit audit,
            Actor actor,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId
    ) {
        boolean matches = audit.actor().equals(actor)
                && audit.action() == AccountLinkAuditAction.MAIN_OVERRIDE_SET
                && audit.discordUserId().equals(Optional.of(discordUserId))
                && audit.minecraftPlayerId().equals(Optional.of(minecraftPlayerId))
                && audit.detail().equals(MAIN_OVERRIDE_SET_DETAIL);
        if (!matches) {
            throw new IllegalStateException("main-account operation key was already used for a different audited request");
        }
    }

    private static void requireMatchingClearAudit(
            AccountLinkAudit audit,
            Actor actor,
            DiscordUserId discordUserId
    ) {
        boolean matches = audit.actor().equals(actor)
                && audit.action() == AccountLinkAuditAction.MAIN_OVERRIDE_CLEAR
                && audit.discordUserId().equals(Optional.of(discordUserId))
                && audit.minecraftPlayerId().isPresent()
                && audit.detail().equals(MAIN_OVERRIDE_CLEAR_DETAIL);
        if (!matches) {
            throw new IllegalStateException("main-account operation key was already used for a different audited request");
        }
    }

    private void requireAuthorized(Actor actor) {
        if (!authorization.permits(actor, ModerationAction.MANAGE_ACCOUNT_LINKS)) {
            throw new SecurityException("actor is not authorized to manage account links");
        }
        if (actor.displayName().length() > 64) {
            throw new IllegalArgumentException("actor display name exceeds account-link audit storage limit");
        }
    }

    private static void validateAuditOperationKey(String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new IllegalArgumentException("operationKey must be nonblank and at most 128 characters");
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }
}
