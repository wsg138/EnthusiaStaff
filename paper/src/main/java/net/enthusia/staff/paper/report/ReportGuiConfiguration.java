package net.enthusia.staff.paper.report;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

public final class ReportGuiConfiguration {
    public static final Set<String> SLOT_KEYS = Set.of(
            "queue-open", "queue-mine", "queue-claimed", "queue-review", "queue-closed",
            "refresh", "back", "close", "previous", "next", "confirm", "empty",
            "detail-header", "detail-reporter", "detail-target", "detail-location",
            "detail-evidence", "detail-description", "detail-public-chat",
            "detail-private-message", "detail-client-evidence",
            "review-report", "review-action", "review-note"
    );
    public static final Set<String> MATERIAL_KEYS = Set.of(
            "filler", "empty", "refresh", "back", "close", "previous", "next", "confirm",
            "active-queue", "queue-open", "queue-mine", "queue-claimed", "queue-review",
            "queue-closed", "reporter", "target", "location", "evidence", "description",
            "public-chat", "private-message", "client-evidence", "private-note",
            "state-open", "state-claimed", "state-awaiting-review", "state-closed",
            "state-no-violation", "action-claim", "action-await-review", "action-close",
            "action-no-violation"
    );
    public static final Set<String> TITLE_KEYS = Set.of("queue", "detail", "review");
    public static final Set<String> MESSAGE_KEYS = Set.of(
            "queue-open", "queue-mine", "queue-claimed", "queue-review", "queue-closed",
            "refresh-queue", "reload-report", "back-queue", "close", "previous-page", "next-page",
            "empty-title", "empty-lore", "click-inspect", "current-queue", "click-open",
            "reporter", "target", "location-context", "captured-evidence", "sensitive-evidence",
            "description", "public-chat-snapshots", "private-message-snapshots",
            "client-evidence-snapshots", "private-note", "private-note-required",
            "back-no-change", "confirm-action", "confirm-revision", "stale-rejected",
            "close-no-change", "snapshot-count", "snapshot-none", "snapshot-protected",
            "action-claim", "action-claim-description", "action-await-review",
            "action-await-review-description", "action-close", "action-close-description",
            "action-no-violation", "action-no-violation-description"
    );

    private final int inventorySize;
    private final List<Integer> contentSlots;
    private final List<Integer> actionSlots;
    private final Map<String, Integer> slots;
    private final Map<String, Material> materials;
    private final Map<String, String> titles;
    private final Map<String, String> messages;

    public ReportGuiConfiguration(
            int inventorySize,
            List<Integer> contentSlots,
            List<Integer> actionSlots,
            Map<String, Integer> slots,
            Map<String, Material> materials,
            Map<String, String> titles,
            Map<String, String> messages
    ) {
        if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
            throw new IllegalArgumentException("report GUI inventory size must be a multiple of 9 from 9 to 54");
        }
        this.inventorySize = inventorySize;
        this.contentSlots = immutableSlots(contentSlots, inventorySize, "content-slots");
        this.actionSlots = immutableSlots(actionSlots, inventorySize, "action-slots");
        if (this.contentSlots.isEmpty()) {
            throw new IllegalArgumentException("report GUI content-slots must not be empty");
        }
        if (this.actionSlots.size() < 3) {
            throw new IllegalArgumentException("report GUI action-slots must contain at least three slots");
        }
        this.slots = immutableRequiredMap(slots, SLOT_KEYS, "slots");
        this.materials = immutableRequiredMap(materials, MATERIAL_KEYS, "materials");
        this.titles = immutableRequiredTextMap(titles, TITLE_KEYS, "titles");
        this.messages = immutableRequiredTextMap(messages, MESSAGE_KEYS, "messages");
        validateNamedSlots();
        validateLayouts();
    }

    public int inventorySize() {
        return inventorySize;
    }

    public int pageSize() {
        return contentSlots.size();
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public List<Integer> actionSlots() {
        return actionSlots;
    }

    public int slot(String key) {
        Integer value = slots.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown report GUI slot key " + key);
        }
        return value;
    }

    public Material material(String key) {
        Material value = materials.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown report GUI material key " + key);
        }
        return value;
    }

    public String title(String key) {
        String value = titles.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown report GUI title key " + key);
        }
        return value;
    }

    public String message(String key) {
        String value = messages.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown report GUI message key " + key);
        }
        return value;
    }

    private void validateNamedSlots() {
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            int slot = entry.getValue();
            if (slot < 0 || slot >= inventorySize) {
                throw new IllegalArgumentException(
                        "report GUI slot " + entry.getKey() + " must be inside the configured inventory"
                );
            }
        }
        if (!contentSlots.contains(slot("empty"))) {
            throw new IllegalArgumentException("report GUI empty slot must be one of the content slots");
        }
    }

    private void validateLayouts() {
        requireUnique("queue", combine(
                contentSlots,
                namedSlots("queue-open", "queue-mine", "queue-claimed", "queue-review", "queue-closed",
                        "refresh", "close", "previous", "next")
        ));
        requireUnique("detail", combine(
                namedSlots(
                        "detail-header", "detail-reporter", "detail-target", "detail-location",
                        "detail-evidence", "detail-description", "detail-public-chat",
                        "detail-private-message", "detail-client-evidence",
                        "queue-open", "queue-mine", "queue-claimed", "queue-review", "queue-closed",
                        "refresh", "back", "close"
                ),
                actionSlots
        ));
        requireUnique("review", namedSlots(
                "review-report", "review-action", "review-note", "back", "confirm", "close"
        ));
    }

    private List<Integer> namedSlots(String... keys) {
        List<Integer> values = new ArrayList<>(keys.length);
        for (String key : keys) {
            values.add(slot(key));
        }
        return values;
    }

    private static List<Integer> combine(List<Integer> first, List<Integer> second) {
        List<Integer> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    private static void requireUnique(String layout, List<Integer> values) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("report GUI " + layout + " layout contains overlapping slots");
        }
    }

    private static List<Integer> immutableSlots(List<Integer> values, int size, String path) {
        Objects.requireNonNull(values, path);
        List<Integer> copy = List.copyOf(values);
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("report GUI " + path + " contains duplicate slots");
        }
        for (Integer slot : copy) {
            if (slot == null || slot < 0 || slot >= size) {
                throw new IllegalArgumentException("report GUI " + path + " contains an invalid slot");
            }
        }
        return copy;
    }

    private static <T> Map<String, T> immutableRequiredMap(
            Map<String, T> values,
            Set<String> required,
            String path
    ) {
        Objects.requireNonNull(values, path);
        if (!values.keySet().equals(required) || values.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("report GUI " + path + " must contain exactly the supported keys");
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> immutableRequiredTextMap(
            Map<String, String> values,
            Set<String> required,
            String path
    ) {
        Map<String, String> copy = immutableRequiredMap(values, required, path);
        if (copy.values().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("report GUI " + path + " values must not be blank");
        }
        return copy;
    }
}
