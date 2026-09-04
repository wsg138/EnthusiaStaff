package net.enthusia.staff.domain.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.moderation.DiscordMinecraftLinkSource;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedLink;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;

/** Provider-neutral DiscordSRV migration logic. Calling importSnapshot is intentionally explicit. */
public final class DiscordSrvMigrationService {
    private final Clock clock;
    private final ImportStore identities;

    public DiscordSrvMigrationService(Clock clock, DiscordModerationPersistenceStore identities) {
        this(clock, new PersistenceImportStore(require(identities, "identities")));
    }

    public DiscordSrvMigrationService(Clock clock, ImportStore identities) {
        this.clock = require(clock, "clock");
        this.identities = require(identities, "identities");
    }

    public ImportReport importSnapshot(DiscordSrvLinkProvider provider) {
        require(provider, "provider");
        List<Map.Entry<String, UUID>> entries = new ArrayList<>(provider.snapshotLinks().entrySet());
        entries.sort(Comparator.comparing(Map.Entry<String, UUID>::getKey)
                .thenComparing(entry -> entry.getValue().toString()));
        ImportAccumulator result = new ImportAccumulator();
        for (Map.Entry<String, UUID> entry : entries) {
            importEntry(entry, result);
        }
        return result.report();
    }

    private void importEntry(Map.Entry<String, UUID> entry, ImportAccumulator result) {
        DiscordUserId discordUserId;
        try {
            discordUserId = new DiscordUserId(entry.getKey());
        } catch (RuntimeException invalid) {
            result.conflict(entry.getKey(), entry.getValue(), "invalid Discord user id");
            return;
        }
        UUID minecraftPlayerId = entry.getValue();
        Optional<VersionedLink> current = identities.currentLink(minecraftPlayerId);
        if (current.isPresent()) {
            if (current.orElseThrow().link().discordUserId().equals(discordUserId)) {
                result.unchanged();
            } else {
                result.conflict(
                        discordUserId.value(), minecraftPlayerId,
                        "Minecraft UUID already has a different current Discord owner");
            }
            return;
        }
        String operationKey = "discordsrv-import:" + discordUserId.value() + ":" + minecraftPlayerId;
        try {
            identities.link(
                    discordUserId,
                    minecraftPlayerId,
                    DiscordMinecraftLinkSource.MIGRATED_DISCORDSRV,
                    operationKey,
                    clock.instant()
            );
            result.imported();
        } catch (RuntimeException failure) {
            result.conflict(
                    discordUserId.value(), minecraftPlayerId,
                    "provider pair could not be imported without overwriting authoritative state");
        }
    }

    /** Mirrors the authoritative current main to legacy DiscordSRV, clearing its link when no main remains. */
    public MirrorResult syncCurrentMain(DiscordUserId discordUserId, DiscordSrvLinkProvider provider) {
        require(discordUserId, "discordUserId");
        require(provider, "provider");
        UUID main = identities.subjectForDiscord(discordUserId)
                .flatMap(value -> value.subject().mainMinecraftAccount())
                .map(value -> value.playerId())
                .orElse(null);
        return main == null
                ? provider.clearMirror(discordUserId.value())
                : provider.mirrorMain(discordUserId.value(), main);
    }

    /** Backward-compatible name retained for callers that only expect a main-account mirror. */
    public MirrorResult mirrorCurrentMain(DiscordUserId discordUserId, DiscordSrvLinkProvider provider) {
        return syncCurrentMain(discordUserId, provider);
    }

    /** Narrow persistence surface needed by a one-way DiscordSRV import. */
    public interface ImportStore {
        Optional<VersionedLink> currentLink(UUID minecraftPlayerId);

        VersionedLink link(
                DiscordUserId discordUserId,
                UUID minecraftPlayerId,
                DiscordMinecraftLinkSource source,
                String operationKey,
                Instant linkedAt
        );

        Optional<VersionedSubject> subjectForDiscord(DiscordUserId discordUserId);
    }

    public interface DiscordSrvLinkProvider {
        Map<String, UUID> snapshotLinks();

        MirrorResult mirrorMain(String discordUserId, UUID minecraftPlayerId);

        default MirrorResult clearMirror(String discordUserId) {
            return MirrorResult.UNAVAILABLE;
        }
    }

    public enum MirrorResult {
        UPDATED,
        UNCHANGED,
        CONFLICT,
        UNAVAILABLE,
        NO_MAIN
    }

    public record ImportConflict(String discordUserId, UUID minecraftPlayerId, String reason) {
    }

    public record ImportReport(int imported, int unchanged, List<ImportConflict> conflicts) {
        public ImportReport {
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }
    }

    private record PersistenceImportStore(DiscordModerationPersistenceStore delegate) implements ImportStore {
        @Override
        public Optional<VersionedLink> currentLink(UUID minecraftPlayerId) {
            return delegate.currentLink(minecraftPlayerId);
        }

        @Override
        public VersionedLink link(
                DiscordUserId discordUserId,
                UUID minecraftPlayerId,
                DiscordMinecraftLinkSource source,
                String operationKey,
                Instant linkedAt
        ) {
            return delegate.link(discordUserId, minecraftPlayerId, source, operationKey, linkedAt);
        }

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId discordUserId) {
            return delegate.subjectForDiscord(discordUserId);
        }
    }

    private static final class ImportAccumulator {
        private int imported;
        private int unchanged;
        private final List<ImportConflict> conflicts = new ArrayList<>();

        private void imported() {
            imported++;
        }

        private void unchanged() {
            unchanged++;
        }

        private void conflict(String discordUserId, UUID minecraftPlayerId, String reason) {
            conflicts.add(new ImportConflict(discordUserId, minecraftPlayerId, reason));
        }

        private ImportReport report() {
            return new ImportReport(imported, unchanged, conflicts);
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be present");
        }
        return value;
    }
}
