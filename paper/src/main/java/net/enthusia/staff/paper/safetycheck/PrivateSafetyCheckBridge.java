package net.enthusia.staff.paper.safetycheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Non-punitive player safety GUI and private on-duty staff-alert adapter. */
public final class PrivateSafetyCheckBridge implements Listener, AutoCloseable {
    private static final String PREFIX = "ai-safety-check.";
    private static final String PERMISSION = "enthusiastaff.safety.alerts";

    private record Credential(String clientId, String token) {}
    private final JavaPlugin plugin;
    private final ObjectMapper mapper;
    private final URI origin;
    private final String scope;
    private final Credential replyAuth;
    private final Credential alertsAuth;
    private final Predicate<UUID> activeDuty;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();
    private final Set<String> presented = ConcurrentHashMap.newKeySet();
    private final Set<String> delivering = ConcurrentHashMap.newKeySet();
    private final Set<String> deliveredPendingAck = ConcurrentHashMap.newKeySet();
    private ScheduledTask pollTask;

    private PrivateSafetyCheckBridge(
            JavaPlugin plugin, ObjectMapper mapper, URI origin, String scope,
            Credential replyAuth, Credential alertsAuth,
            Predicate<UUID> activeDuty
    ) {
        this.activeDuty = Objects.requireNonNull(activeDuty, "activeDuty");
        this.plugin = plugin;
        this.mapper = mapper;
        this.origin = origin;
        this.scope = scope;
        this.replyAuth = replyAuth;
        this.alertsAuth = alertsAuth;
    }

    public static PrivateSafetyCheckBridge create(
            JavaPlugin plugin, ObjectMapper mapper, Function<String, String> env,
            Predicate<UUID> activeDuty
    ) {
        if (!plugin.getConfig().getBoolean(PREFIX + "enabled", false)) {
            return null;
        }
        try {
            URI uri = URI.create(required(plugin.getConfig().getString(PREFIX + "base-url")));
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || !("https".equalsIgnoreCase(scheme)
                        || ("http".equalsIgnoreCase(scheme)
                            && ("localhost".equalsIgnoreCase(uri.getHost())
                                || "127.0.0.1".equals(uri.getHost()))))) {
                throw new IllegalArgumentException("Invalid private API URL");
            }
            String scope = required(plugin.getConfig().getString(PREFIX + "scope-id"));
            String replyClient = required(plugin.getConfig().getString(PREFIX + "reply-client-id"));
            String alertClient = required(plugin.getConfig().getString(PREFIX + "alerts-client-id"));
            String replyEnv = required(plugin.getConfig().getString(
                    PREFIX + "reply-token-environment", "ES_SAFETY_REPLY_TOKEN"));
            String alertsEnv = required(plugin.getConfig().getString(
                    PREFIX + "alerts-token-environment", "ES_SAFETY_ALERTS_TOKEN"));
            String replyToken = required(env.apply(replyEnv));
            String alertToken = required(env.apply(alertsEnv));
            if (replyClient.equals(alertClient) || replyToken.equals(alertToken)) {
                throw new IllegalArgumentException("Private safety roles must be separate");
            }
            return new PrivateSafetyCheckBridge(plugin, mapper, uri, scope,
                    new Credential(replyClient, replyToken),
                    new Credential(alertClient, alertToken), activeDuty);
        } catch (RuntimeException exception) {
            // Do not print URLs, exception messages or secret-bearing values.
            plugin.getLogger().warning("Private safety GUI disabled: invalid/incomplete configuration");
            return null;
        }
    }

    private static String required(String text) {
        if (text == null || text.isBlank() || text.length() > 200) {
            throw new IllegalArgumentException("Missing safety setting");
        }
        return text.trim();
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        pollTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> poll(), 40L, 200L
        );
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private CompletableFuture<JsonNode> request(
            String endpoint, Credential auth, Object postBody
    ) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(origin.resolve(endpoint))
                    .timeout(Duration.ofSeconds(4))
                    .header("X-Client-Id", auth.clientId())
                    .header("Authorization", "Bearer " + auth.token())
                    .header("Accept", "application/json");
            if (postBody == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(postBody)));
            }
            return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200 || response.body().length() > 16384) {
                            throw new IllegalStateException("Private safety API unavailable");
                        }
                        try {
                            return mapper.readTree(response.body());
                        } catch (Exception exception) {
                            throw new IllegalStateException("Private safety API response invalid");
                        }
                    });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void poll() {
        if (!active.get()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player);
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        request("/v1/safety-alerts?limit=20", alertsAuth, null)
                .thenAccept(response -> {
                    JsonNode items = response.path("items");
                    if (items.isArray()) {
                        items.forEach(this::deliver);
                    }
                })
                .exceptionally(failure -> null)
                .whenComplete((none, failure) -> polling.set(false));
    }

    private void checkPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (!resolving.add(uuid)) {
            return;
        }
        request("/v1/safety-checks/pending?subject_id=" + segment(uuid.toString())
                + "&scope_id=" + segment(scope), replyAuth, null)
                .thenAccept(response -> {
                    JsonNode check = response.path("check");
                    if (!check.isObject() || !check.path("show_prompt").asBoolean()) {
                        return;
                    }
                    String id = check.path("check_id").asText("");
                    if (id.isBlank() || !presented.add(uuid + ":" + id)) {
                        return;
                    }
                    player.getScheduler().execute(plugin,
                            () -> {
                                if (active.get() && player.isOnline()) {
                                    show(player, id);
                                }
                            }, null, 1L);
                })
                .exceptionally(failure -> null)
                .whenComplete((none, failure) -> resolving.remove(uuid));
    }

    private static final class Holder implements InventoryHolder {
        final UUID player;
        final String check;
        Inventory inventory;
        Holder(UUID player, String check) {
            this.player = player;
            this.check = check;
        }
        @Override public Inventory getInventory() {
            return Objects.requireNonNull(inventory);
        }
    }

    private static void option(Inventory inv, int slot, Material material, String text) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, NamedTextColor.WHITE));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void show(Player player, String id) {
        Holder holder = new Holder(player.getUniqueId(), id);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Private safety check", NamedTextColor.AQUA));
        holder.inventory = inv;
        option(inv, 10, Material.LIME_CONCRETE, "I'm safe");
        option(inv, 12, Material.PINK_WOOL, "I might hurt myself / need help");
        option(inv, 14, Material.YELLOW_CONCRETE, "I'm not sure");
        option(inv, 16, Material.GRAY_CONCRETE, "I'd rather not answer");
        player.openInventory(inv);
        player.sendMessage(Component.text(
                "We're concerned about you. You can answer privately, or just close the window.",
                NamedTextColor.AQUA));
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.player.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String decision = switch (event.getSlot()) {
            case 10 -> "SAFE";
            case 12 -> "NEEDS_HELP";
            case 14 -> "UNSURE";
            case 16 -> "DECLINED";
            default -> null;
        };
        if (decision == null) {
            return;
        }
        player.closeInventory();
        if ("NEEDS_HELP".equals(decision) || "UNSURE".equals(decision)) {
            player.sendMessage(Component.text(
                    "If you may hurt yourself, reach out to someone you trust right now. "
                    + "If in immediate danger, contact local emergency services. "
                    + "In the U.S., call or text 988 for support.", NamedTextColor.AQUA));
        } else if ("SAFE".equals(decision)) {
            player.sendMessage(Component.text("Thank you for letting us know.", NamedTextColor.GREEN));
        }
        final boolean playerMayNeedHelp =
                "NEEDS_HELP".equals(decision) || "UNSURE".equals(decision);
        request("/v1/safety-checks/" + segment(holder.check) + "/reply",
                replyAuth, Map.of("subject_id", holder.player.toString(), "choice", decision))
                .exceptionally(failure -> {
                    // Best-effort local support notice when the API is
                    // unreachable: never ACK the API outbox here.
                    if (playerMayNeedHelp) {
                        offerLocalFallback(holder.player);
                    }
                    player.getScheduler().execute(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(Component.text(
                                    "Your answer could not be saved. You can still ask staff for help.",
                                    NamedTextColor.YELLOW));
                        }
                    }, null, 1L);
                    return null;
                });
    }

    private void offerLocalFallback(UUID subject) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            staff.getScheduler().execute(plugin, () -> {
                if (active.get() && staff.isOnline()
                        && staff.hasPermission(PERMISSION)
                        && activeDuty.test(staff.getUniqueId())) {
                    staff.sendMessage(Component.text(
                            "[PRIVATE SAFETY ALERT] Player " + subject
                            + " has requested help, but the safety API could not save "
                            + "their response. Please check with them privately.",
                            NamedTextColor.YELLOW
                    ));
                }
            }, null, 1L);
        }
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        checkPlayer(event.getPlayer());
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        resolving.remove(uuid);
        presented.removeIf(key -> key.startsWith(uuid + ":"));
    }

    private void deliver(JsonNode alert) {
        String id = alert.path("alert_id").asText("");
        String subject = alert.path("subject_id").asText("");
        String targetScope = alert.path("scope_id").asText("");
        String urgency = alert.path("urgency").asText("");
        if (!active.get() || id.length() < 10 || id.length() > 128
                || !scope.equals(targetScope) || !delivering.add(id)) {
            return;
        }
        if (deliveredPendingAck.contains(id)) {
            ack(id);
            return;
        }
        List<CompletableFuture<Boolean>> deliveries = new ArrayList<>();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            CompletableFuture<Boolean> outcome = new CompletableFuture<>();
            deliveries.add(outcome);
            boolean scheduled = staff.getScheduler().execute(plugin, () -> {
                try {
                    if (staff.isOnline() && staff.hasPermission(PERMISSION)
                            && activeDuty.test(staff.getUniqueId())) {
                        staff.sendMessage(Component.text(
                                "[PRIVATE SAFETY ALERT] " + urgency + " - player " + subject
                                + " on " + scope + ". Please offer support privately.",
                                NamedTextColor.YELLOW));
                        outcome.complete(true);
                    } else {
                        outcome.complete(false);
                    }
                } catch (RuntimeException exception) {
                    outcome.complete(false);
                }
            }, () -> outcome.complete(false), 1L);
            if (!scheduled) {
                outcome.complete(false);
            }
        }
        if (deliveries.isEmpty()) {
            delivering.remove(id);
            return;
        }
        CompletableFuture.allOf(deliveries.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    if (deliveries.stream().anyMatch(f -> f.getNow(false))) {
                        deliveredPendingAck.add(id);
                        ack(id);
                    } else {
                        delivering.remove(id);
                    }
                });
    }

    private void ack(String id) {
        // SendMessage success is only a transport assertion, not proof staff read it.
        request("/v1/safety-alerts/" + segment(id) + "/ack", alertsAuth,
                Map.of("delivery_system", "minecraft_staff",
                       "delivery_receipt_id", "safety-paper-" + id,
                       "delivered_at", Instant.now().toString()))
                .thenAccept(reply -> {
                    if (reply.path("acknowledged").asBoolean(false)) {
                        deliveredPendingAck.remove(id);
                    }
                })
                .exceptionally(failure -> null)
                .whenComplete((none, failure) -> delivering.remove(id));
    }

    @Override public void close() {
        active.set(false);
        if (pollTask != null) {
            pollTask.cancel();
        }
        resolving.clear();
        presented.clear();
        delivering.clear();
        deliveredPendingAck.clear();
    }
}
