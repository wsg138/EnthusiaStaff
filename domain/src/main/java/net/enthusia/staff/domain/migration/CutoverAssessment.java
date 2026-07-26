package net.enthusia.staff.domain.migration;

import java.util.List;

public record CutoverAssessment(boolean allowed, boolean founderOverrideUsed, List<String> blockers) {
    public CutoverAssessment {
        blockers = List.copyOf(blockers);
        if (allowed && !blockers.isEmpty() && !founderOverrideUsed) {
            throw new IllegalArgumentException("ordinary allowed assessment cannot have blockers");
        }
    }
}
