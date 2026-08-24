package net.enthusia.staff.paper.command;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.application.DiscordSrvMigrationService.MirrorResult;
import net.enthusia.staff.paper.account.PaperAccountLinkRuntime;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Public Minecraft-side account link and confirmed self-unlink commands. */
public final class AccountLinkCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Supplier<PaperAccountLinkRuntime> runtime;
    private final ExecutorService workers;

    public AccountLinkCommand(
            JavaPlugin plugin,
            Supplier<PaperAccountLinkRuntime> runtime,
            ExecutorService workers
    ) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.workers = workers;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Account linking must be completed by the Minecraft player in-game."));
            return true;
        }
        if (CommandRoute.canonicalName(command).equals("unlink")) {
            return unlink(player, arguments);
        }
        if (arguments.length == 0) {
            submit(player, () -> issue(player.getUniqueId()), "Account-link request");
            return true;
        }
        if (arguments.length == 1) {
            String code = arguments[0];
            submit(player, () -> complete(player.getUniqueId(), code), "Account-link completion");
            return true;
        }
        player.sendMessage(Component.text("Usage: /link [code]"));
        return true;
    }

    private boolean unlink(Player player, String[] arguments) {
        if (arguments.length != 1 || !arguments[0].equals("CONFIRM")) {
            player.sendMessage(Component.text("No link was changed. Use /unlink CONFIRM to remove your current Discord link."));
            return true;
        }
        submit(player, () -> unlink(player.getUniqueId()), "Account unlink");
        return true;
    }

    private void issue(UUID playerId) {
        PaperAccountLinkRuntime current = requireRuntime();
        var issued = current.issueFromMinecraft(playerId);
        send(playerId, "Link code: " + issued.code() + " — enter it in Discord within five minutes.");
    }

    private void complete(UUID playerId, String code) {
        PaperAccountLinkRuntime current = requireRuntime();
        var result = current.completeFromMinecraft(code, playerId);
        send(playerId, "Discord account linked." + mirrorSuffix(result.mirrorResult()));
    }

    private void unlink(UUID playerId) {
        PaperAccountLinkRuntime current = requireRuntime();
        var result = current.unlinkFromMinecraft(playerId, true);
        send(playerId, result.changed()
                ? "Discord account unlinked." + mirrorSuffix(result.mirrorResult())
                : "No current Discord link exists.");
    }

    private PaperAccountLinkRuntime requireRuntime() {
        PaperAccountLinkRuntime current = runtime.get();
        if (current == null) {
            throw new IllegalStateException("Account-link storage is not ready");
        }
        return current;
    }

    private static String mirrorSuffix(MirrorResult result) {
        return switch (result) {
            case UPDATED, UNCHANGED -> "";
            case CONFLICT -> " DiscordSRV still has a conflicting legacy link; authoritative state was not overwritten.";
            case UNAVAILABLE, NO_MAIN -> " DiscordSRV legacy mirroring is currently unavailable; the authoritative change is saved.";
        };
    }

    private void submit(Player player, Runnable operation, String name) {
        try {
            workers.execute(() -> {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, name + " failed", exception);
                    send(player.getUniqueId(), name + " failed; no unverified success is assumed.");
                }
            });
        } catch (RejectedExecutionException exception) {
            player.sendMessage(Component.text("The bounded work queue is full; no account-link operation started."));
        }
    }

    private void send(UUID playerId, String message) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.getScheduler().execute(
                plugin,
                () -> player.sendMessage(Component.text(message)),
                null,
                1L
        );
    }
}
