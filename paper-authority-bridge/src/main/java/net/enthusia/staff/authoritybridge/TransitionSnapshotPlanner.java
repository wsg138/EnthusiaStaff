package net.enthusia.staff.authoritybridge;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pure validation/planning boundary between Bukkit observations and transition persistence. */
final class TransitionSnapshotPlanner {
    private static final Pattern JAVA_NAME = Pattern.compile("[A-Za-z0-9_]{1,32}");
    private static final Pattern BEDROCK_NAME = Pattern.compile("\\*[A-Za-z0-9_]{1,31}");

    private TransitionSnapshotPlanner() {
    }

    static Plan plan(Map<String, UUID> links, Collection<Observation> observations) {
        if (links == null || observations == null) {
            throw new IllegalArgumentException("transition snapshot inputs must be present");
        }
        Map<UUID, Observation> usable = usableObservations(observations);
        Map<String, UUID> importable = new LinkedHashMap<>();
        links.forEach((discordId, playerId) -> {
            if (playerId != null && usable.containsKey(playerId)) {
                importable.put(discordId, playerId);
            }
        });
        return new Plan(
                List.copyOf(usable.values()),
                Map.copyOf(importable),
                links.size() - importable.size());
    }

    private static Map<UUID, Observation> usableObservations(Collection<Observation> observations) {
        Map<UUID, Observation> usable = new LinkedHashMap<>();
        for (Observation observation : observations) {
            if (valid(observation)) {
                usable.put(observation.playerId(), observation);
            }
        }
        return usable;
    }

    private static boolean valid(Observation observation) {
        if (observation == null || observation.playerId() == null || observation.seenAt() == null) {
            return false;
        }
        String username = observation.username();
        return username != null && (JAVA_NAME.matcher(username).matches() || BEDROCK_NAME.matcher(username).matches());
    }

    record Observation(UUID playerId, String username, Instant seenAt, boolean online) {
    }

    record Plan(List<Observation> observations, Map<String, UUID> importableLinks, int skippedLinks) {
        Plan {
            observations = List.copyOf(observations);
            importableLinks = Map.copyOf(importableLinks);
            if (skippedLinks < 0) {
                throw new IllegalArgumentException("skipped link count must not be negative");
            }
        }
    }
}
