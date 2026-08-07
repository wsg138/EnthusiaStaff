package net.enthusia.staff.paper.tester;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class ProtocolLibFakeEntityAdapter implements FakeEntityAdapter {
    private static final int FIRST_SYNTHETIC_ENTITY_ID = 2_000_000_000;
    private static final int MINIMUM_SYNTHETIC_ENTITY_ID = 1_500_000_000;

    interface InteractionHandler {
        /** @return true when the packet belongs to a managed synthetic entity and must be cancelled. */
        boolean handle(UUID viewerId, int entityId, String action);
    }

    private final JavaPlugin plugin;
    private final ProtocolManager manager;
    private final PacketListener listener;
    private final Runnable failureHandler;
    private final AtomicInteger nextEntityId = new AtomicInteger(FIRST_SYNTHETIC_ENTITY_ID);
    private final AtomicBoolean healthy;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ProtocolLibFakeEntityAdapter(
            JavaPlugin plugin,
            ProtocolManager manager,
            PacketListener listener,
            Runnable failureHandler,
            AtomicBoolean healthy
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.listener = listener;
        this.failureHandler = failureHandler;
        this.healthy = healthy;
    }

    static FakeEntityAdapter install(
            JavaPlugin plugin,
            InteractionHandler interactionHandler,
            Runnable failureHandler
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(interactionHandler, "interactionHandler");
        Objects.requireNonNull(failureHandler, "failureHandler");
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        AtomicBoolean healthy = new AtomicBoolean(true);
        PacketListener listener = listener(plugin, interactionHandler, failureHandler, healthy);
        protocolManager.addPacketListener(listener);
        return new ProtocolLibFakeEntityAdapter(plugin, protocolManager, listener, failureHandler, healthy);
    }

    private static PacketListener listener(
            JavaPlugin ownerPlugin,
            InteractionHandler interactionHandler,
            Runnable failureHandler,
            AtomicBoolean healthy
    ) {
        return new PacketAdapter(ownerPlugin, ListenerPriority.HIGHEST, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (!healthy.get()) {
                    return;
                }
                try {
                    PacketContainer packet = event.getPacket();
                    if (packet.getIntegers().size() == 0 || packet.getEnumEntityUseActions().size() == 0) {
                        return;
                    }
                    int entityId = packet.getIntegers().read(0);
                    WrappedEnumEntityUseAction action = packet.getEnumEntityUseActions().read(0);
                    String actionName = action == null ? "UNKNOWN" : action.getAction().name();
                    if (interactionHandler.handle(event.getPlayer().getUniqueId(), entityId, actionName)) {
                        event.setCancelled(true);
                    }
                } catch (RuntimeException | LinkageError exception) {
                    disableAfterFailure(ownerPlugin, failureHandler, healthy, exception);
                }
            }
        };
    }

    @Override
    public boolean available() {
        return healthy.get() && !closed.get();
    }

    @Override
    public Handle create() {
        requireAvailable();
        int entityId = nextEntityId.getAndDecrement();
        if (entityId < MINIMUM_SYNTHETIC_ENTITY_ID) {
            nextEntityId.compareAndSet(entityId - 1, FIRST_SYNTHETIC_ENTITY_ID);
        }
        return new Handle(entityId, UUID.randomUUID());
    }

    @Override
    public void show(Player viewer, Handle handle, Location location) {
        requireAvailable();
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(location, "location");
        if (!viewer.isOnline() || !viewer.getWorld().equals(location.getWorld())) {
            return;
        }
        try {
            PacketContainer packet = manager.createPacket(PacketType.Play.Server.SPAWN_ENTITY, true);
            packet.getIntegers().write(0, handle.entityId());
            packet.getUUIDs().write(0, handle.entityUuid());
            packet.getEntityTypeModifier().write(0, EntityType.ZOMBIE);
            packet.getDoubles()
                    .write(0, location.getX())
                    .write(1, location.getY())
                    .write(2, location.getZ());
            manager.sendServerPacket(viewer, packet);
        } catch (RuntimeException | LinkageError exception) {
            disableAfterFailure(plugin, failureHandler, healthy, exception);
            throw exception;
        }
    }

    @Override
    public void destroy(Player viewer, Handle handle) {
        if (viewer == null || handle == null || closed.get() || !viewer.isOnline()) {
            return;
        }
        try {
            PacketContainer packet = manager.createPacket(PacketType.Play.Server.ENTITY_DESTROY, true);
            packet.getIntLists().write(0, List.of(handle.entityId()));
            manager.sendServerPacket(viewer, packet);
        } catch (RuntimeException | LinkageError exception) {
            disableAfterFailure(plugin, failureHandler, healthy, exception);
            plugin.getLogger().log(Level.WARNING, "Failed to remove a synthetic cheat-test entity", exception);
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("fake-entity packet adapter is unavailable");
        }
    }

    private static void disableAfterFailure(
            JavaPlugin plugin,
            Runnable failureHandler,
            AtomicBoolean healthy,
            Throwable exception
    ) {
        if (!healthy.compareAndSet(true, false)) {
            return;
        }
        plugin.getLogger().log(
                Level.SEVERE,
                "ProtocolLib fake-entity adapter failed; cheat-test fake entities are fail-closed",
                exception
        );
        failureHandler.run();
    }

    @Override
    public void close() {
        healthy.set(false);
        if (closed.compareAndSet(false, true)) {
            manager.removePacketListener(listener);
        }
    }
}
