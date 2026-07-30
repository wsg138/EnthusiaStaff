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
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(entries, "entries");
        List<PlayerInfoData> rewritten = new ArrayList<>(entries.size());
        boolean changed = false;
        for (PlayerInfoData entry : entries) {
            UUID targetId = entry.getProfileId();
            if (targetId == null) {
                rewritten.add(entry);
                continue;
            }
            if (!canSee.test(viewerId, targetId)) {
                changed = true;
                continue;
            }
            StaffRank rank = rankLookup.apply(targetId);
            boolean listed = entry.isListed() && !hiddenFromTab.test(targetId);
            EnumWrappers.NativeGameMode gameMode = entry.getGameMode();
            if (SpectatorTabPolicy.masksSpectatorEntry(rank)
                    && gameMode == EnumWrappers.NativeGameMode.SPECTATOR) {
                gameMode = EnumWrappers.NativeGameMode.CREATIVE;
            }
            if (listed == entry.isListed() && gameMode == entry.getGameMode()) {
                rewritten.add(entry);
                continue;
            }
            changed = true;
            rewritten.add(copy(entry, listed, gameMode));
        }
        return changed ? List.copyOf(rewritten) : entries;
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
}
