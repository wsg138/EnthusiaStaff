package net.enthusia.staff.paper.config;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.enthusia.staff.domain.auth.StaffRank;

/**
 * Immutable, validated read-only capability candidate. Never an authority source.
 */
public record RankConfigurationSnapshot(
        int schemaVersion,
        Map<StaffRank, Set<StaffRank>> inherits,
        Map<StaffRank, Set<StaffCapability>> grants
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RankConfigurationSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported rank schema version " + schemaVersion);
        }
        inherits = immutableMatrix(inherits, "inherits");
        grants = immutableMatrix(grants, "grants");
        for (StaffRank rank : StaffRank.values()) {
            for (StaffRank ancestor : inherits.get(rank)) {
                if (rank == ancestor || !rank.atLeast(ancestor)) {
                    throw new IllegalArgumentException(rank + " cannot inherit from " + ancestor);
                }
            }
        }
        if (!inherits.get(StaffRank.SYSTEM).isEmpty() || !grants.get(StaffRank.SYSTEM).isEmpty()) {
            throw new IllegalArgumentException("SYSTEM must not inherit or grant player capabilities");
        }
        if (!inherits.get(StaffRank.DEVELOPER).isEmpty()) {
            throw new IllegalArgumentException("DEVELOPER must remain an isolated technical rank");
        }
    }

    private static <V> Map<StaffRank, Set<V>> immutableMatrix(
            Map<StaffRank, Set<V>> matrix, String label
    ) {
        Objects.requireNonNull(matrix, label);
        if (!matrix.keySet().equals(EnumSet.allOf(StaffRank.class))) {
            throw new IllegalArgumentException(label + " must list every rank exactly once");
        }
        EnumMap<StaffRank, Set<V>> copy = new EnumMap<>(StaffRank.class);
        for (StaffRank rank : StaffRank.values()) {
            copy.put(rank, Set.copyOf(Objects.requireNonNull(matrix.get(rank), label + "." + rank)));
        }
        return Map.copyOf(copy);
    }

    /** Computes candidate transitive grants; does not grant runtime permissions. */
    public Set<StaffCapability> effectiveCapabilities(StaffRank rank) {
        if (rank == null || rank == StaffRank.SYSTEM) {
            return Set.of();
        }
        EnumSet<StaffCapability> result = EnumSet.noneOf(StaffCapability.class);
        gather(rank, result);
        return Set.copyOf(result);
    }

    public boolean proposes(StaffRank rank, StaffCapability capability) {
        return capability != null && effectiveCapabilities(rank).contains(capability);
    }

    private void gather(StaffRank rank, Set<StaffCapability> result) {
        result.addAll(grants.get(rank));
        for (StaffRank parent : inherits.get(rank)) {
            gather(parent, result);
        }
    }
}
