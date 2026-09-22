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
import net.enthusia.staff.paper.scheduler.PlayerEntityScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClientCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "enthusiastaff.client";
    private static final String SAVE_ARGUMENT = "save";
    private static final String UNAVAILABLE_VALUE = "unavailable";

    private final JavaPlugin plugin;
    private final ClientEvidenceCollector collector;
    private final Supplier<ClientEvidenceStore> evidenceStore;
    private final ExecutorService workers;
    private final CommandResponseDispatcher responses;

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
        this.responses = new CommandResponseDispatcher(plugin);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] arguments
    ) {
        if (!CommandPermissionGate.require(
                sender,
                PERMISSION,
                "You do not have permission to inspect or save client evidence."
        )) {
            return true;
        }
        if (arguments.length < 1 || arguments.length > 3) {
            usage(sender, label);
            return true;
        }
        if (arguments.length == 1) {
            beginCapture(sender, arguments[0], false);
            return true;
        }
        if (!arguments[1].equalsIgnoreCase(SAVE_ARGUMENT)) {
            usage(sender, label);
            return true;
        }
        if (arguments.length != 3 || !arguments[2].equals("CONFIRM")) {
            sender.sendMessage(Component.text(
                    "Review only: no evidence was saved. Append the exact word CONFIRM to save it."
            ));
            return true;
        }
        beginCapture(sender, arguments[0], true);
        return true;
    }

    private void beginCapture(CommandSender sender, String targetReference, boolean save) {
        Runnable unavailable = () -> send(
                sender, "Client evidence is live-only; that player is not online on this server."
        );
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                Player target;
                try {
                    target = onlinePlayer(targetReference);
                } catch (RuntimeException exception) {
                    unavailable.run();
                    return;
                }
                if (target == null) {
                    unavailable.run();
                    return;
                }
                PlayerEntityScheduler.execute(plugin, target, () -> {
                    CapturedEvidence evidence = capture(target);
                    if (evidence == null) {
                        send(sender, "Client evidence capture failed; inspect the sanitized server log.");
                        return;
                    }
                    display(sender, evidence.targetName(), evidence.snapshot());
                    if (save) {
                        submitSave(sender, evidence.snapshot());
                    }
                }, unavailable);
            });
        } catch (RuntimeException exception) {
            unavailable.run();
        }
    }

    private CapturedEvidence capture(Player target) {
        try {
            return new CapturedEvidence(target.getName(), collector.capture(target));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Client evidence capture failed", exception);
            return null;
        }
    }

    private void display(CommandSender sender, String targetName, ClientEvidenceSnapshot snapshot) {
        List<Component> messages = new ArrayList<>();
        messages.add(Component.text("Client evidence for " + targetName + ':'));
        messages.add(Component.text(
                "Platform=" + snapshot.platform()
                        + " version=" + snapshot.minecraftVersion().orElse(UNAVAILABLE_VALUE)
                        + " protocol=" + snapshot.protocolVersion()
                                .map(String::valueOf).orElse(UNAVAILABLE_VALUE)
        ));
        messages.add(Component.text(
                "Reported brand=" + snapshot.reportedBrand().orElse(UNAVAILABLE_VALUE)
        ));
        messages.add(Component.text(
                "ViaVersion=" + snapshot.viaVersion()
                        + " plugin-version="
                        + snapshot.viaVersionPluginVersion().orElse(UNAVAILABLE_VALUE)
        ));
        messages.add(Component.text(
                "Floodgate=" + snapshot.floodgate()
                        + " player=" + snapshot.floodgatePlayer()
                        + " Bedrock-version=" + snapshot.bedrockVersion().orElse(UNAVAILABLE_VALUE)
                        + " device=" + snapshot.bedrockDevice().orElse(UNAVAILABLE_VALUE)
        ));
        messages.add(Component.text("Geyser=" + snapshot.geyser()));
        AutoClickerHandshakeEvidence handshake = snapshot.autoClickerHandshake().orElse(null);
        if (handshake == null) {
            messages.add(Component.text(
                    "Enthusia AutoClicker=" + snapshot.autoClicker() + " handshake=not detected"
            ));
        } else {
            messages.add(Component.text(
                    "Enthusia AutoClicker=" + snapshot.autoClicker()
                            + " handshake=reported mod=" + handshake.modVersion()
                            + " loader=" + handshake.loader()
                            + " minecraft=" + handshake.minecraftVersion()
                            + " received=" + handshake.receivedAt()
            ));
            messages.add(Component.text(
                    "The AutoClicker handshake is a convenience signal, not cryptographic proof."
            ));
        }
        messages.add(Component.text(
                "Polar=" + snapshot.polar()
                        + " metadata=" + snapshot.polarMetadata().orElse(UNAVAILABLE_VALUE)
                        + " captured=" + snapshot.capturedAt()
        ));
        responses.send(sender, messages);
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
            send(sender, "The bounded work queue is full; no client evidence was saved.");
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
        responses.send(sender, Component.text(message));
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage: /" + label + " <player|uuid> [save CONFIRM]"));
    }

    private record CapturedEvidence(String targetName, ClientEvidenceSnapshot snapshot) {
        private CapturedEvidence {
            if (targetName == null || targetName.isBlank() || snapshot == null) {
                throw new IllegalArgumentException("captured client evidence is required");
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] arguments
    ) {
        if (!CommandPermissionGate.allows(sender::hasPermission, PERMISSION)) {
            return List.of();
        }
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
        if (arguments.length == 2 && SAVE_ARGUMENT.startsWith(arguments[1].toLowerCase(Locale.ROOT))) {
            return List.of(SAVE_ARGUMENT);
        }
        if (arguments.length == 3 && arguments[1].equalsIgnoreCase(SAVE_ARGUMENT)
                && "CONFIRM".startsWith(arguments[2])) {
            return List.of("CONFIRM");
        }
        return List.of();
    }
}
