package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.player.PlayerResolution;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

final class FreezeQueryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:30:00Z");
    private static final UUID PLAYER_ID = UUID.fromString("42000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("42000000-0000-0000-0000-000000000002");

    @Test
    void statusShowsTheResponsibleStaffMemberAndOfflineDeadline() {
        PlayerIdentity player = identity(PLAYER_ID, "FrozenPlayer");
        PlayerIdentity actor = identity(ACTOR_ID, "StaffMember");
        PlayerDirectory directory = directory(player, actor);
        FreezeRecord record = new FreezeRecord(
                PLAYER_ID,
                ACTOR_ID,
                "Investigating suspicious inventory movement",
                NOW.minusSeconds(60),
                Optional.of(NOW.plusSeconds(600)),
                false,
                3L
        );
        FreezeStore store = proxy(FreezeStore.class, (method, arguments) -> switch (method.getName()) {
            case "readActive" -> Optional.of(record);
            default -> defaultValue(method.getReturnType());
        });
        List<Component> messages = new ArrayList<>();

        handler(directory, store, messages).status(sender(), "FrozenPlayer");

        assertEquals(List.of(
                Component.text("Freeze status for FrozenPlayer (" + PLAYER_ID + ')'),
                Component.text("Applied by StaffMember (" + ACTOR_ID + ") at 2026-09-08 12:29:00 UTC"),
                Component.text("Reason: Investigating suspicious inventory movement"),
                Component.text("Current handling: offline timeout at 2026-09-08 12:40:00 UTC")
        ), messages);
    }

    @Test
    void listRequestsOneExtraRowAndClearlyReportsTruncation() {
        List<FreezeRecord> records = new ArrayList<>();
        for (int index = 0; index < 26; index++) {
            records.add(new FreezeRecord(
                    new UUID(0L, index + 1L),
                    ACTOR_ID,
                    "Investigation " + index,
                    NOW.plusSeconds(index),
                    Optional.empty(),
                    false,
                    0L
            ));
        }
        AtomicInteger requestedLimit = new AtomicInteger();
        FreezeStore store = proxy(FreezeStore.class, (method, arguments) -> switch (method.getName()) {
            case "listActive" -> {
                requestedLimit.set((Integer) arguments[1]);
                yield List.copyOf(records);
            }
            default -> defaultValue(method.getReturnType());
        });
        PlayerDirectory directory = proxy(PlayerDirectory.class,
                (method, arguments) -> Optional.empty());
        List<Component> messages = new ArrayList<>();

        handler(directory, store, messages).list(sender());

        assertEquals(26, requestedLimit.get());
        assertEquals(27, messages.size());
        assertEquals(Component.text("Active player freezes (oldest first):"), messages.getFirst());
        assertEquals(Component.text("Showing the first 25 active freezes."), messages.getLast());
        assertFalse(messages.toString().contains(records.getLast().playerId().toString()));
    }

    private static FreezeQueryHandler handler(
            PlayerDirectory directory,
            FreezeStore store,
            List<Component> messages
    ) {
        return new FreezeQueryHandler(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> directory,
                () -> store,
                (sender, responses) -> messages.addAll(responses)
        );
    }

    private static PlayerDirectory directory(PlayerIdentity player, PlayerIdentity actor) {
        return proxy(PlayerDirectory.class, (method, arguments) -> switch (method.getName()) {
            case "resolve" -> new PlayerResolution.Resolved(
                    player,
                    PlayerResolution.MatchKind.CURRENT_USERNAME
            );
            case "find" -> ACTOR_ID.toString().equals(arguments[0])
                    ? Optional.of(actor)
                    : Optional.empty();
            default -> defaultValue(method.getReturnType());
        });
    }

    private static PlayerIdentity identity(UUID id, String name) {
        return new PlayerIdentity(id, Optional.of(name), PlayerPlatform.JAVA, NOW.minusSeconds(60), NOW);
    }

    private static CommandSender sender() {
        return proxy(CommandSender.class, (method, arguments) -> defaultValue(method.getReturnType()));
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(method, arguments)
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments);
    }
}
