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
import org.junit.jupiter.api.Test;

class RoseChatCommandOwnershipCoordinatorTest {

    @Test
    void roseChatFirstBootRestoresBothControlledLabelsAndKeepsFallbacks() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand("mute");
        StubCommand staffMode = new StubCommand("staff");
        StubCommand roseMute = new StubCommand("mute");
        StubCommand roseStaff = new StubCommand("staff");
        registry.put("mute", roseMute);
        registry.put("rosechat:mute", roseMute);
        registry.put("staff", roseStaff);
        registry.put("staff:staff", roseStaff);

        RoseChatCommandOwnershipCoordinator coordinator = coordinator(
                registry,
                Map.of("mute", staffMute, "staff", staffMode),
                roseStaff
        );

        assertTrue(coordinator.reconcile().isEmpty());
        assertSame(staffMute, registry.command("mute"));
        assertSame(staffMode, registry.command("staff"));
        assertSame(roseMute, registry.command("rosechat:mute"));
        assertSame(roseStaff, registry.command("staff:staff"));
    }

    @Test
    void roseChatEnableAfterStaffOnlyReclaimsTheStolenMuteLabel() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand("mute");
        StubCommand staffMode = new StubCommand("staff");
        StubCommand roseMute = new StubCommand("mute");
        registry.put("mute", roseMute);
        registry.put("rosechat:mute", roseMute);
        registry.put("staff", staffMode);

        RoseChatCommandOwnershipCoordinator coordinator = coordinator(
                registry,
                Map.of("mute", staffMute, "staff", staffMode),
                null
        );

        assertTrue(coordinator.reconcile().isEmpty());
        assertSame(staffMute, registry.command("mute"));
        assertSame(staffMode, registry.command("staff"));
    }

    @Test
    void unrelatedCommandOwnerIsReportedAndNeverReplaced() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand("mute");
        StubCommand staffMode = new StubCommand("staff");
        StubCommand unrelated = new StubCommand("mute");
        registry.put("mute", unrelated);
        registry.put("staff", staffMode);
        List<String> logs = new ArrayList<>();

        RoseChatCommandOwnershipCoordinator coordinator = new RoseChatCommandOwnershipCoordinator(
                registry,
                Map.of("mute", staffMute, "staff", staffMode)::get,
                command -> false,
                logs::add
        );

        assertEquals(List.of("mute"), coordinator.reconcile());
        assertSame(unrelated, registry.command("mute"));
        assertTrue(logs.getFirst().contains("will not replace an unrelated command owner"));
    }

    @Test
    void missingBaseLabelCanBeRecoveredFromTheNamespacedStaffCommand() {
        FakeRegistry registry = new FakeRegistry();
        StubCommand staffMute = new StubCommand("mute");
        StubCommand staffMode = new StubCommand("staff");
        registry.put("staff", staffMode);

        RoseChatCommandOwnershipCoordinator coordinator = coordinator(
                registry,
                Map.of("mute", staffMute, "staff", staffMode),
                null
        );

        assertTrue(coordinator.reconcile().isEmpty());
        assertSame(staffMute, registry.command("mute"));
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
