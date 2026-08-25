package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.moderation.AccountLinkAudit;
import net.enthusia.staff.domain.moderation.AccountLinkAuditAction;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.MainMinecraftAccount;
import net.enthusia.staff.domain.ports.AccountLinkAuditStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Permission-gated, retryable staff recovery for identity-link mistakes. */
public final class AccountLinkRecoveryService {
    private static final String FORCE_LINK_DETAIL = "Staff force-linked a verified identity pair";
    private static final String FORCE_UNLINK_DETAIL = "Staff force-unlinked an identity pair";
    private static final String REASSIGN_DETAIL = "Staff atomically reassigned the current Discord owner";

    private final Clock clock;
    private final AuthorizationPolicy authorization;
    private final DiscordModerationPersistenceStore identities;
    private final AccountLinkAuditStore audits;
    private final MainAccountSelectionService mainAccounts;

    public AccountLinkRecoveryService(
            Clock clock,
            AuthorizationPolicy authorization,
            DiscordModerationPersistenceStore identities,
            AccountLinkAuditStore audits,
            MainAccountSelectionService mainAccounts
    ) {
        this.clock = require(clock, "clock");
        this.authorization = require(authorization, "authorization");
        this.identities = require(identities, "identities");
        this.audits = require(audits, "audits");
        this.mainAccounts = require(mainAccounts, "mainAccounts");
    }

    public VersionedLink forceLink(
            Actor actor,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String operationKey
    ) {
        authorize(actor);
        validateOperationKey(operationKey);
        AccountLinkAudit requestedAudit = auditRecord(
                operationKey, actor, AccountLinkAuditAction.FORCE_LINK,
                discordUserId, minecraftPlayerId, FORCE_LINK_DETAIL, clock.instant());
        if (isAuditReplay(requestedAudit)) {
            return requireDesiredCurrentLink(minecraftPlayerId, discordUserId);
        }

        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        VersionedLink linked;
        if (existing.isPresent()) {
            linked = existing.orElseThrow();
            if (!linked.link().discordUserId().equals(discordUserId)) {
                throw new IllegalStateException("force-link will not silently overwrite a different owner; use reassignment");
            }
            // No identity mutation is required, so the audit is the only durable change.
            audits.append(requestedAudit);
        } else {
            linked = identities.linkWithAudit(
                    discordUserId,
                    minecraftPlayerId,
                    DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    operationKey + ":link",
                    requestedAudit.createdAt(),
                    requestedAudit
            );
        }
        requireCurrentLink(linked, discordUserId);
        if (!linked.replayed()) {
            mainAccounts.evaluate(discordUserId);
        }
        return linked;
    }

    public boolean forceUnlink(
            Actor actor,
            DiscordUserId expectedDiscordUserId,
            UUID minecraftPlayerId,
            String operationKey
    ) {
        authorize(actor);
        validateOperationKey(operationKey);
        AccountLinkAudit requestedAudit = auditRecord(
                operationKey, actor, AccountLinkAuditAction.FORCE_UNLINK,
                expectedDiscordUserId, minecraftPlayerId, FORCE_UNLINK_DETAIL, clock.instant());
        if (isAuditReplay(requestedAudit)) {
            requireNoCurrentLink(minecraftPlayerId);
            return false;
        }

        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        if (existing.isEmpty()) {
            // A confirmed no-op is still auditable, but there is no identity mutation to combine with it.
            audits.append(requestedAudit);
            requireNoCurrentLink(minecraftPlayerId);
            return false;
        }

        VersionedLink current = existing.orElseThrow();
        if (!current.link().discordUserId().equals(expectedDiscordUserId)) {
            throw new IllegalStateException("current Discord owner does not match the recovery request");
        }
        Optional<MainMinecraftAccount> replacement =
                mainAccounts.replacementForUnlink(expectedDiscordUserId, minecraftPlayerId);
        VersionedLink unlinked = identities.unlinkWithAudit(
                expectedDiscordUserId,
                minecraftPlayerId,
                current.revision(),
                replacement,
                operationKey + ":unlink",
                requestedAudit.createdAt(),
                requestedAudit
        );
        requireNoCurrentLink(minecraftPlayerId);
        if (!unlinked.replayed()) {
            mainAccounts.evaluate(expectedDiscordUserId);
        }
        return !unlinked.replayed();
    }

    public VersionedLink reassign(
            Actor actor,
            DiscordUserId newDiscordUserId,
            UUID minecraftPlayerId,
            String operationKey
    ) {
        authorize(actor);
        validateOperationKey(operationKey);
        AccountLinkAudit requestedAudit = auditRecord(
                operationKey, actor, AccountLinkAuditAction.REASSIGN,
                newDiscordUserId, minecraftPlayerId, REASSIGN_DETAIL, clock.instant());
        if (isAuditReplay(requestedAudit)) {
            return requireDesiredCurrentLink(minecraftPlayerId, newDiscordUserId);
        }

        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        DiscordUserId oldDiscordUserId = existing.map(value -> value.link().discordUserId()).orElse(null);
        Optional<MainMinecraftAccount> replacement = oldDiscordUserId != null
                && !oldDiscordUserId.equals(newDiscordUserId)
                ? mainAccounts.replacementForUnlink(oldDiscordUserId, minecraftPlayerId)
                : Optional.empty();
        VersionedLink linked = identities.reassignWithAudit(
                newDiscordUserId,
                minecraftPlayerId,
                replacement,
                operationKey,
                requestedAudit.createdAt(),
                requestedAudit
        );
        requireCurrentLink(linked, newDiscordUserId);
        if (!linked.replayed()) {
            if (oldDiscordUserId != null && !oldDiscordUserId.equals(newDiscordUserId)) {
                mainAccounts.evaluate(oldDiscordUserId);
            }
            mainAccounts.evaluate(newDiscordUserId);
        }
        return linked;
    }

    private VersionedLink requireDesiredCurrentLink(UUID minecraftPlayerId, DiscordUserId expectedDiscordUserId) {
        VersionedLink current = identities.currentLink(minecraftPlayerId).orElseThrow(() ->
                new IllegalStateException("audited account-link replay no longer resolves to a current link"));
        if (!current.link().discordUserId().equals(expectedDiscordUserId)) {
            throw new IllegalStateException("audited account-link replay no longer matches current authoritative state");
        }
        return current;
    }

    private void requireCurrentLink(VersionedLink expected, DiscordUserId expectedDiscordUserId) {
        VersionedLink current = requireDesiredCurrentLink(expected.link().minecraftPlayerId(), expectedDiscordUserId);
        if (!current.linkId().equals(expected.linkId())) {
            throw new IllegalStateException("audited account-link result was superseded before it could be verified");
        }
    }

    private void requireNoCurrentLink(UUID minecraftPlayerId) {
        if (identities.currentLink(minecraftPlayerId).isPresent()) {
            throw new IllegalStateException("audited account-unlink replay no longer matches current authoritative state");
        }
    }

    private boolean isAuditReplay(AccountLinkAudit requested) {
        AccountLinkAudit existing = audits.findByOperationKey(requested.operationKey()).orElse(null);
        if (existing == null) {
            return false;
        }
        boolean sameRequest = existing.actor().equals(requested.actor())
                && existing.action() == requested.action()
                && existing.discordUserId().equals(requested.discordUserId())
                && existing.minecraftPlayerId().equals(requested.minecraftPlayerId())
                && existing.detail().equals(requested.detail());
        if (!sameRequest) {
            throw new IllegalStateException(
                    "account-link recovery operation key was already used for a different audited request");
        }
        return true;
    }

    private static AccountLinkAudit auditRecord(
            String operationKey,
            Actor actor,
            AccountLinkAuditAction action,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String detail,
            Instant createdAt
    ) {
        return new AccountLinkAudit(
                operationKey, actor, action, Optional.of(discordUserId), Optional.of(minecraftPlayerId),
                detail, createdAt);
    }

    private void authorize(Actor actor) {
        if (!authorization.permits(actor, ModerationAction.MANAGE_ACCOUNT_LINKS)) {
            throw new SecurityException("actor is not authorized to manage account links");
        }
        if (actor.displayName().length() > 64) {
            throw new IllegalArgumentException("actor display name exceeds account-link audit storage limit");
        }
    }

    private static void validateOperationKey(String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 116) {
            throw new IllegalArgumentException("operationKey must be nonblank and at most 116 characters");
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }
}
