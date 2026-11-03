package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Folia entity/global region ownership for asynchronous typed punishment messages. */
class PunishmentReplySchedulerTest {
    @Test
    void playerReplyUsesEntitySchedulerAndDoesNotTouchLegacyMainScheduler() {
        AtomicReference<Runnable> pending = new AtomicReference<>();
        AtomicInteger messages = new AtomicInteger();
        EntityScheduler scheduler = (EntityScheduler) Proxy.newProxyInstance(
                EntityScheduler.class.getClassLoader(), new Class<?>[]{EntityScheduler.class},
                (self, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        pending.set((Runnable) args[1]);
                        return true;
                    }
                    throw new AssertionError(method.getName());
                });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (self, method, args) -> switch(method.getName()) {
                    case "getScheduler" -> scheduler;
                    case "isOnline" -> true;
                    default -> throw new AssertionError(method.getName());
                });
        Plugin plugin = plugin(null);
        PunishmentReplyScheduler.onSenderThread(plugin, player, messages::incrementAndGet);
        assertFalse(pending.get() == null, "Entity scheduler must receive the delayed reply");
        assertEquals(0, messages.get());
        pending.get().run();
        assertEquals(1, messages.get());
    }

    @Test
    void consoleReplyUsesGlobalRegionSchedulerNotLegacyBukkitScheduler() {
        AtomicReference<Runnable> pending = new AtomicReference<>();
        AtomicInteger messages = new AtomicInteger();
        GlobalRegionScheduler scheduler = (GlobalRegionScheduler) Proxy.newProxyInstance(
                GlobalRegionScheduler.class.getClassLoader(), new Class<?>[]{GlobalRegionScheduler.class},
                (self, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        pending.set((Runnable) args[1]);
                        return null;
                    }
                    throw new AssertionError(method.getName());
                });
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                new Class<?>[]{Server.class}, (self, method, args) -> {
                    if ("getGlobalRegionScheduler".equals(method.getName())) return scheduler;
                    throw new AssertionError(method.getName());
                });
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (self, method, args) -> {
                    throw new AssertionError(method.getName());
                });
        PunishmentReplyScheduler.onSenderThread(plugin(server), console, messages::incrementAndGet);
        assertTrue(pending.get() != null);
        assertEquals(0, messages.get());
        pending.get().run();
        assertEquals(1, messages.get());
    }

    @Test
    void bothTypedPunishmentCommandsUseCommonScheduler() throws Exception {
        String base = "src/main/java/net/enthusia/staff/paper/command/";
        String punish = Files.readString(Path.of(base + "PunishmentCommand.java"));
        String requests = Files.readString(Path.of(base + "PunishmentRequestCommandHandler.java"));
        assertTrue(punish.contains("PunishmentReplyScheduler.onSenderThread(plugin, sender,"));
        assertTrue(requests.contains("PunishmentReplyScheduler.onSenderThread(plugin, sender,"));
        assertFalse(punish.contains("getScheduler().runTask(plugin,"));
        assertFalse(requests.contains("getScheduler().runTask(plugin,"));
    }

    private static Plugin plugin(Server server) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (self, method, args) -> {
                    if ("getServer".equals(method.getName())) {
                        if (server == null) throw new AssertionError("Player replies may not touch Bukkit server scheduler");
                        return server;
                    }
                    throw new AssertionError(method.getName());
                });
    }
}
