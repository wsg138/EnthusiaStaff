package net.enthusia.staff.paper.command;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException.Reason;
import net.enthusia.staff.domain.staff.StaffSessionOwnership;
import net.enthusia.staff.domain.staff.StaffSessionInspection;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.paper.staff.StaffStateCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/** Private console metadata only. This command has no recovery or player mutation dependency. */
public final class StaffSessionInspectionCommand implements CommandExecutor {
    private static final String PREFIX = "StaffSessionInspection ";
    private static final String ASSURANCE = " snapshot_validation=SHA256_AND_HEADER_ONLY"
            + " proxy_handoff=NOT_INSPECTED recovery=NOT_AUTHORIZED";
    private final Supplier<StaffSessionStore> stores;
    private final Executor workers;
    private final Consumer<Runnable> replies;
    private final Clock clock;
    private final String backend;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final StaffStateCodec codec = new StaffStateCodec();

    public StaffSessionInspectionCommand(Supplier<StaffSessionStore> stores, Executor workers,
                                         Consumer<Runnable> replies, Clock clock, String backend) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (backend == null || !backend.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("runtime backend must be a safe identifier");
        }
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Component.text("Staff session inspection is available only from the server console."));
            return true;
        }
        UUID target = target(args);
        if (target == null) {
            sender.sendMessage(Component.text("Usage: /staffsession inspect <canonical-uuid>"));
            return true;
        }
        if (!busy.compareAndSet(false, true)) {
            sender.sendMessage(Component.text(PREFIX + "status=BUSY recovery=NOT_AUTHORIZED"));
            return true;
        }
        try {
            workers.execute(() -> inspectAndReply(sender, target));
        } catch (RuntimeException exception) {
            busy.set(false);
            sender.sendMessage(Component.text(PREFIX + "status=UNAVAILABLE recovery=NOT_AUTHORIZED"));
        }
        return true;
    }

    private static UUID target(String[] args) {
        if (args.length != 2 || !args[0].equalsIgnoreCase("inspect")) {
            return null;
        }
        try {
            UUID id = UUID.fromString(args[1]);
            return id.toString().equalsIgnoreCase(args[1]) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void inspectAndReply(CommandSender sender, UUID target) {
        String result;
        try {
            StaffSessionStore store = Objects.requireNonNull(stores.get(), "storage unavailable");
            var snapshot = store.inspect(target);
            result = snapshot.isEmpty() ? "status=NO_ACTIVE_SESSION recovery=NOT_AUTHORIZED"
                    : verified(snapshot.orElseThrow(), target);
        } catch (StaffSessionInspectionException exception) {
            result = "status=INVALID reason=" + exception.reason().name() + " recovery=NOT_AUTHORIZED";
        } catch (RuntimeException exception) {
            result = "status=UNAVAILABLE recovery=NOT_AUTHORIZED";
        } finally {
            busy.set(false);
        }
        String output = PREFIX + "player_uuid=" + target + " runtime_backend=" + backend
                + " observed_at=" + clock.instant() + ' ' + result;
        replies.accept(() -> sender.sendMessage(Component.text(output)));
    }

    private String verified(StaffSessionInspection snapshot, UUID target) {
        String owner = snapshot.serverId();
        if (!target.equals(snapshot.staffId()) || snapshot.state() == StaffSessionState.CLOSED
                || !owner.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
        String source;
        try {
            source = codec.inspectedBackend(snapshot.schemaVersion(), snapshot.header());
        } catch (IllegalArgumentException exception) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
        if (!StaffSessionOwnership.detached(owner) && !owner.equalsIgnoreCase(source)) {
            throw new StaffSessionInspectionException(Reason.INVALID_METADATA);
        }
        return "status=METADATA_VERIFIED session_id=" + snapshot.sessionId() + " owner_backend=" + owner
                + " snapshot_backend=" + source + " state=" + snapshot.state().name()
                + " revision=" + snapshot.revision() + " session_started_at=" + snapshot.startedAt()
                + " schema=" + snapshot.schemaVersion()
                + " snapshot_bytes=" + snapshot.snapshotBytes() + " checksum=" + snapshot.checksum()
                + ASSURANCE;
    }
}
