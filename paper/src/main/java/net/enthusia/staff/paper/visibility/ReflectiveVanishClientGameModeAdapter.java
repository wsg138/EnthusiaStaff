package net.enthusia.staff.paper.visibility;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class ReflectiveVanishClientGameModeAdapter implements VanishClientGameModeAdapter {
    private final Logger logger;
    private final ReflectionAccess access;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private ReflectiveVanishClientGameModeAdapter(Logger logger, ReflectionAccess access) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.access = Objects.requireNonNull(access, "access");
    }

    static VanishClientGameModeAdapter install(Logger logger, String runtimeLabel) {
        return install(logger, runtimeLabel, ReflectiveVanishClientGameModeAdapter::reflectionAccess);
    }

    static VanishClientGameModeAdapter install(
            Logger logger,
            String runtimeLabel,
            ReflectionAccessResolver resolver
    ) {
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(runtimeLabel, "runtimeLabel");
        Objects.requireNonNull(resolver, "resolver");
        try {
            return new ReflectiveVanishClientGameModeAdapter(logger, resolver.resolve());
        } catch (ReflectiveOperationException | LinkageError exception) {
            String reason = runtimeLabel + " failed required vanish no-clip ABI validation";
            logger.log(Level.SEVERE, reason, exception);
            return VanishClientGameModeAdapterFactory.unavailable(reason);
        }
    }

    @Override
    public boolean available() {
        return healthy.get();
    }

    @Override
    public String unavailableReason() {
        return available() ? "" : "client game-mode presentation failed at runtime";
    }

    @Override
    public boolean present(Player player, GameMode gameMode) {
        if (!available()) {
            return false;
        }
        try {
            sendPresentation(player, gameMode);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            disableAfterFailure(exception);
            return false;
        }
    }

    private void sendPresentation(Player player, GameMode gameMode) throws ReflectiveOperationException {
        Object handle = access.getHandle().invoke(player);
        Object listener = access.connection().get(handle);
        if (listener == null) {
            throw new IllegalStateException("player connection is unavailable");
        }
        Object packet = access.gameEventPacket().newInstance(access.changeGameMode(), gameModeId(gameMode));
        access.sendPacket().invoke(listener, packet);
    }

    private void disableAfterFailure(Throwable exception) {
        if (healthy.compareAndSet(true, false)) {
            logger.log(Level.SEVERE, "Vanish no-clip client presentation failed; adapter is now fail-closed", exception);
        }
    }

    private static ReflectionAccess reflectionAccess() throws ReflectiveOperationException {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> listener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> gameEvent = Class.forName("net.minecraft.network.protocol.game.ClientboundGameEventPacket");
        Field change = gameEvent.getField("CHANGE_GAME_MODE");
        return new ReflectionAccess(
                craftPlayer.getMethod("getHandle"),
                serverPlayer.getField("connection"),
                gameEvent.getConstructor(change.getType(), float.class),
                change.get(null),
                listener.getMethod("send", packet)
        );
    }

    private static float gameModeId(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> 0.0F;
            case CREATIVE -> 1.0F;
            case ADVENTURE -> 2.0F;
            case SPECTATOR -> 3.0F;
        };
    }

    @FunctionalInterface
    interface ReflectionAccessResolver {
        ReflectionAccess resolve() throws ReflectiveOperationException;
    }

    record ReflectionAccess(
            Method getHandle,
            Field connection,
            Constructor<?> gameEventPacket,
            Object changeGameMode,
            Method sendPacket
    ) {
    }
}
