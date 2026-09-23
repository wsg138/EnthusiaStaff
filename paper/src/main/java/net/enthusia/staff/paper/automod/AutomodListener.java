package net.enthusia.staff.paper.automod;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutomodListener implements Listener {
    private static final String REASON_ID = "hate.full-slur-untargeted";
    private static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(2);
    private static final Actor SYSTEM_ACTOR = new Actor(new UUID(0L, 0L), "Enthusia Automod", StaffRank.SYSTEM);

    private final JavaPlugin plugin;
    private final Clock clock;
    private final StrictVariantMatcher matcher;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentService> punishments;
    private final ExecutorService workers;
    private final Consumer<UUID> invalidateMute;
    private final ConcurrentHashMap<UUID, Detection> recent = new ConcurrentHashMap<>();

    public AutomodListener(
            JavaPlugin plugin,
            Clock clock,
            StrictVariantMatcher matcher,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentService> punishments,
            ExecutorService workers,
            Consumer<UUID> invalidateMute
    ) {
        this.plugin = plugin;
        this.clock = clock;
        this.matcher = matcher;
        this.mode = mode;
        this.punishments = punishments;
        this.workers = workers;
        this.invalidateMute = invalidateMute;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPaperChat(AsyncChatEvent event) {
        inspect(event.getPlayer(), PlainTextComponentSerializer.plainText().serialize(event.message()), event::setCancelled);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        inspect(event.getPlayer(), event.getMessage(), event::setCancelled);
    }

    private void inspect(Player player, String message, Consumer<Boolean> cancellation) {
        if (!matcher.matches(message)) {
            return;
        }
        String normalized = StrictVariantMatcher.normalize(message);
        int fingerprint = normalized.hashCode();
        Instant now = clock.instant();
        Detection prior = recent.put(player.getUniqueId(), new Detection(fingerprint, now));
        if (prior != null && prior.fingerprint() == fingerprint
                && prior.detectedAt().plus(DUPLICATE_WINDOW).isAfter(now)) {
            cancellation.accept(true);
            return;
        }
        recent.entrySet().removeIf(entry -> entry.getValue().detectedAt().plus(DUPLICATE_WINDOW).isBefore(now));
        cancellation.accept(true);
        notify(player, "Your public message was blocked by network moderation.");
        try {
            workers.execute(() -> createCase(player.getUniqueId()));
        } catch (RejectedExecutionException exception) {
            plugin.getLogger().severe("Automod matched a configured variant but the bounded work queue was full");
            alertStaff("Automod blocked a message, but case creation could not enter the bounded work queue.");
        }
    }

    private void createCase(UUID playerId) {
        PunishmentService service = punishments.get();
        if (service == null) {
            plugin.getLogger().severe("Automod blocked a message before punishment storage became ready");
            alertStaff("Automod blocked a message, but punishment storage was unavailable.");
            return;
        }
        PunishmentResult result = service.create(new CreatePunishmentRequest(
                new IdempotencyKey("automod:" + UUID.randomUUID()),
                playerId,
                SYSTEM_ACTOR,
                REASON_ID,
                "Exact configured public-chat variant matched before broadcast",
                CaseVisibility.PUBLIC,
                List.of()
        ), mode.get());
        if (result instanceof PunishmentResult.Accepted accepted) {
            invalidateMute.accept(playerId);
            alertStaff("Automod created case " + accepted.caseId() + " for a blocked public message.");
            return;
        }
        PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
        plugin.getLogger().log(Level.SEVERE, "Automod case creation was rejected: {0}", rejected.code());
        alertStaff("Automod blocked a message, but case creation was rejected: " + rejected.code() + '.');
    }

    private void alertStaff(String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(player -> player.hasPermission("enthusiastaff.alerts"))
                        .forEach(player -> player.sendMessage(Component.text(message))));
    }

    private void notify(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> sender.sendMessage(Component.text(message)));
    }

    private record Detection(int fingerprint, Instant detectedAt) {
    }
}
