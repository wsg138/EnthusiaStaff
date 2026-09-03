package net.enthusia.staff.paper.account;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.AccountLinkingService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.DiscordSrvLinkProvider;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.ImportReport;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;

/** Paper-side coordinator that keeps authoritative links separate from best-effort legacy mirroring. */
public final class PaperAccountLinkRuntime {
    private final AccountLinkingService linking;
    private final DiscordModerationPersistenceStore identities;
    private final DiscordSrvMigrationService migration;
    private final Optional<DiscordSrvLinkProvider> discordSrv;

    public PaperAccountLinkRuntime(
            AccountLinkingService linking,
            DiscordModerationPersistenceStore identities,
            DiscordSrvMigrationService migration,
            Optional<? extends DiscordSrvLinkProvider> discordSrv
    ) {
        this.linking = require(linking, "linking");
        this.identities = require(identities, "identities");
        this.migration = require(migration, "migration");
        this.discordSrv = discordSrv == null
                ? Optional.empty()
                : discordSrv.map(value -> (DiscordSrvLinkProvider) value);
    }

    public AccountLinkingService.IssuedCode issueFromMinecraft(UUID playerId) {
        return linking.issueFromMinecraft(playerId);
    }

    public LinkResult completeFromMinecraft(String code, UUID playerId) {
        VersionedLink linked = linking.completeFromMinecraft(code, playerId);
        return new LinkResult(linked, sync(linked.link().discordUserId()));
    }

    public UnlinkResult unlinkFromMinecraft(UUID playerId, boolean confirmed) {
        DiscordUserId discordUserId = identities.currentLink(playerId)
                .map(value -> value.link().discordUserId())
                .orElse(null);
        boolean changed = linking.unlinkFromMinecraft(playerId, confirmed);
        MirrorResult mirror = changed && discordUserId != null ? sync(discordUserId) : MirrorResult.UNCHANGED;
        return new UnlinkResult(changed, mirror);
    }

    /** Explicit migration hook; never called automatically during startup. */
    public Optional<ImportReport> importDiscordSrvSnapshot() {
        return discordSrv.map(migration::importSnapshot);
    }

    public MirrorResult sync(DiscordUserId discordUserId) {
        return discordSrv
                .map(provider -> migration.syncCurrentMain(discordUserId, provider))
                .orElse(MirrorResult.UNAVAILABLE);
    }

    public record LinkResult(VersionedLink link, MirrorResult mirrorResult) {
    }

    public record UnlinkResult(boolean changed, MirrorResult mirrorResult) {
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }
}
