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
        PacketListener installed = new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.PLAYER_INFO
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (!healthy.get()) {
                    return;
                }
                try {
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
                    List<PlayerInfoData> rewritten = masker.rewrite(viewerId, original);
                    if (rewritten == original) {
                        return;
                    }
                    if (rewritten.isEmpty()) {
                        event.setCancelled(true);
                    } else {
                        modifier.write(0, rewritten);
                    }
                } catch (RuntimeException exception) {
                    if (healthy.compareAndSet(true, false)) {
                        plugin.getLogger().log(
                                Level.SEVERE,
                                "ProtocolLib spectator-tab masking failed; spectator staff will be unlisted fail-closed",
                                exception
                        );
                        failureHandler.run();
                    }
                }
            }
        };
        manager.addPacketListener(installed);
        return new ProtocolLibSpectatorTabPacketAdapter(manager, installed, healthy);
    }

    @Override
    public boolean available() {
        return healthy.get();
    }

    @Override
    public void close() {
        if (healthy.getAndSet(false)) {
            protocolManager.removePacketListener(listener);
        }
    }
}
