package net.enthusia.staff.paper.visibility;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.enthusia.staff.domain.auth.StaffRank;

final class PlayerInfoTabMasker {
    private final BiPredicate<UUID, UUID> canSee;
    private final Function<UUID, StaffRank> rankLookup;
    private final Predicate<UUID> hiddenFromTab;

    PlayerInfoTabMasker(
            BiPredicate<UUID, UUID> canSee,
            Function<UUID, StaffRank> rankLookup,
            Predicate<UUID> hiddenFromTab
    ) {
        this.canSee = Objects.requireNonNull(canSee, "canSee");
        this.rankLookup = Objects.requireNonNull(rankLookup, "rankLookup");
        this.hiddenFromTab = Objects.requireNonNull(hiddenFromTab, "hiddenFromTab");
    }

    List<PlayerInfoData> rewrite(UUID viewerId, List<PlayerInfoData> entries) {
        return rewriteResult(viewerId, entries).entries();
    }

    RewriteResult rewriteResult(UUID viewerId, List<PlayerInfoData> entries) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(entries, "entries");
        List<PlayerInfoData> rewritten = new ArrayList<>(entries.size());
        boolean changed = false;
        for (PlayerInfoData entry : entries) {
            EntryRewrite result = rewriteEntry(viewerId, entry);
            changed |= result.changed();
            if (result.entry() != null) {
                rewritten.add(result.entry());
            }
        }
        return changed
                ? new RewriteResult(List.copyOf(rewritten), true)
                : new RewriteResult(entries, false);
    }

    private EntryRewrite rewriteEntry(UUID viewerId, PlayerInfoData entry) {
        UUID targetId = entry.getProfileId();
        if (targetId == null) {
            return EntryRewrite.unchanged(entry);
        }
        if (!canSee.test(viewerId, targetId)) {
            return EntryRewrite.removed();
        }
        StaffRank rank = rankLookup.apply(targetId);
        boolean listed = entry.isListed() && !hiddenFromTab.test(targetId);
        EnumWrappers.NativeGameMode gameMode = maskedGameMode(rank, entry.getGameMode());
        if (listed == entry.isListed() && gameMode == entry.getGameMode()) {
            return EntryRewrite.unchanged(entry);
        }
        return EntryRewrite.changed(copy(entry, listed, gameMode));
    }

    private static EnumWrappers.NativeGameMode maskedGameMode(
            StaffRank rank,
            EnumWrappers.NativeGameMode gameMode
    ) {
        return SpectatorTabPolicy.masksSpectatorEntry(rank)
                && gameMode == EnumWrappers.NativeGameMode.SPECTATOR
                ? EnumWrappers.NativeGameMode.CREATIVE
                : gameMode;
    }

    private static PlayerInfoData copy(
            PlayerInfoData source,
            boolean listed,
            EnumWrappers.NativeGameMode gameMode
    ) {
        return new PlayerInfoData(
                source.getProfileId(),
                source.getLatency(),
                listed,
                gameMode,
                source.getProfile(),
                source.getDisplayName(),
                source.isShowHat(),
                source.getListOrder(),
                source.getRemoteChatSessionData()
        );
    }

    record RewriteResult(List<PlayerInfoData> entries, boolean changed) {
    }

    private record EntryRewrite(PlayerInfoData entry, boolean changed) {
        private static EntryRewrite unchanged(PlayerInfoData entry) {
            return new EntryRewrite(entry, false);
        }

        private static EntryRewrite changed(PlayerInfoData entry) {
            return new EntryRewrite(entry, true);
        }

        private static EntryRewrite removed() {
            return new EntryRewrite(null, true);
        }
    }
}
