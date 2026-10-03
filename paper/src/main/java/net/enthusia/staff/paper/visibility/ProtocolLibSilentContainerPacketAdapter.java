package net.enthusia.staff.paper.visibility;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.BlockPosition;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.enthusia.staff.paper.visibility.SilentContainerTracker.BlockKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ProtocolLib adapter that cancels {@code BLOCK_ACTION} packets (chest/barrel/
 * shulker/ender-chest lid animations, which also drive the client-side open/close
 * sounds) when a vanished staff member is the only one interacting with the
 * container.
 *
 * <p>The vanished opener still receives their inventory GUI normally; only the
 * animation/sound broadcast is suppressed, and only for recipients who do not
 * currently have the container open.
 *
 * <p>On any packet-handling failure the adapter disables itself (fail-open):
 * container animations play normally rather than risking server instability.
 */
final class ProtocolLibSilentContainerPacketAdapter implements SilentContainerPacketAdapter {
    private final ProtocolManager protocolManager;
    private final PacketListener listener;
    private final AtomicBoolean healthy;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ProtocolLibSilentContainerPacketAdapter(
            ProtocolManager protocolManager,
            PacketListener listener,
            AtomicBoolean healthy
    ) {
        this.protocolManager = protocolManager;
        this.listener = listener;
        this.healthy = healthy;
    }

    static SilentContainerPacketAdapter install(
            JavaPlugin plugin,
            SilentContainerTracker tracker,
            Clock clock,
            Runnable failureHandler
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(tracker, "tracker");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(failureHandler, "failureHandler");
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        AtomicBoolean healthy = new AtomicBoolean(true);
        PacketListener installed = listener(plugin, tracker, clock, failureHandler, healthy);
        manager.addPacketListener(installed);
        return new ProtocolLibSilentContainerPacketAdapter(manager, installed, healthy);
    }

    private static PacketListener listener(
            JavaPlugin javaPlugin,
            SilentContainerTracker tracker,
            Clock clock,
            Runnable failureHandler,
            AtomicBoolean healthy
    ) {
        return new PacketAdapter(
                javaPlugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.BLOCK_ACTION
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!healthy.get()) {
                    return;
                }
                try {
                    maybeSuppress(event, tracker, clock);
                } catch (RuntimeException exception) {
                    disableAfterFailure(javaPlugin, failureHandler, healthy, exception);
                }
            }
        };
    }

    private static void maybeSuppress(
            PacketEvent event,
            SilentContainerTracker tracker,
            Clock clock
    ) {
        Player recipient = event.getPlayer();
        if (recipient == null || recipient.getWorld() == null) {
            return;
        }
        PacketContainer packet = event.getPacket();
        BlockPosition position = packet.getBlockPositionModifier().read(0);
        if (position == null) {
            return;
        }
        UUID recipientId = recipient.getUniqueId();
        BlockKey key = new BlockKey(
                recipient.getWorld().getUID(),
                position.getX(),
                position.getY(),
                position.getZ()
        );
        if (tracker.shouldSuppressFor(key, recipientId, clock.millis())) {
            event.setCancelled(true);
        }
    }

    private static void disableAfterFailure(
            JavaPlugin plugin,
            Runnable failureHandler,
            AtomicBoolean healthy,
            RuntimeException exception
    ) {
        if (!healthy.compareAndSet(true, false)) {
            return;
        }
        plugin.getLogger().log(
                Level.SEVERE,
                "ProtocolLib silent-container masking failed; container animations will play normally",
                exception
        );
        failureHandler.run();
    }

    @Override
    public boolean available() {
        return healthy.get() && !closed.get();
    }

    @Override
    public void close() {
        healthy.set(false);
        if (closed.compareAndSet(false, true)) {
            protocolManager.removePacketListener(listener);
        }
    }
}
