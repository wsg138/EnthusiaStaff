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
        requireCompatibleAudit(requestedAudit);
        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        VersionedLink linked;
        if (existing.isPresent()) {
            linked = existing.orElseThrow();
            if (!linked.link().discordUserId().equals(discordUserId)) {
                throw new IllegalStateException("force-link will not silently overwrite a different owner; use reassignment");
            }
        } else {
            linked = identities.link(
                    discordUserId, minecraftPlayerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                    operationKey + ":link", requestedAudit.createdAt());
        }
        mainAccounts.evaluate(discordUserId);
        audits.append(requestedAudit);
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
        requireCompatibleAudit(requestedAudit);
        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        if (existing.isPresent()) {
            VersionedLink current = existing.orElseThrow();
            if (!current.link().discordUserId().equals(expectedDiscordUserId)) {
                throw new IllegalStateException("current Discord owner does not match the recovery request");
            }
            mainAccounts.prepareForUnlink(expectedDiscordUserId, minecraftPlayerId);
            identities.unlink(
                    expectedDiscordUserId,
                    minecraftPlayerId,
                    current.revision(),
                    operationKey + ":unlink",
                    requestedAudit.createdAt());
            mainAccounts.evaluate(expectedDiscordUserId);
        }
        audits.append(requestedAudit);
        return existing.isPresent();
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
        requireCompatibleAudit(requestedAudit);
        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        DiscordUserId oldDiscordUserId = existing.map(value -> value.link().discordUserId()).orElse(null);
        if (oldDiscordUserId != null && !oldDiscordUserId.equals(newDiscordUserId)) {
            mainAccounts.prepareForUnlink(oldDiscordUserId, minecraftPlayerId);
        }
        VersionedLink linked = identities.reassign(
                newDiscordUserId, minecraftPlayerId, operationKey, requestedAudit.createdAt());
        if (oldDiscordUserId != null && !oldDiscordUserId.equals(newDiscordUserId)) {
            mainAccounts.evaluate(oldDiscordUserId);
        }
        mainAccounts.evaluate(newDiscordUserId);
        audits.append(requestedAudit);
        return linked;
    }

    private void requireCompatibleAudit(AccountLinkAudit requested) {
        audits.findByOperationKey(requested.operationKey()).ifPresent(existing -> {
            boolean sameRequest = existing.actor().equals(requested.actor())
                    && existing.action() == requested.action()
                    && existing.discordUserId().equals(requested.discordUserId())
                    && existing.minecraftPlayerId().equals(requested.minecraftPlayerId())
                    && existing.detail().equals(requested.detail());
            if (!sameRequest) {
                throw new IllegalStateException(
                        "account-link recovery operation key was already used for a different audited request");
            }
        });
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
