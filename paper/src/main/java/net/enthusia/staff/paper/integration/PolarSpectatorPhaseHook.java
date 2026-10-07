package net.enthusia.staff.paper.integration;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;
import top.polar.api.PolarApi;
import top.polar.api.PolarApiAccessor;
import top.polar.api.event.listener.RegisteredListener;
import top.polar.api.event.listener.repository.EventListenerRepository;
import top.polar.api.exception.PolarNotLoadedException;
import top.polar.api.loader.LoaderApi;
import top.polar.api.user.event.MitigationEvent;

/**
 * Polar-loaded half of the Spectator compatibility integration.
 *
 * <p>Polar requires its enable callback to be registered during plugin load. The callback then
 * registers through Polar's own event repository. No Bukkit player state is read on Polar's event
 * thread; eligibility and diagnostics use immutable snapshots maintained by the non-Polar half.</p>
 */
public final class PolarSpectatorPhaseHook implements Runnable {
    private static final AtomicReference<PolarSpectatorPhaseHook> ACTIVE = new AtomicReference<>();
    private static final long DIAGNOSTIC_INTERVAL_NANOS = Duration.ofSeconds(2).toNanos();

    private final Logger logger;
    private final Object lifecycleLock = new Object();
    private final ConcurrentHashMap<DiagnosticKey, Long> diagnosticWindows = new ConcurrentHashMap<>();
    private boolean closed;
    private EventListenerRepository events;
    private RegisteredListener<MitigationEvent> registration;

    private PolarSpectatorPhaseHook(JavaPlugin plugin) {
        logger = Objects.requireNonNull(plugin, "plugin").getLogger();
    }

    public static void registerEnableCallback(JavaPlugin plugin) {
        PolarSpectatorPhaseHook hook = new PolarSpectatorPhaseHook(plugin);
        PolarSpectatorPhaseHook previous = ACTIVE.getAndSet(hook);
        if (previous != null) {
            previous.unregister();
        }
        LoaderApi.registerEnableCallback(hook);
    }

    @Override
    public void run() {
        synchronized (lifecycleLock) {
            if (closed || registration != null) {
                return;
            }
            try {
                PolarApi api = PolarApiAccessor.access().get();
                if (api == null) {
                    throw new IllegalStateException("Polar API became unavailable during enable callback");
                }
                events = api.events().repository();
                registration = events.registerListener(MitigationEvent.class, this::onMitigation);
                logger.info(
                        "Polar compatibility active: PHASE mitigation is exempted only for explicit staff in Spectator"
                );
            } catch (PolarNotLoadedException | RuntimeException failure) {
                logger.log(Level.WARNING, "Polar Spectator phase compatibility could not start", failure);
            }
        }
    }

    private void onMitigation(MitigationEvent event) {
        if (event.cancelled()) {
            return;
        }
        UUID playerId = event.user().uuid();
        String checkType = event.check().type().name();
        PolarSpectatorPhaseCompatibility.EligibilitySnapshot snapshot =
                PolarSpectatorPhaseCompatibility.snapshot(playerId);
        boolean eligible = snapshot != null && snapshot.eligible();
        boolean cancelled = PolarSpectatorPhasePolicy.shouldCancelMitigation(checkType, eligible);
        if (cancelled) {
            event.cancelled(true);
        }
        logDiagnostic(playerId, checkType, snapshot, cancelled);
    }

    private void logDiagnostic(
            UUID playerId,
            String checkType,
            PolarSpectatorPhaseCompatibility.EligibilitySnapshot snapshot,
            boolean cancelled
    ) {
        if (!diagnosticRelevant(snapshot, checkType)) {
            return;
        }
        DiagnosticKey key = diagnosticKey(playerId, checkType, snapshot, cancelled);
        long now = System.nanoTime();
        if (!reserveDiagnosticWindow(key, now)) {
            return;
        }
        logDiagnosticMessage(playerId, checkType, snapshot, cancelled, now);
    }

    private static boolean diagnosticRelevant(
            PolarSpectatorPhaseCompatibility.EligibilitySnapshot snapshot,
            String checkType
    ) {
        return snapshot != null
                && (snapshot.gameMode() == GameMode.SPECTATOR
                || ("PHASE".equals(checkType) && snapshot.hasStaffSignal()));
    }

    private static DiagnosticKey diagnosticKey(
            UUID playerId,
            String checkType,
            PolarSpectatorPhaseCompatibility.EligibilitySnapshot snapshot,
            boolean cancelled
    ) {
        return new DiagnosticKey(
                playerId,
                checkType,
                snapshot.gameMode(),
                snapshot.resolvedRank(),
                snapshot.identityRank(),
                snapshot.legacyRank(),
                snapshot.unrestricted(),
                snapshot.eligible(),
                cancelled
        );
    }

    private boolean reserveDiagnosticWindow(DiagnosticKey key, long now) {
        Long previous = diagnosticWindows.putIfAbsent(key, now);
        if (previous == null) {
            return true;
        }
        if (now - previous < DIAGNOSTIC_INTERVAL_NANOS) {
            return false;
        }
        return diagnosticWindows.replace(key, previous, now);
    }

    private void logDiagnosticMessage(
            UUID playerId,
            String checkType,
            PolarSpectatorPhaseCompatibility.EligibilitySnapshot snapshot,
            boolean cancelled,
            long now
    ) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(
                    "Polar Spectator mitigation: player=" + playerId
                            + " check=" + checkType
                            + " cancelled=" + cancelled
                            + " snapshotAgeMs=" + snapshot.ageMillis(now)
                            + " " + snapshot.summary()
            );
        }
    }

    public static void close() {
        PolarSpectatorPhaseHook hook = ACTIVE.getAndSet(null);
        if (hook != null) {
            hook.unregister();
        }
    }

    private void unregister() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            diagnosticWindows.clear();
            EventListenerRepository currentEvents = events;
            RegisteredListener<MitigationEvent> currentRegistration = registration;
            if (currentEvents != null && currentRegistration != null) {
                currentEvents.unregisterListener(currentRegistration);
            }
        }
    }

    private record DiagnosticKey(
            UUID playerId,
            String checkType,
            GameMode gameMode,
            net.enthusia.staff.domain.auth.StaffRank resolvedRank,
            net.enthusia.staff.domain.auth.StaffRank identityRank,
            net.enthusia.staff.domain.auth.StaffRank legacyRank,
            boolean unrestricted,
            boolean eligible,
            boolean cancelled
    ) {
    }
}
