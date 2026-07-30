package net.enthusia.staff.paper.visibility;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

final class ProtocolLibSpectatorTabPacketAdapter implements SpectatorTabPacketAdapter {
    private final ProtocolManager protocolManager;
    private final PacketListener listener;
    private final AtomicBoolean healthy;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ProtocolLibSpectatorTabPacketAdapter(
            ProtocolManager protocolManager,
            PacketListener listener,
            AtomicBoolean healthy
    ) {
        this.protocolManager = protocolManager;
        this.listener = listener;
        this.healthy = healthy;
    }

    static SpectatorTabPacketAdapter install(
            JavaPlugin plugin,
            PlayerInfoTabMasker masker,
            Runnable failureHandler
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(masker, "masker");
        Objects.requireNonNull(failureHandler, "failureHandler");
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        AtomicBoolean healthy = new AtomicBoolean(true);
        PacketListener installed = listener(plugin, masker, failureHandler, healthy);
        manager.addPacketListener(installed);
        return new ProtocolLibSpectatorTabPacketAdapter(manager, installed, healthy);
    }

    private static PacketListener listener(
            JavaPlugin javaPlugin,
            PlayerInfoTabMasker masker,
            Runnable failureHandler,
            AtomicBoolean healthy
    ) {
        return new PacketAdapter(
                javaPlugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.PLAYER_INFO
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!healthy.get()) {
                    return;
                }
                try {
                    rewritePacket(event, masker);
                } catch (RuntimeException exception) {
                    disableAfterFailure(javaPlugin, failureHandler, healthy, exception);
                }
            }
        };
    }

    private static void rewritePacket(PacketEvent event, PlayerInfoTabMasker masker) {
        PacketContainer packet = event.getPacket();
        StructureModifier<List<PlayerInfoData>> modifier = packet.getPlayerInfoDataLists();
        if (modifier.size() == 0) {
            return;
        }
        List<PlayerInfoData> original = modifier.read(0);
        if (original == null || original.isEmpty()) {
            return;
        }
        UUID viewerId = event.getPlayer().getUniqueId();
        PlayerInfoTabMasker.RewriteResult result = masker.rewriteResult(viewerId, original);
        if (!result.changed()) {
            return;
        }
        if (result.entries().isEmpty()) {
            event.setCancelled(true);
        } else {
            modifier.write(0, result.entries());
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
                "ProtocolLib spectator-tab masking failed; spectator staff will be unlisted fail-closed",
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
