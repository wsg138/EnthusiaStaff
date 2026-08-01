package net.enthusia.staff.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OperationalModeTest {
    @Test
    void onlyActiveModeAllowsDestructiveWrites() {
        assertTrue(OperationalMode.ACTIVE.destructiveWritesAllowed());

        Arrays.stream(OperationalMode.values())
                .filter(mode -> mode != OperationalMode.ACTIVE)
                .forEach(mode -> assertFalse(mode.destructiveWritesAllowed(), mode.name()));
    }
}
