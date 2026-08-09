package net.enthusia.staff.paper.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.enthusia.staff.domain.report.ReportDetails;

/**
 * Produces bounded, staff-only text pages from retained report evidence.
 *
 * <p>The formatter deliberately never returns the raw stored JSON. Public/private chat bodies are
 * rendered as individual bounded messages and client evidence is allow-listed so provider-specific
 * opaque metadata cannot accidentally be copied into chat.</p>
 */
public final class ReportEvidenceFormatter {
    private static final int CHAT_MESSAGES_PER_PAGE = 5;
    private static final int CLIENT_LINES_PER_PAGE = 8;
    private static final int MAX_RENDERED_FIELD = 1_000;

    private final ObjectMapper json;

    public ReportEvidenceFormatter() {
        this(new ObjectMapper());
    }

    ReportEvidenceFormatter(ObjectMapper json) {
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    public Optional<EvidenceKind> parseKind(String input) {
        if (input == null) {
            return Optional.empty();
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "public", "chat" -> Optional.of(EvidenceKind.PUBLIC_CHAT);
            case "private", "pm" -> Optional.of(EvidenceKind.PRIVATE_MESSAGES);
            case "client" -> Optional.of(EvidenceKind.CLIENT);
            default -> Optional.empty();
        };
    }

    public EvidencePage render(
            ReportDetails details,
            EvidenceKind kind,
            int requestedSnapshot,
            int requestedPage
    ) {
        validateRequest(details, kind, requestedPage);
        List<String> snapshots = snapshots(details, kind);
        if (snapshots.isEmpty()) {
            return emptyPage(kind);
        }
        int snapshot = resolveSnapshot(snapshots, requestedSnapshot);
        List<String> lines = renderSnapshot(kind, snapshots.get(snapshot - 1));
        PageSlice slice = pageSlice(lines, pageSize(kind), requestedPage);
        return new EvidencePage(
                kind,
                snapshot,
                snapshots.size(),
                requestedPage,
                slice.totalPages(),
                slice.lines()
        );
    }

    private static void validateRequest(ReportDetails details, EvidenceKind kind, int requestedPage) {
        if (details == null || kind == null) {
            throw new IllegalArgumentException("report evidence request is invalid");
        }
        if (requestedPage < 1) {
            throw new IllegalArgumentException("report evidence request is invalid");
        }
    }

    private static EvidencePage emptyPage(EvidenceKind kind) {
        return new EvidencePage(kind, 0, 0, 0, 0, List.of("No retained evidence is available."));
    }

    private static int resolveSnapshot(List<String> snapshots, int requestedSnapshot) {
        int snapshot = requestedSnapshot < 1 ? snapshots.size() : requestedSnapshot;
        if (snapshot > snapshots.size()) {
            throw new IllegalArgumentException("snapshot must be between 1 and " + snapshots.size());
        }
        return snapshot;
    }

    private static int pageSize(EvidenceKind kind) {
        return kind == EvidenceKind.CLIENT ? CLIENT_LINES_PER_PAGE : CHAT_MESSAGES_PER_PAGE;
    }

    private static PageSlice pageSlice(List<String> lines, int pageSize, int requestedPage) {
        int totalPages = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        if (requestedPage > totalPages) {
            throw new IllegalArgumentException("page must be between 1 and " + totalPages);
        }
        int from = Math.min(lines.size(), (requestedPage - 1) * pageSize);
        int to = Math.min(lines.size(), from + pageSize);
        List<String> pageLines = lines.isEmpty()
                ? List.of("The retained snapshot is empty.")
                : List.copyOf(lines.subList(from, to));
        return new PageSlice(totalPages, pageLines);
    }

    private List<String> renderSnapshot(EvidenceKind kind, String snapshot) {
        try {
            JsonNode root = json.readTree(snapshot);
            return switch (kind) {
                case PUBLIC_CHAT -> renderPublic(root);
                case PRIVATE_MESSAGES -> renderPrivate(root);
                case CLIENT -> renderClient(root);
            };
        } catch (Exception exception) {
            return List.of("Stored evidence could not be rendered safely. Use approved database tooling for recovery.");
        }
    }

    private static List<String> renderPublic(JsonNode root) {
        if (!root.isArray()) {
            return List.of("Stored public-chat evidence has an unexpected format.");
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode message : root) {
            lines.add('[' + text(message, "sentAt", "unknown-time") + "] "
                    + text(message, "senderName", "unknown-sender") + ": "
                    + text(message, "body", ""));
        }
        return List.copyOf(lines);
    }

    private static List<String> renderPrivate(JsonNode root) {
        if (!root.isArray()) {
            return List.of("Stored private-message evidence has an unexpected format.");
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode message : root) {
            lines.add('[' + text(message, "sentAt", "unknown-time") + "] "
                    + text(message, "senderName", "unknown-sender") + " -> "
                    + text(message, "recipientName", "unknown-recipient") + ": "
                    + text(message, "body", ""));
        }
        return List.copyOf(lines);
    }

    private static List<String> renderClient(JsonNode root) {
        if (!root.isObject()) {
            return List.of("Stored client evidence has an unexpected format.");
        }
        List<String> lines = new ArrayList<>();
        add(lines, root, "capturedAt", "Captured");
        add(lines, root, "platform", "Platform");
        add(lines, root, "protocolVersion", "Protocol");
        add(lines, root, "minecraftVersion", "Minecraft");
        add(lines, root, "reportedBrand", "Brand");
        add(lines, root, "viaVersionStatus", "ViaVersion");
        add(lines, root, "viaVersionPluginVersion", "ViaVersion plugin");
        add(lines, root, "floodgateStatus", "Floodgate");
        add(lines, root, "floodgatePlayer", "Floodgate player");
        add(lines, root, "bedrockVersion", "Bedrock version");
        add(lines, root, "bedrockDevice", "Bedrock device");
        add(lines, root, "geyserStatus", "Geyser");
        add(lines, root, "autoClickerStatus", "AutoClicker");
        renderAutoClickerHandshake(lines, root.path("autoClickerHandshake"));
        add(lines, root, "polarStatus", "Polar");
        if (present(root, "polarMetadata")) {
            lines.add("Polar metadata: withheld from chat presentation");
        }
        return List.copyOf(lines);
    }

    private static void renderAutoClickerHandshake(List<String> lines, JsonNode handshake) {
        if (!handshake.isObject()) {
            return;
        }
        add(lines, handshake, "modVersion", "AutoClicker mod version");
        add(lines, handshake, "loader", "AutoClicker loader");
        add(lines, handshake, "minecraftVersion", "AutoClicker Minecraft");
        add(lines, handshake, "receivedAt", "AutoClicker received");
    }

    private static void add(List<String> lines, JsonNode root, String field, String label) {
        JsonNode value = root.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isTextual() && !value.isNumber() && !value.isBoolean()) {
            lines.add(label + ": withheld (unexpected structured value)");
            return;
        }
        lines.add(label + ": " + bounded(value.asText()));
    }

    private static boolean present(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return !value.isMissingNode() && !value.isNull();
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return bounded(value.isTextual() ? value.asText() : fallback);
    }

    private static String bounded(String value) {
        String singleLine = value.replace('\r', ' ').replace('\n', ' ');
        return singleLine.length() <= MAX_RENDERED_FIELD
                ? singleLine
                : singleLine.substring(0, MAX_RENDERED_FIELD) + "…";
    }

    private static List<String> snapshots(ReportDetails details, EvidenceKind kind) {
        return switch (kind) {
            case PUBLIC_CHAT -> details.publicChatSnapshots();
            case PRIVATE_MESSAGES -> details.privateMessageSnapshots();
            case CLIENT -> details.clientEvidenceSnapshots();
        };
    }

    public enum EvidenceKind {
        PUBLIC_CHAT("public"),
        PRIVATE_MESSAGES("private"),
        CLIENT("client");

        private final String commandName;

        EvidenceKind(String commandName) {
            this.commandName = commandName;
        }

        public String commandName() {
            return commandName;
        }
    }

    private record PageSlice(int totalPages, List<String> lines) {
        private PageSlice {
            lines = List.copyOf(lines);
        }
    }

    public record EvidencePage(
            EvidenceKind kind,
            int snapshot,
            int totalSnapshots,
            int page,
            int totalPages,
            List<String> lines
    ) {
        public EvidencePage {
            if (kind == null || totalSnapshots < 0 || snapshot < 0 || page < 0 || totalPages < 0 || lines == null) {
                throw new IllegalArgumentException("report evidence page is invalid");
            }
            lines = List.copyOf(lines);
        }
    }
}