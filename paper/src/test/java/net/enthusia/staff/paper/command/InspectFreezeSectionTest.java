package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.freeze.FreezeRecord;
import net.enthusia.staff.domain.ports.FreezeStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class InspectFreezeSectionTest {
    private static final Instant NOW = Instant.parse("2026-09-08T18:00:00Z");
    private static final UUID PLAYER_ID = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("51000000-0000-0000-0000-000000000002");

    @Test
    void inactiveFreezeOffersAuthorizedStaffTheDirectCommand() {
        InspectFreezeSection section = section(() -> store(Optional.empty()));

        Component status = section.render(PLAYER_ID, true).getFirst();

        assertTrue(plain(status).contains("Freeze: not active"));
        assertEquals("/freeze " + PLAYER_ID + ' ', clickCommand(status));
    }

    @Test
    void activeFreezeShowsDurableDetailsAndUnfreezeAction() {
        FreezeRecord record = new FreezeRecord(
                PLAYER_ID,
                ACTOR_ID,
                "Movement investigation",
                NOW.minusSeconds(90),
                Optional.empty(),
                true,
                4L
        );
        InspectFreezeSection section = section(() -> store(Optional.of(record)));

        List<Component> lines = section.render(PLAYER_ID, true);

        assertEquals(2, lines.size());
        assertTrue(plain(lines.getFirst()).contains("Freeze: active"));
        assertTrue(plain(lines.getFirst()).contains(ACTOR_ID.toString()));
        assertTrue(plain(lines.getFirst()).contains("held until staff release"));
        assertTrue(plain(lines.getLast()).contains("Movement investigation"));
        assertEquals("/unfreeze " + PLAYER_ID + ' ', clickCommand(lines.getFirst()));
    }

    @Test
    void statusRemainsVisibleWithoutFreezeMutationPermission() {
        InspectFreezeSection section = section(() -> store(Optional.empty()));

        Component status = section.render(PLAYER_ID, false).getFirst();

        assertTrue(plain(status).contains("Freeze: not active"));
        assertNull(findClick(status));
    }

    @Test
    void storageFailureDoesNotBreakTheRestOfTheInspector() {
        InspectFreezeSection section = section(() -> {
            throw new IllegalStateException("database unavailable");
        });

        Component status = section.render(PLAYER_ID, true).getFirst();

        assertEquals("Freeze: unavailable", plain(status));
        assertNull(findClick(status));
    }

    private static InspectFreezeSection section(java.util.function.Supplier<FreezeStore> freezes) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new InspectFreezeSection(Clock.fixed(NOW, ZoneOffset.UTC), freezes, logger);
    }

    private static FreezeStore store(Optional<FreezeRecord> record) {
        return (FreezeStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{FreezeStore.class},
                (proxy, method, arguments) -> {
                    if ("readActive".equals(method.getName())) {
                        assertEquals(PLAYER_ID, arguments[0]);
                        assertEquals(NOW, arguments[1]);
                        return record;
                    }
                    throw new AssertionError("Unexpected method: " + method.getName());
                }
        );
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String clickCommand(Component component) {
        ClickEvent event = findClick(component);
        assertNotNull(event);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, event.action());
        return ((ClickEvent.Payload.Text) event.payload()).value();
    }

    private static ClickEvent findClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent event = findClick(child);
            if (event != null) {
                return event;
            }
        }
        return null;
    }
}
