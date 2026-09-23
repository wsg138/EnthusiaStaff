package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rosewood.rosechat.api.staff.BridgeRegistration;
import dev.rosewood.rosechat.api.staff.RoseChatStaffService;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.paper.integration.RoseChatIntegration;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffChatCommandTest {
    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void permissionDenialStopsBeforeIntegrationLookup() {
        List<Object> messages = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        Player player = player(false, messages);
        StaffChatCommand command = new StaffChatCommand(() -> {
            lookups.incrementAndGet();
            return null;
        });

        assertTrue(command.onCommand(player, null, "staffchat", new String[0]));
        assertEquals(0, lookups.get());
        assertEquals(List.of(Component.text("You do not have permission to use staff chat.")), messages);
    }

    @Test
    void consoleSenderIsRejectedWithoutIntegrationLookup() {
        List<Object> messages = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        CommandSender sender = sender(true, messages);
        StaffChatCommand command = new StaffChatCommand(() -> {
            lookups.incrementAndGet();
            return null;
        });

        assertTrue(command.onCommand(sender, null, "staffchat", new String[0]));
        assertEquals(0, lookups.get());
        assertEquals(List.of("RoseChat channel state belongs to an online player."), messages);
    }

    @Test
    void trailingArgumentsReturnUsageWithoutIntegrationLookup() {
        List<Object> messages = new ArrayList<>();
        AtomicInteger lookups = new AtomicInteger();
        Player player = player(true, messages);
        StaffChatCommand command = new StaffChatCommand(() -> {
            lookups.incrementAndGet();
            return null;
        });

        assertTrue(command.onCommand(player, null, "sc", new String[]{"extra"}));
        assertEquals(0, lookups.get());
        assertEquals(List.of(Component.text("Usage: /sc")), messages);
    }

    @Test
    void missingOrInactiveBridgeFailsClosed() throws Exception {
        List<Object> missingMessages = new ArrayList<>();
        StaffChatCommand missing = new StaffChatCommand(() -> null);

        assertTrue(missing.onCommand(player(true, missingMessages), null, "staffchat", new String[0]));
        assertEquals(List.of(Component.text("RoseChat staff-channel integration is unavailable.")), missingMessages);

        List<Object> inactiveMessages = new ArrayList<>();
        RoseChatIntegration inactiveIntegration = integration(
                false,
                true,
                Optional.of("staff"),
                new AtomicReference<>()
        );
        StaffChatCommand inactive = new StaffChatCommand(() -> inactiveIntegration);

        assertTrue(inactive.onCommand(player(true, inactiveMessages), null, "staffchat", new String[0]));
        assertEquals(List.of(Component.text("RoseChat staff-channel integration is unavailable.")), inactiveMessages);
    }

    @Test
    void missingStaffChannelReportsConfigurationProblem() throws Exception {
        List<Object> messages = new ArrayList<>();
        AtomicReference<UUID> toggledPlayer = new AtomicReference<>();
        RoseChatIntegration integration = integration(true, false, Optional.empty(), toggledPlayer);
        StaffChatCommand command = new StaffChatCommand(() -> integration);

        assertTrue(command.onCommand(player(true, messages), null, "staffchat", new String[0]));
        assertEquals(PLAYER_ID, toggledPlayer.get());
        assertEquals(List.of(Component.text("RoseChat has no configured staff channel.")), messages);
    }

    @Test
    void successfulToggleReportsTheCurrentChannel() throws Exception {
        List<Object> messages = new ArrayList<>();
        AtomicReference<UUID> toggledPlayer = new AtomicReference<>();
        RoseChatIntegration integration = integration(true, true, Optional.of("staff"), toggledPlayer);
        StaffChatCommand command = new StaffChatCommand(() -> integration);

        assertTrue(command.onCommand(player(true, messages), null, "staffchat", new String[0]));
        assertEquals(PLAYER_ID, toggledPlayer.get());
        assertEquals(List.of(Component.text("RoseChat channel switched to staff.")), messages);
    }

    private static RoseChatIntegration integration(
            boolean active,
            boolean toggleResult,
            Optional<String> currentChannel,
            AtomicReference<UUID> toggledPlayer
    ) throws Exception {
        RoseChatStaffService service = (RoseChatStaffService) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{RoseChatStaffService.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toggleStaffChannel" -> {
                        toggledPlayer.set((UUID) arguments[0]);
                        yield toggleResult;
                    }
                    case "getCurrentChannel" -> currentChannel;
                    case "apiVersion" -> RoseChatStaffService.API_VERSION;
                    default -> defaultValue(method.getReturnType());
                }
        );
        BridgeRegistration registration = (BridgeRegistration) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{BridgeRegistration.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isActive" -> active;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );

        Constructor<RoseChatIntegration> constructor = RoseChatIntegration.class.getDeclaredConstructor(
                RoseChatStaffService.class,
                BridgeRegistration.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(service, registration);
    }

    private static Player player(boolean allowed, List<Object> messages) {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> allowed;
                    case "getUniqueId" -> PLAYER_ID;
                    case "getName" -> "StaffChatTest";
                    case "sendMessage" -> {
                        messages.add(arguments[0]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static CommandSender sender(boolean allowed, List<Object> messages) {
        return (CommandSender) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hasPermission" -> allowed;
                    case "getName" -> "StaffChatConsoleTest";
                    case "sendMessage" -> {
                        messages.add(arguments[0]);
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || !type.isPrimitive()) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }
}
