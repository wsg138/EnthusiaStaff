package net.enthusia.staff.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandMap;
import org.junit.jupiter.api.Test;

class RoseChatCommandOwnershipCoordinatorTest {
    private static final String MUTE = "mute";
    private static final String STAFF = "staff";

    @Test
    void paperForwardingMapClaimsThroughItsBrigadierAccessors() {
        Map<String, Command> dispatcher = new HashMap<>();
        Map<String, Command> forwarding = new HashMap<>() {
            @Override
            public Command get(Object key) { return dispatcher.get(key); }

            @Override
            public Command put(String key, Command value) { return dispatcher.put(key, value); }
        };
        CommandMap commandMap = (CommandMap) java.lang.reflect.Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{CommandMap.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getKnownCommands")) return forwarding;
                    if (method.getName().equals("getCommand")) return forwarding.get(arguments[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
        var registry = new RoseChatCommandOwnershipCoordinator.PaperCommandRegistry(commandMap);
        Command old = new StubCommand(MUTE);
        Command replacement = new StubCommand(MUTE);
        dispatcher.put(MUTE, old);
        assertTrue(registry.claim(MUTE, old, replacement));
        assertSame(replacement, dispatcher.get(MUTE));
        assertTrue(registry.claim(STAFF, null, replacement));
        org.junit.jupiter.api.Assertions.assertFalse(registry.claim(MUTE, old, old));
        assertSame(replacement, dispatcher.get(MUTE));
    }

    @Test
    void roseChatFirstBootRestoresBothControlledLabelsAndKeepsFallbacks() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand(MUTE);
        StubCommand staffMode = new StubCommand(STAFF);
        StubCommand roseMute = new StubCommand(MUTE);
        StubCommand roseStaff = new StubCommand(STAFF);
        registry.put(MUTE, roseMute);
        registry.put("rosechat:mute", roseMute);
        registry.put(STAFF, roseStaff);
        registry.put("staff:staff", roseStaff);

        RoseChatCommandOwnershipCoordinator coordinator = coordinator(
                registry,
                Map.of(MUTE, staffMute, STAFF, staffMode),
                roseStaff
        );

        assertTrue(coordinator.reconcile().isEmpty());
        assertSame(staffMute, registry.command(MUTE));
        assertSame(staffMode, registry.command(STAFF));
        assertSame(roseMute, registry.command("rosechat:mute"));
        assertSame(roseStaff, registry.command("staff:staff"));
    }

    @Test
    void roseChatEnableAfterStaffOnlyReclaimsTheStolenMuteLabel() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand(MUTE);
        StubCommand staffMode = new StubCommand(STAFF);
        StubCommand roseMute = new StubCommand(MUTE);
        registry.put(MUTE, roseMute);
        registry.put("rosechat:mute", roseMute);
        registry.put(STAFF, staffMode);

        RoseChatCommandOwnershipCoordinator coordinator = coordinator(
                registry,
                Map.of(MUTE, staffMute, STAFF, staffMode),
                null
        );

        assertTrue(coordinator.reconcile().isEmpty());
        assertSame(staffMute, registry.command(MUTE));
        assertSame(staffMode, registry.command(STAFF));
    }

    @Test
    void unrelatedCommandOwnerIsReportedAndNeverReplaced() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand(MUTE);
        StubCommand staffMode = new StubCommand(STAFF);
        StubCommand unrelated = new StubCommand(MUTE);
        registry.put(MUTE, unrelated);
        registry.put(STAFF, staffMode);
        List<String> logs = new ArrayList<>();

        RoseChatCommandOwnershipCoordinator coordinator = new RoseChatCommandOwnershipCoordinator(
                registry,
                Map.of(MUTE, staffMute, STAFF, staffMode)::get,
                command -> false,
                logs::add
        );

        assertEquals(List.of(MUTE), coordinator.reconcile());
        assertSame(unrelated, registry.command(MUTE));
        assertTrue(logs.getFirst().contains("will not replace an unrelated command owner"));
    }

    @Test
    void missingBaseLabelCanBeRecoveredFromTheNamespacedStaffCommand() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand(MUTE);
        StubCommand staffMode = new StubCommand(STAFF);
        registry.put(STAFF, staffMode);

        RoseChatCommandOwnershipCoordinator coordinator = coordinator(
                registry,
                Map.of(MUTE, staffMute, STAFF, staffMode),
                null
        );

        assertTrue(coordinator.reconcile().isEmpty());
        assertSame(staffMute, registry.command(MUTE));
    }

    private static RoseChatCommandOwnershipCoordinator coordinator(
            FakeRegistry registry,
            Map<String, Command> staffCommands,
            Command roseStaff
    ) {
        return new RoseChatCommandOwnershipCoordinator(
                registry,
                staffCommands::get,
                command -> command == roseStaff,
                ignored -> {
                }
        );
    }

    private static final class FakeRegistry implements RoseChatCommandOwnershipCoordinator.CommandRegistry {
        // Tests are single-threaded; this fake registry intentionally has no synchronization.
        @SuppressWarnings("PMD.DocumentMutableMapFieldConcurrency")
        private final Map<String, Command> commands = new HashMap<>();

        void put(String label, Command command) {
            commands.put(label, command);
        }

        @Override
        public Command command(String label) {
            return commands.get(label);
        }

        @Override
        public boolean claim(String label, Command expected, Command replacement) {
            if (expected == null) {
                return commands.putIfAbsent(label, replacement) == null;
            }
            return commands.replace(label, expected, replacement);
        }
    }

    private static final class StubCommand extends Command {
        private StubCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return true;
        }
    }
}
