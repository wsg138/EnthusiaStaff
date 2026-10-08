package net.enthusia.staff.paper.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import net.enthusia.staff.domain.auth.StaffRank;

/**
 * Read-only preflight comparator of preview rank grants against a caller-provided
 * legacy rank-only oracle. This is NOT a permission decision or runtime gate.
 */
public final class RankCapabilityShadowAudit {
    private RankCapabilityShadowAudit() {
    }

    public static List<Mismatch> comparePlayerRanks(
            RankConfigurationSnapshot candidate,
            BiPredicate<StaffRank, StaffCapability> legacyRankOnly
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(legacyRankOnly, "legacyRankOnly");
        List<Mismatch> differences = new ArrayList<>();
        for (StaffRank rank : StaffRank.values()) {
            // SYSTEM is not a player identity. Its low-level Spectator fallback
            // must not become a configurable player capability.
            if (rank == StaffRank.SYSTEM) {
                continue;
            }
            for (StaffCapability capability : StaffCapability.values()) {
                boolean existing = legacyRankOnly.test(rank, capability);
                boolean proposed = candidate.proposes(rank, capability);
                if (existing != proposed) {
                    differences.add(new Mismatch(rank, capability, existing, proposed));
                }
            }
        }
        return List.copyOf(differences);
    }

    public record Mismatch(
            StaffRank rank,
            StaffCapability capability,
            boolean legacyAllowed,
            boolean candidateAllowed
    ) {
    }
}
