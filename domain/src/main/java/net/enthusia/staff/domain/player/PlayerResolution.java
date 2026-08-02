package net.enthusia.staff.domain.player;

import java.util.List;

public sealed interface PlayerResolution {
    record Resolved(PlayerIdentity identity, MatchKind matchKind) implements PlayerResolution {
        public Resolved {
            if (identity == null || matchKind == null) {
                throw new IllegalArgumentException("resolved player identity fields must be present");
            }
        }
    }

    record Ambiguous(List<PlayerIdentity> matches) implements PlayerResolution {
        public Ambiguous {
            if (matches == null || matches.size() < 2) {
                throw new IllegalArgumentException("ambiguous resolution requires at least two matches");
            }
            matches = List.copyOf(matches);
        }
    }

    record Missing() implements PlayerResolution {
    }

    enum MatchKind {
        UUID,
        CURRENT_USERNAME,
        HISTORICAL_USERNAME
    }
}
