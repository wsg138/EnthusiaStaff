package net.enthusia.staff.authoritybridge;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /** A quit/join event wins over an online roster that may still include a departing player. */
    static Map<UUID, Observation> mergePending(
            Map<UUID, Observation> online, Map<UUID, Observation> pending
    ) {
        Map<UUID, Observation> observations = new LinkedHashMap<>(online);
        observations.putAll(pending);
        return observations;
    }

    /** Pure bounded round-robin candidate planning; no Bukkit or database calls. */
    static LinkBatch selectLinkBatch(Map<String, UUID> links, int cursor, int limit, Set<UUID> priority) {
        if (links == null || priority == null || cursor < 0 || limit < 1) {
            throw new IllegalArgumentException("invalid transition link batch arguments");
        }
        List<UUID> sorted = links.values().stream().distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        if (sorted.isEmpty()) {
            return new LinkBatch(Map.of(), 0);
        }
        int start = cursor % sorted.size();
        int count = Math.min(sorted.size(), limit);
        Set<UUID> selected = new LinkedHashSet<>(priority);
        for (int offset = 0; offset < count; offset++) {
            selected.add(sorted.get((start + offset) % sorted.size()));
        }
        Map<String, UUID> chosen = new LinkedHashMap<>();
        links.forEach((discordId, playerId) -> {
            if (selected.contains(playerId)) {
                chosen.put(discordId, playerId);
            }
        });
        return new LinkBatch(chosen, (start + count) % sorted.size());
    }

    record LinkBatch(Map<String, UUID> links, int nextCursor) {
        LinkBatch {
            links = Map.copyOf(links);
        }
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
