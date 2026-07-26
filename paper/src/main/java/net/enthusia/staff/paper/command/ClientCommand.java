package net.enthusia.staff.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import net.enthusia.staff.domain.evidence.AutoClickerHandshakeEvidence;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.ports.ClientEvidenceStore;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final ClientEvidenceCollector collector;
    private final Supplier<ClientEvidenceStore> evidenceStore;
    private final ExecutorService workers;

    public ClientCommand(
            JavaPlugin plugin,
            ClientEvidenceCollector collector,
            Supplier<ClientEvidenceStore> evidenceStore,
            ExecutorService workers
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.collector = java.util.Objects.requireNonNull(collector, "collector");
        this.evidenceStore = java.util.Objects.requireNonNull(evidenceStore, "evidenceStore");
        this.workers = java.util.Objects.requireNonNull(workers, "workers");
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (arguments.length < 1 || arguments.length > 3) {
            usage(sender, label);
            return true;
        }
        Player target = onlinePlayer(arguments[0]);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Client evidence is live-only; that player is not online on this server."
            ));
            return true;
        }
        ClientEvidenceSnapshot snapshot = collector.capture(target);
        display(sender, target.getName(), snapshot);
        if (arguments.length == 1) {
            return true;
        }
        if (!arguments[1].equalsIgnoreCase("save")) {
            usage(sender, label);
            return true;
        }
        if (arguments.length != 3 || !arguments[2].equals("CONFIRM")) {
            sender.sendMessage(Component.text(
                    "Review only: no evidence was saved. Append the exact word CONFIRM to save it."
            ));
            return true;
        }
        submitSave(sender, snapshot);
        return true;
    }

    private void display(CommandSender sender, String targetName, ClientEvidenceSnapshot snapshot) {
        sender.sendMessage(Component.text("Client evidence for " + targetName + ':'));
        sender.sendMessage(Component.text(
                "Platform=" + snapshot.platform()
                        + " version=" + snapshot.minecraftVersion().orElse("unavailable")
                        + " protocol=" + snapshot.protocolVersion()
                                .map(String::valueOf).orElse("unavailable")
        ));
        sender.sendMessage(Component.text(
                "Reported brand=" + snapshot.reportedBrand().orElse("unavailable")
        ));
        sender.sendMessage(Component.text(
                "ViaVersion=" + snapshot.viaVersion()
                        + " plugin-version="
                        + snapshot.viaVersionPluginVersion().orElse("unavailable")
        ));
        sender.sendMessage(Component.text(
                "Floodgate=" + snapshot.floodgate()
                        + " player=" + snapshot.floodgatePlayer()
                        + " Bedrock-version=" + snapshot.bedrockVersion().orElse("unavailable")
                        + " device=" + snapshot.bedrockDevice().orElse("unavailable")
        ));
        sender.sendMessage(Component.text("Geyser=" + snapshot.geyser()));
        AutoClickerHandshakeEvidence handshake = snapshot.autoClickerHandshake().orElse(null);
        if (handshake == null) {
            sender.sendMessage(Component.text(
                    "Enthusia AutoClicker=" + snapshot.autoClicker() + " handshake=not detected"
            ));
        } else {
            sender.sendMessage(Component.text(
                    "Enthusia AutoClicker=" + snapshot.autoClicker()
                            + " handshake=reported mod=" + handshake.modVersion()
                            + " loader=" + handshake.loader()
                            + " minecraft=" + handshake.minecraftVersion()
                            + " received=" + handshake.receivedAt()
            ));
            sender.sendMessage(Component.text(
                    "The AutoClicker handshake is a convenience signal, not cryptographic proof."
            ));
        }
        sender.sendMessage(Component.text(
                "Polar=" + snapshot.polar()
                        + " metadata=" + snapshot.polarMetadata().orElse("unavailable")
                        + " captured=" + snapshot.capturedAt()
        ));
    }

    private void submitSave(CommandSender sender, ClientEvidenceSnapshot snapshot) {
        try {
            workers.execute(() -> {
                ClientEvidenceStore store = evidenceStore.get();
                if (store == null) {
                    send(sender, "Client evidence storage is not ready; no snapshot was saved.");
                    return;
                }
                try {
                    UUID snapshotId = store.save(snapshot);
                    send(sender, "Client evidence saved as " + snapshotId + '.');
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(
                            java.util.logging.Level.SEVERE,
                            "Client evidence save failed",
                            exception
                    );
                    send(sender, "Client evidence save failed; inspect the sanitized server log.");
                }
            });
        } catch (RejectedExecutionException exception) {
            sender.sendMessage(Component.text(
                    "The bounded work queue is full; no client evidence was saved."
            ));
        }
    }

    private Player onlinePlayer(String input) {
        Player byName = plugin.getServer().getPlayerExact(input);
        if (byName != null) {
            return byName;
        }
        try {
            return plugin.getServer().getPlayer(UUID.fromString(input));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void send(CommandSender sender, String message) {
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin,
                () -> sender.sendMessage(Component.text(message))
        );
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage: /" + label + " <player|uuid> [save CONFIRM]"));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (arguments.length == 1) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(player.getName());
                }
            }
            return names;
        }
        if (arguments.length == 2 && "save".startsWith(arguments[1].toLowerCase(Locale.ROOT))) {
            return List.of("save");
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase("save")
                && "CONFIRM".startsWith(arguments[2])) {
            return List.of("CONFIRM");
        }
        return List.of();
    }
}
