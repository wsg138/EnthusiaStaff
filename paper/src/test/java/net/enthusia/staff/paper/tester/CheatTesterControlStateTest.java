package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.enthusia.staff.domain.tester.CheatTesterType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CheatTesterControlStateTest {
    private static final Instant NOW = Instant.parse("2026-08-07T12:00:10Z");
    private static final String TESTER_PERMISSION = "enthusiastaff.cheattester";

    @Test
    void selectionRequiresAuthorizationAndProviderAvailability() {
        UUID staffId = UUID.randomUUID();
        Set<UUID> staffMode = new HashSet<>();
        CheatTesterControlState state = state(staffMode, new ConcurrentHashMap<>(), settings(8, 2));
        Player denied = player(staffId, Set.of(), true);
        Player staff = player(staffId, Set.of(TESTER_PERMISSION), true);

        assertFalse(state.authorized(null));
        assertFalse(state.select(denied, CheatTesterType.VELOCITY, true));
        assertFalse(state.select(staff, CheatTesterType.VELOCITY, true));
        assertEquals(CheatTesterType.TOTEM_REFILL, state.selected(staffId));

        staffMode.add(staffId);
        assertFalse(state.select(staff, CheatTesterType.FAKE_ENTITY, false));
        assertTrue(state.select(staff, CheatTesterType.VELOCITY, true));
        assertEquals(CheatTesterType.VELOCITY, state.selected(staffId));

        state.clearSelection(staffId);
        assertEquals(CheatTesterType.TOTEM_REFILL, state.selected(staffId));
        assertTrue(state.select(staff, CheatTesterType.NO_FALL, true));
        state.clear();
        assertEquals(CheatTesterType.TOTEM_REFILL, state.selected(staffId));
    }

    @Test
    void cycleSkipsUnavailableFakeEntityType() {
        UUID staffId = UUID.randomUUID();
        Set<UUID> staffMode = new HashSet<>(Set.of(staffId));
        CheatTesterControlState state = state(staffMode, new ConcurrentHashMap<>(), settings(8, 2));
        Player staff = player(staffId, Set.of(TESTER_PERMISSION), true);

        assertTrue(state.select(staff, CheatTesterType.AUTO_ARMOR, true));
        state.cycle(staff, false);
        assertEquals(CheatTesterType.TOTEM_REFILL, state.selected(staffId));

        assertTrue(state.select(staff, CheatTesterType.AUTO_ARMOR, true));
        state.cycle(staff, true);
        assertEquals(CheatTesterType.FAKE_ENTITY, state.selected(staffId));
    }

    @Test
    void startPolicyRejectsInvalidParticipantsProviderAndCapacity() {
        UUID staffId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Set<UUID> staffMode = new HashSet<>(Set.of(staffId));
        Map<UUID, CheatTesterSession> active = new ConcurrentHashMap<>();
        CheatTesterControlState state = state(staffMode, active, settings(2, 1));
        Player staff = player(staffId, Set.of(TESTER_PERMISSION), true);
        Player target = player(targetId, Set.of(), true);

        assertFalse(state.canStart(staff, null, CheatTesterType.VELOCITY, true, false));
        assertFalse(state.canStart(staff, staff, CheatTesterType.VELOCITY, true, false));
        assertFalse(state.canStart(staff, player(targetId, Set.of(), false), CheatTesterType.VELOCITY, true, false));
        assertFalse(state.canStart(staff, target, null, true, false));
        assertFalse(state.canStart(staff, target, CheatTesterType.VELOCITY, true, true));
        assertFalse(state.canStart(staff, target, CheatTesterType.FAKE_ENTITY, false, false));
        assertTrue(state.canStart(staff, target, CheatTesterType.VELOCITY, true, false));

        CheatTesterSession owned = session(staffId, UUID.randomUUID(), CheatTesterType.NO_FALL, NOW.minusSeconds(1));
        active.put(owned.targetId, owned);
        assertFalse(state.canStart(staff, target, CheatTesterType.VELOCITY, true, false));

        active.clear();
        CheatTesterSession otherOne = session(UUID.randomUUID(), UUID.randomUUID(), CheatTesterType.NO_FALL, NOW);
        CheatTesterSession otherTwo = session(UUID.randomUUID(), UUID.randomUUID(), CheatTesterType.VELOCITY, NOW);
        active.put(otherOne.targetId, otherOne);
        active.put(otherTwo.targetId, otherTwo);
        assertFalse(state.canStart(staff, target, CheatTesterType.VELOCITY, true, false));
    }

    @Test
    void controlAndStatusRespectOwnershipAndCancelAnyPermission() {
        UUID staffId = UUID.randomUUID();
        UUID otherStaffId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Map<UUID, CheatTesterSession> active = new ConcurrentHashMap<>();
        CheatTesterSession own = session(staffId, targetId, CheatTesterType.NO_FALL, NOW.minusMillis(250));
        CheatTesterSession other = session(otherStaffId, UUID.randomUUID(), CheatTesterType.VELOCITY, NOW.plusMillis(50));
        active.put(own.targetId, own);
        active.put(other.targetId, other);
        CheatTesterControlState state = state(Set.of(staffId), active, settings(8, 2));
        Player staff = player(staffId, Set.of(TESTER_PERMISSION), true);
        Player cancelAny = player(
                UUID.randomUUID(),
                Set.of(TESTER_PERMISSION, "enthusiastaff.cheattester.cancel-any"),
                true
        );

        assertTrue(state.controllable(staff, own));
        assertFalse(state.controllable(staff, other));
        assertFalse(state.controllable(staff, null));
        assertTrue(state.controllable(cancelAny, other));

        assertEquals(1, state.statusLines(staffId, false).size());
        assertTrue(state.statusLines(staffId, false).getFirst().contains("age=250ms"));
        assertEquals(2, state.statusLines(staffId, true).size());
    }

    private static CheatTesterControlState state(
            Set<UUID> staffMode,
            Map<UUID, CheatTesterSession> active,
            CheatTesterSettings settings
    ) {
        return new CheatTesterControlState(
                Clock.fixed(NOW, ZoneOffset.UTC),
                staffMode::contains,
                settings,
                active
        );
    }

    private static CheatTesterSettings settings(int global, int perStaff) {
        return new CheatTesterSettings(
                Duration.ofSeconds(4),
                global,
                perStaff,
                3.0D,
                0.75D,
                0.30D,
                0.70D,
                60
        );
    }

    private static CheatTesterSession session(
            UUID staffId,
            UUID targetId,
            CheatTesterType type,
            Instant startedAt
    ) {
        return new CheatTesterSession(UUID.randomUUID(), staffId, targetId, type, false, startedAt);
    }

    private static Player player(UUID id, Set<String> permissions, boolean online) {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hasPermission" -> permissions.contains((String) arguments[0]);
                    case "isOnline" -> online;
                    case "getName" -> "tester-" + id.toString().substring(0, 8);
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "PlayerProxy[" + id + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        return Array.get(Array.newInstance(type, 1), 0);
    }
}
