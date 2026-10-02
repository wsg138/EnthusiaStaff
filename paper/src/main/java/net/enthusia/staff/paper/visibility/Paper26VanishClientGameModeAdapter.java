package net.enthusia.staff.paper.visibility;

import io.papermc.paper.ServerBuildInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class Paper26VanishClientGameModeAdapter implements VanishClientGameModeAdapter {
    static final String SUPPORTED_MINECRAFT_VERSION = "26.2";
    static final int SUPPORTED_PAPER_BUILD = 129;
    private final Logger logger;
    private final ReflectionAccess access;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    private Paper26VanishClientGameModeAdapter(Logger logger, ReflectionAccess access) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.access = Objects.requireNonNull(access, "access");
    }

    static VanishClientGameModeAdapter install(Logger logger) {
        ServerBuildInfo info = ServerBuildInfo.buildInfo();
        String incompatibility = incompatibility(info);
        if (incompatibility != null) {
            logIncompatibility(logger, incompatibility);
            return new UnavailableClientGameModeAdapter(incompatibility);
        }
        try {
            return new Paper26VanishClientGameModeAdapter(logger, reflectionAccess());
        } catch (ReflectiveOperationException | LinkageError exception) {
            String reason = "Paper 26.2 build 129 internals do not match the pinned no-clip adapter";
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, reason, exception);
            }
            return new UnavailableClientGameModeAdapter(reason);
        }
    }

    private static void logIncompatibility(Logger logger, String incompatibility) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, "Vanish no-clip client adapter disabled: {0}", incompatibility);
        }
    }

    static boolean supportsRuntime(boolean paperBrand, String minecraftVersion, OptionalInt buildNumber) {
        return paperBrand
                && SUPPORTED_MINECRAFT_VERSION.equals(minecraftVersion)
                && buildNumber.isPresent()
                && buildNumber.getAsInt() == SUPPORTED_PAPER_BUILD;
    }

    private static String incompatibility(ServerBuildInfo info) {
        boolean paperBrand = ServerBuildInfo.BRAND_PAPER_ID.equals(info.brandId());
        if (supportsRuntime(paperBrand, info.minecraftVersionId(), info.buildNumber())) {
            return null;
        }
        return "requires exact Paper " + SUPPORTED_MINECRAFT_VERSION
                + " build " + SUPPORTED_PAPER_BUILD + ", found "
                + info.asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE);
    }

    private static ReflectionAccess reflectionAccess() throws ReflectiveOperationException {
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> listener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> gameEvent = Class.forName("net.minecraft.network.protocol.game.ClientboundGameEventPacket");
        Field change = gameEvent.getField("CHANGE_GAME_MODE");
        Object changeValue = change.get(null);
        Constructor<?> constructor = gameEvent.getConstructor(change.getType(), float.class);
        return new ReflectionAccess(
                craftPlayer.getMethod("getHandle"),
                serverPlayer.getField("connection"),
                constructor,
                changeValue,
                listener.getMethod("send", packet)
        );
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
            Object handle = access.getHandle().invoke(player);
            Object listener = access.connection().get(handle);
            if (listener == null) {
                return false;
            }
            Object packet = access.gameEventPacket().newInstance(access.changeGameMode(), gameModeId(gameMode));
            access.sendPacket().invoke(listener, packet);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableAfterFailure(exception);
            return false;
        }
    }

    private void disableAfterFailure(Exception exception) {
        if (healthy.compareAndSet(true, false) && logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, "Vanish no-clip client presentation failed; adapter is now fail-closed", exception);
        }
    }

    private static float gameModeId(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> 0.0F;
            case CREATIVE -> 1.0F;
            case ADVENTURE -> 2.0F;
            case SPECTATOR -> 3.0F;
        };
    }

    private record ReflectionAccess(
            Method getHandle,
            Field connection,
            Constructor<?> gameEventPacket,
            Object changeGameMode,
            Method sendPacket
    ) {
    }

    private record UnavailableClientGameModeAdapter(String unavailableReason)
            implements VanishClientGameModeAdapter {
        private UnavailableClientGameModeAdapter {
            Objects.requireNonNull(unavailableReason, "unavailableReason");
        }

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean present(Player player, GameMode gameMode) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(gameMode, "gameMode");
            return false;
        }
    }
}
