package net.enthusia.staff.paper.enforcement;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies online-only effects after the punishment transaction is durable. */
public final class PaperPunishmentCommitEffects implements AutoCloseable {
    private static final long OBSERVER_RETRY_SECONDS = 1L;

    private final JavaPlugin plugin;
    private final Supplier<PunishmentService> punishments;
    private final Supplier<MuteEnforcementListener> muteEnforcement;
    private volatile Optional<PunishmentService> observedService = Optional.empty();
    private volatile Runnable observerRemoval = () -> {
    };
    private volatile ScheduledTask observerTask;
    private volatile boolean closed;

    public PaperPunishmentCommitEffects(
            JavaPlugin plugin,
            Supplier<PunishmentService> punishments,
            Supplier<MuteEnforcementListener> muteEnforcement
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.punishments = Objects.requireNonNull(punishments, "punishments");
        this.muteEnforcement = Objects.requireNonNull(muteEnforcement, "muteEnforcement");
    }

    public void start() {
        if (closed || observerTask != null) {
            return;
        }
        ensureObserverInstalled();
        observerTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> ensureObserverInstalled(),
                1L,
                OBSERVER_RETRY_SECONDS,
                TimeUnit.SECONDS
        );
    }

    void onPunishmentCommitted(PunishmentPlan plan) {
        if (plan == null || closed) {
            return;
        }
        invalidateMuteIfNeeded(plan.targetId(), plan.sanctions());
        CommitEffect effect = effectFor(plan.sanctions());
        if (effect == CommitEffect.NONE) {
            return;
        }
        dispatchToPlayer(plan.targetId(), player -> applyOnlineEffect(player, plan, effect));
    }

    static CommitEffect effectFor(List<SanctionSpec> sanctions) {
        if (sanctions.stream().anyMatch(spec -> spec.type().isBan())) {
            return CommitEffect.BAN;
        }
        if (sanctions.stream().anyMatch(spec -> spec.type() == SanctionType.KICK)) {
            return CommitEffect.KICK;
        }
        if (sanctions.stream().anyMatch(spec -> spec.type() == SanctionType.WARNING)) {
            return CommitEffect.WARNING;
        }
        return CommitEffect.NONE;
    }

    private void invalidateMuteIfNeeded(UUID playerId, List<SanctionSpec> sanctions) {
        if (sanctions.stream().noneMatch(spec -> spec.type() == SanctionType.MUTE)) {
            return;
        }
        MuteEnforcementListener enforcement = muteEnforcement.get();
        if (enforcement != null) {
            enforcement.invalidate(playerId);
        }
    }

    private void applyOnlineEffect(Player player, PunishmentPlan plan, CommitEffect effect) {
        switch (effect) {
            case BAN -> player.kick(Component.text(
                    "You are banned. " + plan.publicReason() + " (case " + plan.caseId() + ')'
            ));
            case KICK -> player.kick(Component.text(
                    "You were kicked. " + plan.publicReason() + " (case " + plan.caseId() + ')'
            ));
            case WARNING -> player.sendMessage(Component.text(
                    "Staff warning: " + plan.publicReason() + " (case " + plan.caseId() + ')'
            ));
            case NONE -> {
            }
            default -> throw new IllegalStateException("Unhandled punishment commit effect");
        }
    }

    private void ensureObserverInstalled() {
        if (closed) {
            return;
        }
        try {
            PunishmentService current = punishments.get();
            if (current == null || observedService.filter(service -> service == current).isPresent()) {
                return;
            }
            observerRemoval.run();
            observerRemoval = current.addCommittedObserver(this::onPunishmentCommitted);
            observedService = Optional.of(current);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Punishment commit effects are not ready yet", exception);
        }
    }

    private void dispatchToPlayer(UUID playerId, java.util.function.Consumer<Player> action) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            player.getScheduler().execute(plugin, () -> action.accept(player), null, 1L);
        });
    }

    @Override
    public void close() {
        closed = true;
        ScheduledTask task = observerTask;
        if (task != null) {
            task.cancel();
        }
        observedService = Optional.empty();
        Runnable removal = observerRemoval;
        observerRemoval = () -> {
        };
        removal.run();
    }

    enum CommitEffect {
        NONE,
        WARNING,
        KICK,
        BAN
    }
}
