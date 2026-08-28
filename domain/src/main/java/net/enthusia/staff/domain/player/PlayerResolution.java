package net.enthusia.staff.domain.player;

import java.util.List;

public sealed interface PlayerResolution {
    int MIN_AMBIGUOUS_MATCHES = 2;
    int MAX_AMBIGUOUS_MATCHES = 25;

    record Resolved(PlayerIdentity identity, MatchKind matchKind) implements PlayerResolution {
        public Resolved {
            if (identity == null || matchKind == null) {
                throw new IllegalArgumentException("resolved player identity fields must be present");
            }
        }
    }

    record Ambiguous(List<PlayerIdentity> matches, boolean truncated) implements PlayerResolution {
        public Ambiguous {
            if (matches == null || matches.size() < MIN_AMBIGUOUS_MATCHES) {
                throw new IllegalArgumentException("ambiguous resolution requires at least two matches");
            }
            truncated = truncated || matches.size() > MAX_AMBIGUOUS_MATCHES;
            matches = List.copyOf(matches.subList(0, Math.min(matches.size(), MAX_AMBIGUOUS_MATCHES)));
        }

        public Ambiguous(List<PlayerIdentity> matches) {
            this(matches, false);
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
