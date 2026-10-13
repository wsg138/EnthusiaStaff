package net.enthusia.staff.paper.integration;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Transitional staging renderer backed by the already-installed InteractiveChat Discord addon.
 *
 * <p>No addon implementation is copied or linked into EnthusiaStaff. All upstream calls are
 * reflective and this service is registered only while both InteractiveChat and
 * InteractiveChatDiscordSrvAddon are enabled. The permanent renderer remains a separate
 * GPL-compatible companion.</p>
 */
public final class InteractiveChatStagingArtifactProvider
        implements RichChatArtifactProvider, AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(InteractiveChatStagingArtifactProvider.class.getName());
    public static final String INTERACTIVE_CHAT = "InteractiveChat";
    public static final String DISCORD_ADDON = "InteractiveChatDiscordSrvAddon";
    private static final int PLAYER_INVENTORY_SIZE = 45;

    enum Kind {
        ITEM("interactivechat.module.item", RichChatArtifact.Kind.ITEM, "Item", "Shared item"),
        INVENTORY(
                "interactivechat.module.inventory",
                RichChatArtifact.Kind.INVENTORY,
                "Inventory",
                "Shared inventory"
        ),
        ENDER_CHEST(
                "interactivechat.module.enderchest",
                RichChatArtifact.Kind.ENDER_CHEST,
                "EnderChest",
                "Shared Ender chest"
        );

        private final String permission;
        private final RichChatArtifact.Kind artifactKind;
        private final String filename;
        private final String altText;

        Kind(
                String permission,
                RichChatArtifact.Kind artifactKind,
                String filename,
                String altText
        ) {
            this.permission = permission;
            this.artifactKind = artifactKind;
            this.filename = filename;
            this.altText = altText;
        }

        String permission() {
            return permission;
        }
    }

    record RenderJob(Kind kind, int position, ItemStack item, Inventory inventory,
            String itemTitle, List<?> tooltipComponents, Object tooltipStyleKey) {
        RenderJob(Kind kind, int position, ItemStack item, Inventory inventory) {
            this(kind, position, item, inventory, "", List.of(), null);
        }

        RenderJob {
            Objects.requireNonNull(kind, "kind");
            if (position < 0) {
                throw new IllegalArgumentException("render position must not be negative");
            }
            itemTitle = Objects.requireNonNull(itemTitle, "itemTitle");
            tooltipComponents = List.copyOf(Objects.requireNonNull(tooltipComponents, "tooltipComponents"));
        }
    }

    record Snapshot(Object interactivePlayer, List<RenderJob> jobs) {
        Snapshot {
            Objects.requireNonNull(interactivePlayer, "interactivePlayer");
            jobs = List.copyOf(Objects.requireNonNull(jobs, "jobs"));
        }
    }

    public record Discovery(Optional<InteractiveChatStagingArtifactProvider> integration, String issue) {
        public Discovery {
            integration = Objects.requireNonNull(integration, "integration");
            issue = Objects.requireNonNull(issue, "issue");
        }

        static Discovery inactive() {
            return new Discovery(Optional.empty(), "");
        }

        static Discovery unavailable(String issue) {
            return new Discovery(Optional.empty(), Objects.requireNonNull(issue, "issue"));
        }
    }

    interface Access {
        Snapshot snapshot(Player player, RichChatArtifactRequest request) throws ReflectiveOperationException;

        List<RichChatArtifact> render(UUID eventId, Snapshot snapshot) throws ReflectiveOperationException;
    }

    private final JavaPlugin plugin;
    private final Clock clock;
    private final ExecutorService workers;
    private final ServicesManager services;
    private final Access access;
    private volatile boolean closed;

    private InteractiveChatStagingArtifactProvider(
            JavaPlugin plugin,
            Clock clock,
            ExecutorService workers,
            Access access
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.services = plugin.getServer().getServicesManager();
        this.access = Objects.requireNonNull(access, "access");
        services.register(
                RichChatArtifactProvider.class,
                this,
                plugin,
                ServicePriority.Lowest
        );
    }

    public static Discovery discoverAndRegister(
            JavaPlugin plugin,
            Clock clock,
            ExecutorService workers
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Plugin interactiveChat =
                plugin.getServer().getPluginManager().getPlugin(INTERACTIVE_CHAT);
        Plugin discordAddon =
                plugin.getServer().getPluginManager().getPlugin(DISCORD_ADDON);
        if (interactiveChat == null || !interactiveChat.isEnabled()
                || discordAddon == null || !discordAddon.isEnabled()) {
            return Discovery.inactive();
        }

        try {
            Access access = new ReflectionAccess(interactiveChat, discordAddon);
            return new Discovery(
                    Optional.of(new InteractiveChatStagingArtifactProvider(
                            plugin,
                            clock,
                            workers,
                            access
                    )),
                    ""
            );
        } catch (ReflectiveOperationException | LinkageError failure) {
            return Discovery.unavailable(
                    "InteractiveChat staging renderer API mismatch: "
                            + failure.getClass().getSimpleName()
            );
        }
    }

    static int firstUnescapedMatch(Pattern pattern, String message) {
        List<Integer> positions = unescapedMatchPositions(pattern, message, 1);
        return positions.isEmpty() ? -1 : positions.getFirst();
    }

    static List<Integer> unescapedMatchPositions(Pattern pattern, String message, int maximum) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(message, "message");
        if (maximum <= 0) {
            return List.of();
        }
        List<Integer> positions = new ArrayList<>();
        Matcher matcher = pattern.matcher(message);
        while (matcher.find() && positions.size() < maximum) {
            int precedingSlashes = 0;
            for (int index = matcher.start() - 1;
                    index >= 0 && message.charAt(index) == '\\'; index--) {
                precedingSlashes++;
            }
            if (precedingSlashes % 2 == 0) {
                positions.add(matcher.start());
            }
        }
        return List.copyOf(positions);
    }

    static UUID detachedSnapshotUuid(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return UUID.nameUUIDFromBytes(
                ("enthusia-rich-render:" + playerId).getBytes(StandardCharsets.UTF_8)
        );
    }

    static InteractiveChatStagingArtifactProvider forTest(
            JavaPlugin plugin,
            Clock clock,
            ExecutorService workers,
            Access access
    ) {
        return new InteractiveChatStagingArtifactProvider(plugin, clock, workers, access);
    }

    @Override
    public CompletionStage<List<RichChatArtifact>> render(RichChatArtifactRequest request) {
        Objects.requireNonNull(request, "request");
        CompletableFuture<List<RichChatArtifact>> result = new CompletableFuture<>();
        if (closed || clock.millis() > request.expiresAtEpochMillis()) {
            result.complete(List.of());
            return result;
        }

        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (closed || clock.millis() > request.expiresAtEpochMillis()) {
                    result.complete(List.of());
                    return;
                }
                Player player = plugin.getServer().getPlayer(request.minecraftPlayerId());
                if (player == null || !player.isOnline()) {
                    result.complete(List.of());
                    return;
                }
                boolean scheduled = player.getScheduler().execute(
                        plugin,
                        () -> snapshotAndRender(player, request, result),
                        () -> result.complete(List.of()),
                        1L
                );
                if (!scheduled) {
                    result.complete(List.of());
                }
            });
        } catch (RuntimeException failure) {
            result.complete(List.of());
        }
        return result;
    }

    private void snapshotAndRender(
            Player player,
            RichChatArtifactRequest request,
            CompletableFuture<List<RichChatArtifact>> result
    ) {
        if (closed || result.isDone() || clock.millis() > request.expiresAtEpochMillis()) {
            result.complete(List.of());
            return;
        }

        Snapshot snapshot;
        try {
            snapshot = access.snapshot(player, request);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            result.complete(List.of());
            return;
        }
        if (snapshot.jobs().isEmpty()) {
            result.complete(List.of());
            return;
        }

        try {
            workers.execute(() -> {
                if (closed || result.isDone() || clock.millis() > request.expiresAtEpochMillis()) {
                    result.complete(List.of());
                    return;
                }
                try {
                    result.complete(access.render(request.eventId(), snapshot));
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    result.complete(List.of());
                }
            });
        } catch (RejectedExecutionException failure) {
            result.complete(List.of());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        services.unregister(RichChatArtifactProvider.class, this);
    }

    static List<RichChatArtifact> withinBundleBudget(List<RichChatArtifact> candidates) {
        List<RichChatArtifact> kept = new ArrayList<>();
        long total = 0L;
        for (RichChatArtifact artifact : candidates) {
            if (kept.size() >= net.enthusia.staff.protocol.ChatBridgeArtifactBundle.MAX_ARTIFACTS) {
                break;
            }
            long size = artifact.data().length;
            if (total + size > net.enthusia.staff.protocol.ChatBridgeArtifactBundle.MAX_TOTAL_ARTIFACT_BYTES) {
                continue;
            }
            kept.add(artifact);
            total += size;
        }
        return kept;
    }


    /**
     * Reads an optional item tooltip-style Key while still on the player's owner thread.
     * The image worker receives only the detached value; a missing/incompatible API
     * retains the normal Minecraft tooltip background.
     */
    static Object captureTooltipStyle(Method getInstance, Method getStyle, ItemStack item) {
        try {
            Object wrapper = getInstance.invoke(null);
            return getStyle.invoke(wrapper, item);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    static String plainItemTitle(String value) {
        String filtered = value.replaceAll("(?i)§[0-9a-fk-or]", "")
                .replaceAll("(?i)</?reset>", "").strip();
        if (filtered.isBlank()) {
            return "Shared item";
        }
        return filtered.substring(0, Math.min(filtered.length(), 240));
    }


    private static final class ReflectionAccess implements Access {
        private final Field useItem;
        private final Field useInventory;
        private final Field useEnder;
        private final Field itemPlaceholder;
        private final Field inventoryPlaceholder;
        private final Field enderPlaceholder;
        private final Method getKeyword;
        private final Constructor<?> offlinePlayerConstructor;
        private final Method renderItem;
        private final Method renderInventory;
        private final Optional<Method> itemTitleMethod;
        private final Optional<Method> itemTooltipMethod;
        private final Optional<Method> tooltipHiddenMethod;
        private final Optional<Method> tooltipComponentsMethod;
        private final Optional<Method> renderTooltipMethod;
        private final Optional<Method> tooltipStyleInstanceMethod;
        private final Optional<Method> tooltipStyleLookupMethod;
        private final String addonLanguage;
        private final boolean detailedTooltipsEnabled;
        private final boolean itemImagesEnabled;
        private final boolean inventoryImagesEnabled;
        private final boolean enderImagesEnabled;

        private ReflectionAccess(
                Plugin interactiveChat,
                Plugin discordAddon
        ) throws ReflectiveOperationException {
            ClassLoader interactiveLoader = interactiveChat.getClass().getClassLoader();
            ClassLoader addonLoader = discordAddon.getClass().getClassLoader();

            Class<?> interactiveClass = Class.forName(
                    "com.loohp.interactivechat.InteractiveChat",
                    false,
                    interactiveLoader
            );
            Class<?> placeholderClass = Class.forName(
                    "com.loohp.interactivechat.objectholders.ICPlaceholder",
                    false,
                    interactiveLoader
            );
            Class<?> offlinePlayerClass = Class.forName(
                    "com.loohp.interactivechat.objectholders.OfflineICPlayer",
                    false,
                    interactiveLoader
            );
            Class<?> imageGeneration = Class.forName(
                    "com.loohp.interactivechatdiscordsrvaddon.graphics.ImageGeneration",
                    false,
                    addonLoader
            );

            Class<?> addonClass = discordAddon.getClass();
            itemImagesEnabled = addonClass.getField("itemImage").getBoolean(discordAddon);
            inventoryImagesEnabled = addonClass.getField("invImage").getBoolean(discordAddon);
            enderImagesEnabled = addonClass.getField("enderImage").getBoolean(discordAddon);

            useItem = interactiveClass.getField("useItem");
            useInventory = interactiveClass.getField("useInventory");
            useEnder = interactiveClass.getField("useEnder");
            itemPlaceholder = interactiveClass.getField("itemPlaceholder");
            inventoryPlaceholder = interactiveClass.getField("invPlaceholder");
            enderPlaceholder = interactiveClass.getField("enderPlaceholder");
            getKeyword = placeholderClass.getMethod("getKeyword");
            offlinePlayerConstructor = offlinePlayerClass.getDeclaredConstructor(
                    UUID.class,
                    String.class,
                    int.class,
                    boolean.class,
                    int.class,
                    Inventory.class,
                    Inventory.class
            );
            if (!offlinePlayerConstructor.trySetAccessible()) {
                throw new IllegalAccessException(
                        "InteractiveChat OfflineICPlayer snapshot constructor is inaccessible"
                );
            }
            renderItem = imageGeneration.getMethod(
                    "getItemStackImage",
                    ItemStack.class,
                    offlinePlayerClass
            );
            renderInventory = imageGeneration.getMethod(
                    "getInventoryImage",
                    Inventory.class,
                    offlinePlayerClass
            );
            // Optional addon APIs: do not disable existing icon/inventory rendering
            // when older installed addon builds lack tooltip support.
            Optional<Method> itemName = Optional.empty();
            Optional<Method> tooltip = Optional.empty();
            Optional<Method> hidden = Optional.empty();
            Optional<Method> components = Optional.empty();
            Optional<Method> tooltipImage = Optional.empty();
            String language = "en_us";
            boolean advanced = false;
            try {
                Class<?> itemUtils = Class.forName(
                        "com.loohp.interactivechatdiscordsrvaddon.utils.DiscordItemStackUtils",
                        false, addonLoader);
                Class<?> tooltipClass = Class.forName(
                        "com.loohp.interactivechatdiscordsrvaddon.utils.DiscordItemStackUtils$DiscordToolTip",
                        false, addonLoader);
                Class<?> keyClass = Class.forName(
                        "com.loohp.interactivechat.libs.net.kyori.adventure.key.Key",
                        false, interactiveLoader);
                itemName = Optional.of(itemUtils.getMethod(
                        "getItemNameForDiscord", ItemStack.class, offlinePlayerClass, String.class));
                tooltip = Optional.of(itemUtils.getMethod(
                        "getToolTip", ItemStack.class, offlinePlayerClass, boolean.class));
                hidden = Optional.of(tooltipClass.getMethod("isHideTooltip"));
                components = Optional.of(tooltipClass.getMethod("getComponents"));
                tooltipImage = Optional.of(imageGeneration.getMethod("getToolTipImage", List.class, keyClass));
                language = String.valueOf(addonClass.getField("language").get(discordAddon));
                advanced = addonClass.getField("showAdvanceDetails").getBoolean(discordAddon);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                // Preserve icon-only fallback when addon APIs/config differ.
                itemName = Optional.empty();
                tooltip = Optional.empty();
                hidden = Optional.empty();
                components = Optional.empty();
                tooltipImage = Optional.empty();
            }
            // Custom tooltip backgrounds are optional on older addon versions. Resolve the
            // style on the player's owner scheduler, never from the async image worker.
            Optional<Method> styleInstance = Optional.empty();
            Optional<Method> styleLookup = Optional.empty();
            if (tooltipImage.isPresent()) {
                try {
                    Class<?> addonNms = Class.forName(
                            "com.loohp.interactivechatdiscordsrvaddon.nms.NMSAddon",
                            false, addonLoader);
                    Class<?> addonNmsWrapper = Class.forName(
                            "com.loohp.interactivechatdiscordsrvaddon.nms.NMSAddonWrapper",
                            false, addonLoader);
                    styleInstance = Optional.of(addonNms.getMethod("getInstance"));
                    styleLookup = Optional.of(addonNmsWrapper.getMethod(
                            "getCustomTooltipResourceLocation", ItemStack.class));
                } catch (ReflectiveOperationException | LinkageError unsupportedStyle) {
                    // Keep the standard tooltip and icon when the style API is absent.
                }
            }
            tooltipStyleInstanceMethod = styleInstance;
            tooltipStyleLookupMethod = styleLookup;
            itemTitleMethod = itemName;
            itemTooltipMethod = tooltip;
            tooltipHiddenMethod = hidden;
            tooltipComponentsMethod = components;
            renderTooltipMethod = tooltipImage;
            addonLanguage = language;
            detailedTooltipsEnabled = advanced;
        }

        @Override
        public Snapshot snapshot(
                Player player,
                RichChatArtifactRequest request
        ) throws ReflectiveOperationException {
            Inventory inventory = copyInventory(player.getInventory(), PLAYER_INVENTORY_SIZE);
            Inventory enderChest = copyInventory(
                    player.getEnderChest(),
                    normalizedInventorySize(player.getEnderChest().getSize())
            );
            Object detachedPlayer = newOfflinePlayer(
                    player,
                    inventory,
                    enderChest
            );

            List<RenderJob> jobs = new ArrayList<>(3);
            addItemJob(player, request, inventory, detachedPlayer, jobs);
            addInventoryJob(
                    Kind.INVENTORY,
                    useInventory,
                    inventoryPlaceholder,
                    inventory,
                    player,
                    request,
                    jobs
            );
            addInventoryJob(
                    Kind.ENDER_CHEST,
                    useEnder,
                    enderPlaceholder,
                    enderChest,
                    player,
                    request,
                    jobs
            );
            return new Snapshot(detachedPlayer, jobs);
        }

        private void addItemJob(
                Player player,
                RichChatArtifactRequest request,
                Inventory inventory,
                Object detachedPlayer,
                List<RenderJob> jobs
        ) throws ReflectiveOperationException {
            if (!itemImagesEnabled) {
                return;
            }
            Match match = match(
                    Kind.ITEM,
                    useItem,
                    itemPlaceholder,
                    player,
                    request
            );
            if (match == null) {
                return;
            }
            Pattern keyword = (Pattern) invoke(getKeyword, itemPlaceholder.get(null));
            List<Integer> positions = unescapedMatchPositions(
                    keyword, request.canonicalPlainText(),
                    net.enthusia.staff.protocol.ChatBridgeArtifactBundle.MAX_ARTIFACTS / 2);
            if (positions.isEmpty()) {
                return;
            }
            int selectedSlot = player.getInventory().getHeldItemSlot();
            ItemStack item = inventory.getItem(selectedSlot);
            if (item == null) {
                return;
            }
            String name = "";
            List<?> tooltip = List.of();
            Object tooltipStyleKey = null;
            if (itemTitleMethod.isPresent() && itemTooltipMethod.isPresent()
                    && tooltipHiddenMethod.isPresent() && tooltipComponentsMethod.isPresent()) {
                try {
                    name = String.valueOf(invoke(itemTitleMethod.orElseThrow(), null, item,
                            detachedPlayer, addonLanguage));
                    Object data = invoke(itemTooltipMethod.orElseThrow(), null, item,
                            detachedPlayer, detailedTooltipsEnabled);
                    if (!Boolean.TRUE.equals(invoke(tooltipHiddenMethod.orElseThrow(), data))) {
                        Object components = invoke(tooltipComponentsMethod.orElseThrow(), data);
                        if (components instanceof List<?> list) {
                            tooltip = List.copyOf(list);
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException unsupported) {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        LOGGER.log(System.Logger.Level.DEBUG,
                                "InteractiveChat tooltip snapshot unavailable: {0}",
                                unsupported.getClass().getSimpleName());
                    }
                }
            }
            if (!tooltip.isEmpty() && tooltipStyleInstanceMethod.isPresent()
                    && tooltipStyleLookupMethod.isPresent()) {
                tooltipStyleKey = captureTooltipStyle(
                        tooltipStyleInstanceMethod.orElseThrow(),
                        tooltipStyleLookupMethod.orElseThrow(), item);
            }
            for (int position : positions) {
                jobs.add(new RenderJob(
                        Kind.ITEM, position, item.clone(), null, name, tooltip, tooltipStyleKey
                ));
            }
        }

        private void addInventoryJob(
                Kind kind,
                Field enabled,
                Field placeholder,
                Inventory inventory,
                Player player,
                RichChatArtifactRequest request,
                List<RenderJob> jobs
        ) throws ReflectiveOperationException {
            if ((kind == Kind.INVENTORY && !inventoryImagesEnabled)
                    || (kind == Kind.ENDER_CHEST && !enderImagesEnabled)) {
                return;
            }
            Match match = match(kind, enabled, placeholder, player, request);
            if (match == null) {
                return;
            }
            jobs.add(new RenderJob(kind, match.position(), null, inventory));
        }

        private Object newOfflinePlayer(
                Player player,
                Inventory inventory,
                Inventory enderChest
        ) throws ReflectiveOperationException {
            try {
                boolean rightHanded = player.getMainHand()
                        == org.bukkit.inventory.MainHand.RIGHT;
                return offlinePlayerConstructor.newInstance(
                        detachedSnapshotUuid(player.getUniqueId()),
                        player.getName(),
                        player.getInventory().getHeldItemSlot(),
                        rightHanded,
                        player.getLevel(),
                        inventory,
                        enderChest
                );
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw failure;
            }
        }

        private static Inventory copyInventory(Inventory source, int targetSize) {
            Inventory copy = Bukkit.createInventory(null, targetSize);
            int limit = Math.min(source.getSize(), targetSize);
            for (int index = 0; index < limit; index++) {
                ItemStack item = source.getItem(index);
                if (item != null) {
                    copy.setItem(index, item.clone());
                }
            }
            return copy;
        }

        private static int normalizedInventorySize(int sourceSize) {
            return Math.max(9, Math.min(54, ((sourceSize + 8) / 9) * 9));
        }

        private Match match(
                Kind kind,
                Field enabled,
                Field placeholderField,
                Player player,
                RichChatArtifactRequest request
        ) throws ReflectiveOperationException {
            if (!enabled.getBoolean(null) || !player.hasPermission(kind.permission)) {
                return null;
            }
            Object placeholder = placeholderField.get(null);
            if (placeholder == null) {
                return null;
            }
            Pattern pattern = (Pattern) invoke(getKeyword, placeholder);
            int position = firstUnescapedMatch(pattern, request.canonicalPlainText());
            return position < 0 ? null : new Match(position);
        }

        @Override
        public List<RichChatArtifact> render(
                UUID eventId,
                Snapshot snapshot
        ) throws ReflectiveOperationException {
            List<RichChatArtifact> artifacts = new ArrayList<>(snapshot.jobs().size());
            int ordinal = 0;
            for (RenderJob job : snapshot.jobs()) {
                BufferedImage image;
                try {
                    image = switch (job.kind()) {
                        case ITEM -> (BufferedImage) invoke(
                                renderItem,
                                null,
                                job.item(),
                                snapshot.interactivePlayer()
                        );
                        case INVENTORY -> (BufferedImage) invoke(
                                renderInventory,
                                null,
                                job.inventory(),
                                snapshot.interactivePlayer()
                        );
                        case ENDER_CHEST -> (BufferedImage) invoke(
                                renderInventory,
                                null,
                                job.inventory(),
                                snapshot.interactivePlayer()
                        );
                    };
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    continue;
                }
                if (image == null) {
                    continue;
                }
                byte[] png = png(image);
                if (png.length == 0 || png.length > RichChatArtifact.MAX_ARTIFACT_BYTES) {
                    continue;
                }
                String suffix = eventId.toString().substring(0, 8);
                String iconTitle = job.kind() == Kind.ITEM && !job.itemTitle().isBlank()
                        ? plainItemTitle(job.itemTitle()) : job.kind().altText;
                artifacts.add(new RichChatArtifact(
                        job.kind().artifactKind,
                        job.position(),
                        "IC-" + job.kind().filename + "-" + suffix + "-" + ordinal++ + ".png",
                        "image/png",
                        iconTitle,
                        png
                ));
                if (job.kind() == Kind.ITEM && !job.tooltipComponents().isEmpty()
                        && renderTooltipMethod.isPresent()
                        && artifacts.size() < net.enthusia.staff.protocol.ChatBridgeArtifactBundle.MAX_ARTIFACTS) {
                    renderItemTooltip(artifacts, job, suffix, ordinal++);
                }
            }
            return withinBundleBudget(artifacts);
        }

        private void renderItemTooltip(List<RichChatArtifact> artifacts,
                RenderJob job, String suffix, int ordinal) {
            try {
                BufferedImage tooltip = (BufferedImage) invoke(
                        renderTooltipMethod.orElseThrow(), null,
                        job.tooltipComponents(), job.tooltipStyleKey());
                if (tooltip == null) {
                    return;
                }
                byte[] rendered = png(tooltip);
                if (rendered.length == 0 || rendered.length > RichChatArtifact.MAX_ARTIFACT_BYTES) {
                    return;
                }
                artifacts.add(new RichChatArtifact(
                        RichChatArtifact.Kind.TOOLTIP,
                        job.position(),
                        "IC-ToolTip-" + suffix + "-" + ordinal + ".png",
                        "image/png",
                        "Minecraft item details",
                        rendered
                ));
            } catch (ReflectiveOperationException | RuntimeException failure) {
                if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                    LOGGER.log(System.Logger.Level.DEBUG,
                            "InteractiveChat tooltip image unavailable: {0}",
                            failure.getClass().getSimpleName());
                }
            }
        }

        private static byte[] png(BufferedImage image) {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(image, "PNG", output)) {
                    return new byte[0];
                }
                return output.toByteArray();
            } catch (java.io.IOException failure) {
                return new byte[0];
            }
        }

        private static Object invoke(
                Method method,
                Object target,
                Object... arguments
        ) throws ReflectiveOperationException {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw failure;
            }
        }

        private record Match(int position) {
        }
    }
}
