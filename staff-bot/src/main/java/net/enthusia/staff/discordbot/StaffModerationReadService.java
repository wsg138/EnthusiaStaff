package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.HistoryQueryOptions;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubject;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.DiscordModerationPersistenceStore.VersionedSubject;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.persistence.DiscordStaffReadRuntime;

/** Bounded aggregation of authoritative read-only moderation data for Discord panels. */
final class StaffModerationReadService {
    private static final int MAX_LINKED_ACCOUNTS = 32;
    private static final int PER_ACCOUNT_LIMIT = 8;
    private static final int PANEL_LIMIT = 8;

    interface ReadData {
        Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId);

        Optional<VersionedSubject> subjectForMinecraft(UUID playerId);

        PlayerResolution resolvePlayer(String uuidOrUsername);

        Optional<PlayerIdentity> player(UUID playerId);

        long linkHistoryCountForDiscord(DiscordUserId userId);

        ModerationHistoryPage historyPage(
                UUID targetId,
                int page,
                int pageSize,
                HistoryQueryOptions options
        );

        List<CaseReview> recentCases(UUID targetId, int limit);

        Optional<CaseReview> caseReview(CaseId caseId);

        List<ActiveSanction> activeSanctions(UUID targetId, Instant now);

        List<StaffNote> recentNotes(UUID targetId, int limit);
    }

    enum TargetKind {
        DISCORD,
        MINECRAFT
    }

    record Target(
            TargetKind kind,
            Optional<DiscordUserId> discordId,
            Optional<UUID> minecraftId,
            Optional<VersionedSubject> subject
    ) {
        Target {
            if (kind == null || discordId == null || minecraftId == null || subject == null) {
                throw new IllegalArgumentException("target fields must be present");
            }
        }
    }

    sealed interface MinecraftResolution {
        record Resolved(Target target) implements MinecraftResolution {
        }

        record Ambiguous(List<PlayerIdentity> matches, boolean truncated) implements MinecraftResolution {
            public Ambiguous(List<PlayerIdentity> matches, boolean truncated) {
                this.matches = List.copyOf(matches);
                this.truncated = truncated;
            }
        }

        record Missing() implements MinecraftResolution {
        }
    }

    record LinkedMinecraft(UUID playerId, Optional<String> username, PlayerPlatform platform, boolean main) {
    }

    record Snapshot(
            Target target,
            List<LinkedMinecraft> linkedMinecraft,
            List<ActiveSanction> activeMinecraftSanctions,
            List<ModerationHistoryEntry> recentHistory,
            List<StaffNote> recentNotes,
            List<CaseReview> recentCases,
            long historicalLinkCount
    ) {
        Snapshot(
                Target target,
                List<LinkedMinecraft> linkedMinecraft,
                List<ActiveSanction> activeMinecraftSanctions,
                List<ModerationHistoryEntry> recentHistory,
                List<StaffNote> recentNotes,
                List<CaseReview> recentCases,
                long historicalLinkCount
        ) {
            this.target = target;
            this.linkedMinecraft = List.copyOf(linkedMinecraft);
            this.activeMinecraftSanctions = List.copyOf(activeMinecraftSanctions);
            this.recentHistory = List.copyOf(recentHistory);
            this.recentNotes = List.copyOf(recentNotes);
            this.recentCases = List.copyOf(recentCases);
            this.historicalLinkCount = historicalLinkCount;
        }
    }

    static final class TooManyLinksException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TooManyLinksException() {
            super("moderation subject exceeds the bounded linked-account limit");
        }
    }

    private final ReadData data;
    private final Clock clock;

    StaffModerationReadService(DiscordStaffReadRuntime data, Clock clock) {
        this(new RuntimeReadData(data), clock);
    }

    StaffModerationReadService(ReadData data, Clock clock) {
        if (data == null || clock == null) {
            throw new IllegalArgumentException("read service dependencies must be present");
        }
        this.data = data;
        this.clock = clock;
    }

    Target discordTarget(DiscordUserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Discord target must be present");
        }
        return checked(new Target(
                TargetKind.DISCORD,
                Optional.of(userId),
                Optional.empty(),
                data.subjectForDiscord(userId)
        ));
    }

    MinecraftResolution resolveMinecraft(String input) {
        PlayerResolution resolution = data.resolvePlayer(input);
        if (resolution instanceof PlayerResolution.Missing) {
            return new MinecraftResolution.Missing();
        }
        if (resolution instanceof PlayerResolution.Ambiguous ambiguous) {
            return new MinecraftResolution.Ambiguous(ambiguous.matches(), ambiguous.truncated());
        }
        PlayerResolution.Resolved resolved = (PlayerResolution.Resolved) resolution;
        return new MinecraftResolution.Resolved(minecraftTarget(resolved.identity().playerId()));
    }

    Target minecraftTarget(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Minecraft target must be present");
        }
        Optional<VersionedSubject> subject = data.subjectForMinecraft(playerId);
        Optional<DiscordUserId> discord = subject
                .flatMap(value -> value.subject().discordUserIds().stream().findFirst());
        return checked(new Target(
                TargetKind.MINECRAFT,
                discord,
                Optional.of(playerId),
                subject
        ));
    }

    Optional<CaseReview> caseReview(CaseId caseId) {
        return data.caseReview(caseId);
    }

    Snapshot snapshot(Target target) {
        Target checkedTarget = checked(target);
        if (checkedTarget.subject().isEmpty()) {
            return emptySnapshot(checkedTarget);
        }
        ModerationSubject subject = checkedTarget.subject().orElseThrow().subject();
        Set<UUID> accountIds = subject.minecraftAccountIds();
        Instant now = clock.instant();
        return new Snapshot(
                checkedTarget,
                linkedAccounts(subject, accountIds),
                activeSanctions(accountIds, now),
                recentHistory(accountIds),
                recentNotes(accountIds),
                recentCases(accountIds),
                historicalLinkCount(checkedTarget)
        );
    }

    private static Snapshot emptySnapshot(Target target) {
        return new Snapshot(target, List.of(), List.of(), List.of(), List.of(), List.of(), 0L);
    }

    private List<LinkedMinecraft> linkedAccounts(ModerationSubject subject, Set<UUID> accountIds) {
        return accountIds.stream()
                .map(id -> linkedAccount(subject, id))
                .sorted(Comparator.comparing(
                        value -> value.username().orElse(value.playerId().toString()),
                        String.CASE_INSENSITIVE_ORDER
                ))
                .toList();
    }

    private List<ActiveSanction> activeSanctions(Set<UUID> accountIds, Instant now) {
        return accountIds.stream()
                .flatMap(id -> data.activeSanctions(id, now).stream())
                .sorted(Comparator.comparing(ActiveSanction::issuedAt).reversed())
                .limit(PANEL_LIMIT)
                .toList();
    }

    private List<ModerationHistoryEntry> recentHistory(Set<UUID> accountIds) {
        return accountIds.stream()
                .flatMap(id -> history(id).stream())
                .sorted(Comparator.comparing(ModerationHistoryEntry::occurredAt).reversed())
                .limit(PANEL_LIMIT)
                .toList();
    }

    private List<StaffNote> recentNotes(Set<UUID> accountIds) {
        return accountIds.stream()
                .flatMap(id -> data.recentNotes(id, PER_ACCOUNT_LIMIT).stream())
                .sorted(Comparator.comparing(StaffNote::createdAt).reversed())
                .limit(PANEL_LIMIT)
                .toList();
    }

    private List<CaseReview> recentCases(Set<UUID> accountIds) {
        return accountIds.stream()
                .flatMap(id -> data.recentCases(id, PER_ACCOUNT_LIMIT).stream())
                .sorted(Comparator.comparing(CaseReview::issuedAt).reversed())
                .limit(PANEL_LIMIT)
                .toList();
    }

    private long historicalLinkCount(Target target) {
        return target.discordId().map(data::linkHistoryCountForDiscord).orElse(0L);
    }

    private List<ModerationHistoryEntry> history(UUID playerId) {
        return data.historyPage(
                playerId,
                1,
                PER_ACCOUNT_LIMIT,
                HistoryQueryOptions.publicStaffView(true, true)
        ).entries();
    }

    private LinkedMinecraft linkedAccount(ModerationSubject subject, UUID playerId) {
        Optional<PlayerIdentity> identity = data.player(playerId);
        boolean main = subject.mainMinecraftAccount()
                .map(account -> account.playerId().equals(playerId))
                .orElse(false);
        return identity
                .map(value -> new LinkedMinecraft(
                        playerId,
                        value.currentUsername(),
                        value.platform(),
                        main
                ))
                .orElseGet(() -> new LinkedMinecraft(
                        playerId,
                        Optional.empty(),
                        PlayerPlatform.UNKNOWN,
                        main
                ));
    }

    private Target checked(Target target) {
        if (target == null) {
            throw new IllegalArgumentException("target must be present");
        }
        target.subject().ifPresent(subject -> {
            if (subject.subject().minecraftAccountIds().size() > MAX_LINKED_ACCOUNTS) {
                throw new TooManyLinksException();
            }
        });
        return target;
    }

    private static final class RuntimeReadData implements ReadData {
        private final DiscordStaffReadRuntime runtime;

        private RuntimeReadData(DiscordStaffReadRuntime runtime) {
            if (runtime == null) {
                throw new IllegalArgumentException("read runtime must be present");
            }
            this.runtime = runtime;
        }

        @Override
        public Optional<VersionedSubject> subjectForDiscord(DiscordUserId userId) {
            return runtime.subjectForDiscord(userId);
        }

        @Override
        public Optional<VersionedSubject> subjectForMinecraft(UUID playerId) {
            return runtime.subjectForMinecraft(playerId);
        }

        @Override
        public PlayerResolution resolvePlayer(String uuidOrUsername) {
            return runtime.resolvePlayer(uuidOrUsername);
        }

        @Override
        public Optional<PlayerIdentity> player(UUID playerId) {
            return runtime.player(playerId);
        }

        @Override
        public long linkHistoryCountForDiscord(DiscordUserId userId) {
            return runtime.linkHistoryCountForDiscord(userId);
        }

        @Override
        public ModerationHistoryPage historyPage(
                UUID targetId,
                int page,
                int pageSize,
                HistoryQueryOptions options
        ) {
            return runtime.historyPage(targetId, page, pageSize, options);
        }

        @Override
        public List<CaseReview> recentCases(UUID targetId, int limit) {
            return runtime.recentCases(targetId, limit);
        }

        @Override
        public Optional<CaseReview> caseReview(CaseId caseId) {
            return runtime.caseReview(caseId);
        }

        @Override
        public List<ActiveSanction> activeSanctions(UUID targetId, Instant now) {
            return runtime.activeSanctions(targetId, now);
        }

        @Override
        public List<StaffNote> recentNotes(UUID targetId, int limit) {
            return runtime.recentNotes(targetId, limit);
        }
    }
}
