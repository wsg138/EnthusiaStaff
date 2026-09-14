package net.enthusia.staff.paper.staff;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable server-owned state for the staff-tools inventory views. */
sealed interface StaffToolsMenuView {
    int TARGET_PAGE_SIZE = 45;
    int MAX_TARGETS = TARGET_PAGE_SIZE * 4;

    UUID viewerId();

    record Root(UUID viewerId, List<StaffToolDefinition> tools) implements StaffToolsMenuView {
        public Root {
            if (viewerId == null || tools == null || tools.isEmpty()) {
                throw new IllegalArgumentException("staff tools root view must contain a viewer and tools");
            }
            Set<StaffToolDefinition> distinct = new HashSet<>(tools);
            if (distinct.size() != tools.size() || tools.contains(StaffToolDefinition.STAFF_TOOLS)) {
                throw new IllegalArgumentException("staff tools root view contains an invalid tool list");
            }
            tools = List.copyOf(tools);
        }

        StaffToolDefinition toolAt(int index) {
            if (index < 0 || index >= tools.size()) {
                return null;
            }
            return tools.get(index);
        }
    }

    record Loading(UUID viewerId, StaffToolDefinition tool) implements StaffToolsMenuView {
        public Loading {
            if (viewerId == null || tool == null || !tool.targetRequired()) {
                throw new IllegalArgumentException("staff tools loading view is invalid");
            }
        }
    }

    record TargetEntry(UUID playerId, String playerName) {
        public TargetEntry {
            if (playerId == null || playerName == null || playerName.isBlank()) {
                throw new IllegalArgumentException("staff tools target identity must be present");
            }
            playerName = playerName.trim();
        }
    }

    record TargetPicker(
            UUID viewerId,
            StaffToolDefinition tool,
            List<TargetEntry> targets,
            int page,
            boolean truncated
    ) implements StaffToolsMenuView {
        public TargetPicker {
            if (viewerId == null || tool == null || !tool.targetRequired() || targets == null || page < 0) {
                throw new IllegalArgumentException("staff tools target picker state is invalid");
            }
            if (targets.size() > MAX_TARGETS) {
                throw new IllegalArgumentException("staff tools target picker must remain bounded");
            }
            Set<UUID> targetIds = new HashSet<>();
            for (TargetEntry target : targets) {
                if (target == null || viewerId.equals(target.playerId()) || !targetIds.add(target.playerId())) {
                    throw new IllegalArgumentException("staff tools target picker contains an invalid target");
                }
            }
            targets = List.copyOf(targets);
            page = Math.min(page, lastPage(targets));
        }

        static TargetPicker fromCandidates(
                UUID viewerId,
                StaffToolDefinition tool,
                List<TargetEntry> candidates,
                int requestedPage
        ) {
            if (viewerId == null || tool == null || candidates == null || requestedPage < 0) {
                throw new IllegalArgumentException("staff tools target picker candidates are invalid");
            }
            List<TargetEntry> ordered = normalize(viewerId, candidates);
            boolean truncated = ordered.size() > MAX_TARGETS;
            return new TargetPicker(
                    viewerId,
                    tool,
                    ordered.stream().limit(MAX_TARGETS).toList(),
                    requestedPage,
                    truncated
            );
        }

        List<TargetEntry> pageTargets() {
            int start = page * TARGET_PAGE_SIZE;
            int end = Math.min(start + TARGET_PAGE_SIZE, targets.size());
            return targets.subList(start, end);
        }

        TargetEntry targetAtPageIndex(int index) {
            List<TargetEntry> visible = pageTargets();
            if (index < 0 || index >= visible.size()) {
                return null;
            }
            return visible.get(index);
        }

        boolean hasPreviousPage() {
            return page > 0;
        }

        boolean hasNextPage() {
            return page < lastPage(targets);
        }

        int totalPages() {
            return lastPage(targets) + 1;
        }

        private static List<TargetEntry> normalize(UUID viewerId, List<TargetEntry> candidates) {
            List<TargetEntry> normalized = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (TargetEntry target : candidates) {
                if (target != null && !viewerId.equals(target.playerId()) && seen.add(target.playerId())) {
                    normalized.add(target);
                }
            }
            normalized.sort(Comparator.comparing(TargetEntry::playerName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(TargetEntry::playerId));
            return List.copyOf(normalized);
        }

        private static int lastPage(List<TargetEntry> targets) {
            return targets.isEmpty() ? 0 : (targets.size() - 1) / TARGET_PAGE_SIZE;
        }
    }

    static Root root(UUID viewerId, List<StaffToolDefinition> tools) {
        return new Root(viewerId, Objects.requireNonNull(tools, "tools"));
    }
}
