package net.enthusia.staff.discordbot;

import java.util.List;
import net.enthusia.staff.domain.player.PlayerIdentity;

final class AmbiguityChoices {
    static final int MAX_CHOICES = 25;

    private AmbiguityChoices() {
    }

    static List<PlayerIdentity> bounded(List<PlayerIdentity> matches) {
        return matches.stream().limit(MAX_CHOICES).toList();
    }

    static boolean hasMore(List<PlayerIdentity> matches, boolean truncated) {
        return truncated || matches.size() > MAX_CHOICES;
    }
}
