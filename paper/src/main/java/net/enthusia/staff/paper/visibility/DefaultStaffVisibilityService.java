package net.enthusia.staff.paper.visibility;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.api.StaffVisibilityService;

public final class DefaultStaffVisibilityService implements StaffVisibilityService {
    private final Map<StaffRank, Set<StaffRank>> visibilityMatrix;
    private final Map<UUID, StaffRank> vanished = new ConcurrentHashMap<>();
    private final Map<UUID, StaffRank> viewers = new ConcurrentHashMap<>();

    public DefaultStaffVisibilityService(Map<StaffRank, Set<StaffRank>> visibilityMatrix) {
        if (visibilityMatrix == null) {
            throw new IllegalArgumentException("visibility matrix must be present");
        }
        EnumMap<StaffRank, Set<StaffRank>> copy = new EnumMap<>(StaffRank.class);
        for (StaffRank viewer : new StaffRank[]{
                StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN, StaffRank.FOUNDER
        }) {
            Set<StaffRank> visible = visibilityMatrix.get(viewer);
            if (visible == null || visible.contains(StaffRank.SYSTEM)) {
                throw new IllegalArgumentException("visibility matrix must define every staff viewer rank");
            }
            copy.put(viewer, Set.copyOf(visible));
        }
        this.visibilityMatrix = Map.copyOf(copy);
    }

    public static Map<StaffRank, Set<StaffRank>> defaultMatrix() {
        return Map.of(
                StaffRank.MOD, Set.of(StaffRank.MOD, StaffRank.DEVELOPER),
                StaffRank.DEVELOPER, Set.of(StaffRank.MOD, StaffRank.DEVELOPER),
                StaffRank.ADMIN, Set.of(StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN),
                StaffRank.FOUNDER, Set.of(StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.ADMIN, StaffRank.FOUNDER)
        );
    }

    @Override
    public boolean isVanished(UUID playerId) {
        return vanished.containsKey(playerId);
    }

    @Override
    public boolean canSee(UUID viewerId, UUID targetId) {
        StaffRank targetRank = vanished.get(targetId);
        if (targetRank == null || viewerId.equals(targetId)) {
            return true;
        }
        StaffRank viewerRank = viewers.get(viewerId);
        return viewerRank != null && visibilityMatrix.getOrDefault(viewerRank, Set.of()).contains(targetRank);
    }

    public void setVanished(UUID playerId, StaffRank rank, boolean value) {
        if (value) {
            vanished.put(playerId, rank);
        } else {
            vanished.remove(playerId);
        }
    }

    public void setViewerRank(UUID playerId, StaffRank rank) {
        viewers.put(playerId, Objects.requireNonNull(rank, "rank"));
    }

    public void removeViewer(UUID playerId) {
        viewers.remove(playerId);
    }
}
