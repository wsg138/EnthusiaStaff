package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class StaffRestorationComparisonTest {
    @Test
    void separatelyDecodedEmptyInventoryAndFullYawRotationsAreEquivalent() {
        var expected = baseline();
        var equivalent = baseline();
        assertEquals(expected, equivalent);
        equivalent.location().setYaw(equivalent.location().getYaw() + 360);
        var normalized = new StaffStateCodec.Decoded(equivalent.serverId(), equivalent.inventory(),
                equivalent.level(), equivalent.experienceProgress(), equivalent.totalExperience(), equivalent.health(),
                equivalent.absorption(), equivalent.food(), equivalent.saturation(), equivalent.exhaustion(),
                equivalent.effects(), equivalent.location(), equivalent.gameMode(), equivalent.allowFlight(),
                equivalent.flying(), equivalent.flySpeed(), equivalent.walkSpeed(), equivalent.invulnerable(),
                equivalent.collidable(), equivalent.canPickupItems(), equivalent.fireTicks(), equivalent.remainingAir(),
                equivalent.fallDistance());
        assertEquals(expected, normalized);
    }

    @Test
    void everyStoredFieldStillRejectsAnActualStateDifference() throws ReflectiveOperationException {
        var expected = baseline();
        var fields = StaffStateCodec.Decoded.class.getRecordComponents();
        var constructor = StaffStateCodec.Decoded.class.getDeclaredConstructor(
                Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
        Object[] original = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            original[i] = fields[i].getAccessor().invoke(expected);
        }
        for (int i = 0; i < fields.length; i++) {
            String name = fields[i].getName();
            if ("effects".equals(name)) {
                continue;
            }
            Object[] changed = original.clone();
            changed[i] = changedValue(changed[i], name);
            var actual = constructor.newInstance(changed);
            assertFalse(expected.equals(actual), name);
            assertEquals(List.of(name), StaffStateCodec.differingFields(expected, actual));
        }
    }

    private static Object changedValue(Object value, String fieldName) {
        if (value instanceof String) {
            return "OTHER";
        }
        if (value instanceof Number number) {
            return changedNumber(number);
        }
        if (value instanceof Boolean flag) {
            return !flag;
        }
        if (value instanceof Location location) {
            return location.clone().add(0.01, 0, 0);
        }
        if (value instanceof GameMode) {
            return GameMode.SURVIVAL;
        }
        if (value instanceof List<?>) {
            return new ArrayList<>();
        }
        throw new AssertionError("Uncovered field " + fieldName);
    }

    private static Number changedNumber(Number number) {
        if (number instanceof Integer value) {
            return value + 1;
        }
        if (number instanceof Float value) {
            return value + 0.01f;
        }
        if (number instanceof Double value) {
            return value + 0.01;
        }
        throw new AssertionError("Uncovered numeric type " + number.getClass().getName());
    }

    private static StaffStateCodec.Decoded baseline() {
        return new StaffStateCodec.Decoded("SMP", Arrays.asList(null, null), 3, .25f, 42, 20, 0,
                20, 5, 0, Set.of(), new Location(null, 1, 65, 2, 30, 10), GameMode.CREATIVE,
                true, false, .1f, .2f, false, true, true, 0, 300, 0);
    }
}
