package net.enthusia.staff.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionSnapshot;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import org.slf4j.Logger;

/** Supplies the same active-duty LuckPerms context on the proxy as Paper supplies on backends. */
final class VelocityStaffDutyContext implements ContextCalculator<Player>, AutoCloseable {
    static final String CONTEXT_KEY = "enthusiastaff-duty";
    static final String ACTIVE_VALUE = "active";
    static final String UNRESTRICTED_PERMISSION = "enthusiastaff.identity.unrestricted";
    private static final long REFRESH_MILLIS = 500L;
    private static final Set<String> IDENTITY_NODES = Set.of(
            "enthusiastaff.identity.owner",
            "enthusiastaff.identity.admin",
            "enthusiastaff.identity.developer",
            "enthusiastaff.identity.mod",
            "enthusiastaff.identity.helper"
    );

    private final ProxyServer proxy;
    private final Logger logger;
    private final LuckPerms luckPerms;
    private final Supplier<StaffSessionStore> sessions;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean lookupFailureLogged = new AtomicBoolean();
    private final ScheduledTask task;

    private VelocityStaffDutyContext(
            Object plugin,
            ProxyServer proxy,
            Logger logger,
            LuckPerms luckPerms,
            Supplier<StaffSessionStore> sessions
    ) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        luckPerms.getContextManager().registerCalculator(this);
        task = proxy.getScheduler().buildTask(plugin, this::refresh)
                .repeat(REFRESH_MILLIS, TimeUnit.MILLISECONDS)
                .schedule();
    }

    static Optional<VelocityStaffDutyContext> start(
            Object plugin,
            ProxyServer proxy,
            Logger logger,
            Supplier<StaffSessionStore> sessions
    ) {
        if (proxy.getPluginManager().getPlugin("luckperms").isEmpty()) {
            logger.warn("LuckPerms is unavailable; proxy Staff Mode permission context was not installed");
            return Optional.empty();
        }
        try {
            return Optional.of(new VelocityStaffDutyContext(
                    plugin, proxy, logger, LuckPermsProvider.get(), sessions
            ));
        } catch (IllegalStateException exception) {
            logger.error("LuckPerms provider is unavailable; proxy Staff Mode permission context was not installed", exception);
            return Optional.empty();
        }
    }

    @Override
    public void calculate(Player player, ContextConsumer consumer) {
        if (active.contains(player.getUniqueId())) {
            consumer.accept(CONTEXT_KEY, ACTIVE_VALUE);
        }
    }

    private void refresh() {
        if (closed.get() || !refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            Set<UUID> online = new HashSet<>();
            for (Player player : proxy.getAllPlayers()) {
                UUID playerId = player.getUniqueId();
                online.add(playerId);
                boolean current;
                try {
                    current = player.hasPermission(UNRESTRICTED_PERMISSION)
                            || (hasStaffIdentity(player) && hasActiveSession(player));
                } catch (RuntimeException exception) {
                    if (lookupFailureLogged.compareAndSet(false, true)) {
                        logger.warn("Unable to calculate proxy Staff Mode permission context", exception);
                    }
                    current = false;
                }
                if (current == active.contains(playerId)) {
                    continue;
                }
                if (current) {
                    active.add(playerId);
                } else {
                    active.remove(playerId);
                }
                luckPerms.getContextManager().signalContextUpdate(player);
            }
            active.retainAll(online);
        } finally {
            refreshing.set(false);
        }
    }

    private static boolean hasStaffIdentity(Player player) {
        for (String node : IDENTITY_NODES) {
            if (player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveSession(Player player) {
        StaffSessionStore store = sessions.get();
        if (store == null || player.getCurrentServer().isEmpty()) {
            return false;
        }
        String backend = player.getCurrentServer().orElseThrow().getServerInfo().getName();
        try {
            boolean activeSession = matchesActiveSession(store.active(player.getUniqueId()), backend);
            lookupFailureLogged.set(false);
            return activeSession;
        } catch (RuntimeException exception) {
            if (lookupFailureLogged.compareAndSet(false, true)) {
                logger.warn("Unable to verify Staff Mode session for proxy permission context", exception);
            }
            return false;
        }
    }

    static boolean matchesActiveSession(Optional<StaffSessionSnapshot> session, String backend) {
        return backend != null && session
                .filter(snapshot -> snapshot.state() == StaffSessionState.ACTIVE)
                .filter(snapshot -> snapshot.serverId().equalsIgnoreCase(backend))
                .isPresent();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        task.cancel();
        luckPerms.getContextManager().unregisterCalculator(this);
        active.clear();
        for (Player player : proxy.getAllPlayers()) {
            luckPerms.getContextManager().signalContextUpdate(player);
        }
    }
}
