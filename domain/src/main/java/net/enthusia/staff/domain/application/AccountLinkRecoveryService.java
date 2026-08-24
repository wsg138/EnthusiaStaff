package net.enthusia.staff.domain.application;

import java.time.Clock;
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
    private final Clock clock;
    private final AuthorizationPolicy authorization;
    private final DiscordModerationPersistenceStore identities;
    private final AccountLinkAuditStore audits;

    public AccountLinkRecoveryService(
            Clock clock,
            AuthorizationPolicy authorization,
            DiscordModerationPersistenceStore identities,
            AccountLinkAuditStore audits
    ) {
        this.clock = require(clock, "clock");
        this.authorization = require(authorization, "authorization");
        this.identities = require(identities, "identities");
        this.audits = require(audits, "audits");
    }

    public VersionedLink forceLink(
            Actor actor,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String operationKey
    ) {
        authorize(actor);
        validateOperationKey(operationKey);
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
                    operationKey + ":link", clock.instant());
        }
        audit(operationKey, actor, AccountLinkAuditAction.FORCE_LINK,
                discordUserId, minecraftPlayerId, "Staff force-linked a verified identity pair");
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
        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        if (existing.isPresent()) {
            VersionedLink current = existing.orElseThrow();
            if (!current.link().discordUserId().equals(expectedDiscordUserId)) {
                throw new IllegalStateException("current Discord owner does not match the recovery request");
            }
            identities.unlink(
                    expectedDiscordUserId,
                    minecraftPlayerId,
                    current.revision(),
                    operationKey + ":unlink",
                    clock.instant());
        }
        audit(operationKey, actor, AccountLinkAuditAction.FORCE_UNLINK,
                expectedDiscordUserId, minecraftPlayerId, "Staff force-unlinked an identity pair");
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
        Optional<VersionedLink> existing = identities.currentLink(minecraftPlayerId);
        if (existing.isPresent() && !existing.orElseThrow().link().discordUserId().equals(newDiscordUserId)) {
            VersionedLink current = existing.orElseThrow();
            identities.unlink(
                    current.link().discordUserId(),
                    minecraftPlayerId,
                    current.revision(),
                    operationKey + ":unlink",
                    clock.instant());
        }
        VersionedLink linked = identities.currentLink(minecraftPlayerId)
                .filter(value -> value.link().discordUserId().equals(newDiscordUserId))
                .orElseGet(() -> identities.link(
                        newDiscordUserId, minecraftPlayerId, DiscordMinecraftLinkSource.STAFF_RECOVERY,
                        operationKey + ":link", clock.instant()));
        audit(operationKey, actor, AccountLinkAuditAction.REASSIGN,
                newDiscordUserId, minecraftPlayerId, "Staff reassigned the current Discord owner");
        return linked;
    }

    private void audit(
            String operationKey,
            Actor actor,
            AccountLinkAuditAction action,
            DiscordUserId discordUserId,
            UUID minecraftPlayerId,
            String detail
    ) {
        audits.append(new AccountLinkAudit(
                operationKey, actor, action, Optional.of(discordUserId), Optional.of(minecraftPlayerId),
                detail, clock.instant()));
    }

    private void authorize(Actor actor) {
        if (!authorization.permits(actor, ModerationAction.MANAGE_ACCOUNT_LINKS)) {
            throw new SecurityException("actor is not authorized to manage account links");
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
