package net.enthusia.staff.paper.integration;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.enthusia.staff.api.chat.RichChatArtifact;
import net.enthusia.staff.api.chat.RichChatArtifactProvider;
import net.enthusia.staff.api.chat.RichChatArtifactRequest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Self-contained text/slot-card rich artifact renderer. Requires InteractiveChat for the actual
 * configured placeholder expressions, but neither DiscordSRV nor its image-rendering addon.
 *
 * <p>This deliberately does NOT promise upstream pixel/texture fidelity. Item material names,
 * counts and inventory positions are represented visibly without redistributing game assets.
 * Opt-in, with original addon rendering still preferred whenever available.</p>
 */
public final class IndependentRichChatArtifactProvider
        implements RichChatArtifactProvider, AutoCloseable {
    private static final int COLUMNS = 9;
    private static final int CELL_WIDTH = 84;
    private static final int CELL_HEIGHT = 62;
    private static final int TOP = 55;
    private static final int MAX_SLOTS = 54;
    private static final Color BACKGROUND = new Color(32, 35, 42);
    private static final Color CELL = new Color(51, 55, 66);
    private static final Color OUTLINE = new Color(98, 105, 120);
    private static final Color TEXT = new Color(238, 241, 245);
    private static final Color SUBTEXT = new Color(187, 196, 209);

    enum Kind {
        ITEM("interactivechat.module.item", RichChatArtifact.Kind.ITEM, "Item", "Shared item"),
        INVENTORY("interactivechat.module.inventory", RichChatArtifact.Kind.INVENTORY,
                "Inventory", "Shared inventory"),
        ENDER_CHEST("interactivechat.module.enderchest", RichChatArtifact.Kind.ENDER_CHEST,
                "EnderChest", "Shared Ender chest");

        final String permission;
        final RichChatArtifact.Kind artifactKind;
        final String title;
        final String alt;

        Kind(String permission, RichChatArtifact.Kind artifactKind, String title, String alt) {
            this.permission = permission;
            this.artifactKind = artifactKind;
            this.title = title;
            this.alt = alt;
        }
    }

    record Slot(String material, int amount) {
        Slot {
            material = Objects.requireNonNull(material, "material");
            if (material.length() > 64 || amount < 0 || amount > 127) {
                throw new IllegalArgumentException("invalid inventory slot");
            }
        }

        static Slot empty() {
            return new Slot("", 0);
        }
    }

    record Job(Kind kind, int position, List<Slot> slots) {
        Job {
            Objects.requireNonNull(kind, "kind");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            if (position < 0 || position > 2_000 || slots.size() > MAX_SLOTS) {
                throw new IllegalArgumentException("invalid rich render job");
            }
        }
    }

    record MatchRule(Kind kind, Pattern expression, boolean enabled) {
        MatchRule {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(expression, "expression");
        }
    }

    public record Discovery(Optional<IndependentRichChatArtifactProvider> integration, String issue) {
        public Discovery {
            integration = Objects.requireNonNull(integration, "integration");
            issue = Objects.requireNonNull(issue, "issue");
        }
    }

    private final JavaPlugin plugin;
    private final Clock clock;
    private final ExecutorService workers;
    private final ServicesManager services;
    private final List<MatchRule> rules;
    private volatile boolean closed;

    private IndependentRichChatArtifactProvider(
            JavaPlugin plugin, Clock clock, ExecutorService workers, List<MatchRule> rules
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.rules = List.copyOf(rules);
        services = plugin.getServer().getServicesManager();
        services.register(RichChatArtifactProvider.class, this, plugin, ServicePriority.Lowest);
    }

    public static Discovery discoverAndRegister(
            JavaPlugin plugin, Clock clock, ExecutorService workers
    ) {
        Plugin interactiveChat = plugin.getServer().getPluginManager().getPlugin(
                InteractiveChatStagingArtifactProvider.INTERACTIVE_CHAT);
        if (interactiveChat == null || !interactiveChat.isEnabled()) {
            return new Discovery(Optional.empty(), "");
        }
        try {
            List<MatchRule> rules = discoverRules(interactiveChat);
            return new Discovery(Optional.of(new IndependentRichChatArtifactProvider(
                    plugin, clock, workers, rules)), "");
        } catch (ReflectiveOperationException | LinkageError failure) {
            return new Discovery(Optional.empty(), "Independent InteractiveChat placeholder API "
                    + failure.getClass().getSimpleName());
        }
    }

    // A Bukkit plugin's class loader must resolve that plugin's private API, not the
    // request worker's thread-context class loader.
    @SuppressWarnings("PMD.UseProperClassLoader")
    private static List<MatchRule> discoverRules(Plugin interactiveChat)
            throws ReflectiveOperationException {
        ClassLoader loader = interactiveChat.getClass().getClassLoader();
        Class<?> main = Class.forName("com.loohp.interactivechat.InteractiveChat", false, loader);
        Class<?> placeholder = Class.forName(
                "com.loohp.interactivechat.objectholders.ICPlaceholder", false, loader);
        Method getKeyword = placeholder.getMethod("getKeyword");
        List<MatchRule> result = new ArrayList<>();
        appendRule(result, main, getKeyword, "useItem", "itemPlaceholder", Kind.ITEM);
        appendRule(result, main, getKeyword, "useInventory", "invPlaceholder", Kind.INVENTORY);
        appendRule(result, main, getKeyword, "useEnder", "enderPlaceholder", Kind.ENDER_CHEST);
        return result;
    }

    private static void appendRule(
            List<MatchRule> result, Class<?> main, Method keyword,
            String enabledField, String placeholderField, Kind kind
    ) throws ReflectiveOperationException {
        Field enabled = main.getField(enabledField);
        Object placeholder = main.getField(placeholderField).get(null);
        boolean active = enabled.getBoolean(null) && placeholder != null;
        if (active) {
            Object expression = keyword.invoke(placeholder);
            if (!(expression instanceof Pattern pattern)) {
                throw new IllegalArgumentException("Invalid InteractiveChat placeholder pattern");
            }
            result.add(new MatchRule(kind, pattern, true));
        }
    }

    @Override
    public CompletionStage<List<RichChatArtifact>> render(RichChatArtifactRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<List<RichChatArtifact>> result = new CompletableFuture<>();
        if (expired(request)) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (expired(request)) {
                    result.complete(List.of());
                    return;
                }
                Player player = plugin.getServer().getPlayer(request.minecraftPlayerId());
                if (player == null || !player.isOnline()) {
                    result.complete(List.of());
                    return;
                }
                boolean accepted = player.getScheduler().execute(plugin,
                        () -> snapshot(player, request, result),
                        () -> result.complete(List.of()), 1L);
                if (!accepted) {
                    result.complete(List.of());
                }
            });
        } catch (RuntimeException failure) {
            result.complete(List.of());
        }
        return result;
    }

    private boolean expired(RichChatArtifactRequest request) {
        return closed || clock.millis() >= request.expiresAtEpochMillis();
    }

    private void snapshot(
            Player player, RichChatArtifactRequest request,
            CompletableFuture<List<RichChatArtifact>> result
    ) {
        if (expired(request) || result.isDone()) {
            result.complete(List.of());
            return;
        }
        List<Job> jobs = new ArrayList<>();
        try {
            for (MatchRule rule : rules) {
                captureJob(rule, player, request).ifPresent(jobs::add);
            }
        } catch (RuntimeException failure) {
            result.complete(List.of());
            return;
        }
        if (jobs.isEmpty()) {
            result.complete(List.of());
            return;
        }
        try {
            workers.execute(() -> renderSnapshots(request, jobs, result));
        } catch (RejectedExecutionException failure) {
            result.complete(List.of());
        }
    }

    private static Optional<Job> captureJob(
            MatchRule rule, Player player, RichChatArtifactRequest request
    ) {
        if (!rule.enabled() || !player.hasPermission(rule.kind().permission)) {
            return Optional.empty();
        }
        int position = firstUnescapedMatch(rule.expression(), request.canonicalPlainText());
        if (position < 0) {
            return Optional.empty();
        }
        List<Slot> slots = switch (rule.kind()) {
            case ITEM -> List.of(slot(player.getInventory().getItemInMainHand()));
            case INVENTORY -> copySlots(player.getInventory(), MAX_SLOTS);
            case ENDER_CHEST -> copySlots(player.getEnderChest(), MAX_SLOTS);
        };
        return Optional.of(new Job(rule.kind(), position, slots));
    }

    private void renderSnapshots(
            RichChatArtifactRequest request, List<Job> jobs,
            CompletableFuture<List<RichChatArtifact>> result
    ) {
        if (expired(request) || result.isDone()) {
            result.complete(List.of());
            return;
        }
        List<RichChatArtifact> artifacts = new ArrayList<>();
        try {
            for (Job job : jobs) {
                if (expired(request) || result.isDone()) {
                    return;
                }
                try {
                    byte[] png = png(renderCard(job.kind(), job.slots()));
                    if (png.length == 0 || png.length > RichChatArtifact.MAX_ARTIFACT_BYTES) {
                        continue;
                    }
                    String suffix = request.eventId().toString().substring(0, 8);
                    artifacts.add(new RichChatArtifact(job.kind().artifactKind, job.position(),
                            "IC-Native-" + job.kind().title + "-" + suffix + ".png",
                            "image/png", job.kind().alt, png));
                } catch (RuntimeException failure) {
                    // Drop only this failed image. The textual chat message is unaffected.
                    continue;
                }
            }
        } finally {
            result.complete(List.copyOf(artifacts));
        }
    }

    private static List<Slot> copySlots(Inventory inventory, int max) {
        int count = Math.min(inventory.getSize(), max);
        List<Slot> slots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            slots.add(slot(inventory.getItem(index)));
        }
        return List.copyOf(slots);
    }

    private static Slot slot(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Slot.empty();
        }
        return new Slot(item.getType().name(), Math.min(127, Math.max(0, item.getAmount())));
    }

    static int firstUnescapedMatch(Pattern expression, String message) {
        Matcher match = expression.matcher(message);
        while (match.find()) {
            int backslashes = 0;
            for (int at = match.start() - 1; at >= 0 && message.charAt(at) == '\\'; at--) {
                backslashes++;
            }
            if (backslashes % 2 == 0) {
                return match.start();
            }
        }
        return -1;
    }

    static BufferedImage renderCard(Kind kind, List<Slot> slots) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(slots, "slots");
        if (slots.isEmpty() || slots.size() > MAX_SLOTS) {
            throw new IllegalArgumentException("invalid slot count");
        }
        int columns = kind == Kind.ITEM ? 1 : COLUMNS;
        int rows = (slots.size() + columns - 1) / columns;
        int width = columns * CELL_WIDTH + 32;
        int height = TOP + rows * CELL_HEIGHT + 18;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, width, height);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
            g.drawString(kind.title, 17, 34);
            for (int i = 0; i < slots.size(); i++) {
                drawSlot(g, i, columns, slots.get(i));
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawSlot(Graphics2D g, int index, int columns, Slot slot) {
        int x = 16 + (index % columns) * CELL_WIDTH;
        int y = TOP + (index / columns) * CELL_HEIGHT;
        g.setColor(CELL);
        g.fillRoundRect(x, y, CELL_WIDTH - 4, CELL_HEIGHT - 4, 7, 7);
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRoundRect(x, y, CELL_WIDTH - 4, CELL_HEIGHT - 4, 7, 7);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        if (slot.amount() == 0 || slot.material().isBlank()) {
            g.setColor(SUBTEXT);
            g.drawString("Empty", x + 8, y + 31);
            return;
        }
        String label = slot.material().toLowerCase(Locale.ROOT).replace('_', ' ');
        int split = label.lastIndexOf(' ', Math.min(10, label.length() - 1));
        String first = split > 0 ? label.substring(0, split) : label;
        String second = split > 0 ? label.substring(split + 1) : "";
        g.setColor(TEXT);
        g.drawString(trim(first, 12), x + 6, y + 21);
        g.drawString(trim(second, 12), x + 6, y + 35);
        g.setColor(SUBTEXT);
        g.drawString("x" + slot.amount(), x + 6, y + 51);
    }

    private static String trim(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private static byte[] png(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", output)) {
                return new byte[0];
            }
            return output.toByteArray();
        } catch (IOException failure) {
            return new byte[0];
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            services.unregister(RichChatArtifactProvider.class, this);
        }
    }
}
