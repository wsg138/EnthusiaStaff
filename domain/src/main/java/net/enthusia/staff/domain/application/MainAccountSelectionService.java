package net.enthusia.staff.domain.application;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalLong;
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
        if (current.selectionSource() == MainAccountSelectionSource.STAFF_OVERRIDE) {
            return Optional.of(current);
        }
        OptionalLong currentMinutes = safeMinutes(current.playerId());
        if (currentMinutes.isEmpty()) {
            return Optional.of(current);
        }
        Candidate best = subject.minecraftAccountIds().stream()
                .filter(playerId -> !playerId.equals(current.playerId()))
                .map(playerId -> new Candidate(playerId, safeMinutes(playerId)))
                .filter(candidate -> candidate.minutes().isPresent())
                .max(Comparator.comparingLong(candidate -> candidate.minutes().orElseThrow()))
                .orElse(null);
        if (best == null || !shouldSwitch(currentMinutes.orElseThrow(), best.minutes().orElseThrow())) {
            return Optional.of(current);
        }
        VersionedSubject changed = identities.setMainMinecraftAccount(
                subject.subjectId(),
                new MainMinecraftAccount(best.playerId(), MainAccountSelectionSource.AUTOMATIC),
                versioned.revision(),
                clock.instant()
        );
        return changed.subject().mainMinecraftAccount();
    }

    public MainMinecraftAccount setStaffOverride(
            Actor actor,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String operationKey
    ) {
        requireAuthorized(actor);
        VersionedSubject versioned = identities.subjectForDiscord(discordUserId)
                .orElseThrow(() -> new IllegalStateException("Discord identity has no moderation subject"));
        if (!versioned.subject().minecraftAccountIds().contains(minecraftPlayerId)) {
            throw new IllegalArgumentException("main account must belong to the linked Discord subject");
        }
        MainMinecraftAccount main = new MainMinecraftAccount(
                minecraftPlayerId, MainAccountSelectionSource.STAFF_OVERRIDE);
        identities.setMainMinecraftAccount(
                versioned.subject().subjectId(), main, versioned.revision(), clock.instant());
        audits.append(new AccountLinkAudit(
                operationKey, actor, AccountLinkAuditAction.MAIN_OVERRIDE_SET,
                Optional.of(discordUserId), Optional.of(minecraftPlayerId),
                "Staff set the authoritative main Minecraft account", clock.instant()
        ));
        return main;
    }

    public MainMinecraftAccount clearStaffOverride(
            Actor actor,
            DiscordUserId discordUserId,
            String operationKey
    ) {
        requireAuthorized(actor);
        VersionedSubject versioned = identities.subjectForDiscord(discordUserId)
                .orElseThrow(() -> new IllegalStateException("Discord identity has no moderation subject"));
        MainMinecraftAccount current = versioned.subject().mainMinecraftAccount()
                .orElseThrow(() -> new IllegalStateException("subject has no main Minecraft account"));
        MainMinecraftAccount automatic = new MainMinecraftAccount(
                current.playerId(), MainAccountSelectionSource.AUTOMATIC);
        identities.setMainMinecraftAccount(
                versioned.subject().subjectId(), automatic, versioned.revision(), clock.instant());
        audits.append(new AccountLinkAudit(
                operationKey, actor, AccountLinkAuditAction.MAIN_OVERRIDE_CLEAR,
                Optional.of(discordUserId), Optional.of(current.playerId()),
                "Staff removed the main-account override; automatic selection may resume", clock.instant()
        ));
        return evaluate(discordUserId).orElse(automatic);
    }

    private Optional<MainMinecraftAccount> establishMissingMain(VersionedSubject versioned) {
        UUID selected = versioned.subject().minecraftAccountIds().stream()
                .sorted()
                .findFirst()
                .orElseThrow();
        MainMinecraftAccount main = new MainMinecraftAccount(selected, MainAccountSelectionSource.AUTOMATIC);
        VersionedSubject changed = identities.setMainMinecraftAccount(
                versioned.subject().subjectId(), main, versioned.revision(), clock.instant());
        return changed.subject().mainMinecraftAccount();
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

    private void requireAuthorized(Actor actor) {
        if (!authorization.permits(actor, ModerationAction.MANAGE_ACCOUNT_LINKS)) {
            throw new SecurityException("actor is not authorized to manage account links");
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }

    private record Candidate(UUID playerId, OptionalLong minutes) {
    }
}
