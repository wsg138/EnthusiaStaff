package net.enthusia.staff.domain.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MainMinecraftAccountPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    @Test
    void firstLinkStartsAsAutomaticMain() {
        UUID first = UUID.randomUUID();
        UUID laterButMoreActive = UUID.randomUUID();

        MainMinecraftAccount selected = MainMinecraftAccountPolicy.select(
                List.of(
                        account(first, NOW.minusSeconds(60), 10),
                        account(laterButMoreActive, NOW, 1000)
                ),
                Optional.empty(),
                Optional.empty()
        );

        assertEquals(first, selected.playerId());
        assertEquals(MainAccountSelectionSource.AUTOMATIC, selected.source());
    }

    @Test
    void automaticMainDoesNotFlapBelowTwentyFivePercentAdvantage() {
        UUID current = UUID.randomUUID();
        UUID challenger = UUID.randomUUID();

        MainMinecraftAccount selected = MainMinecraftAccountPolicy.select(
                List.of(account(current, NOW.minusSeconds(60), 100), account(challenger, NOW, 124)),
                Optional.of(current),
                Optional.empty()
        );

        assertEquals(current, selected.playerId());
    }

    @Test
    void automaticMainSwitchesAtTwentyFivePercentAdvantage() {
        UUID current = UUID.randomUUID();
        UUID challenger = UUID.randomUUID();

        MainMinecraftAccount selected = MainMinecraftAccountPolicy.select(
                List.of(account(current, NOW.minusSeconds(60), 100), account(challenger, NOW, 125)),
                Optional.of(current),
                Optional.empty()
        );

        assertEquals(challenger, selected.playerId());
    }

    @Test
    void staffOverrideWinsRegardlessOfPlaytime() {
        UUID current = UUID.randomUUID();
        UUID override = UUID.randomUUID();

        MainMinecraftAccount selected = MainMinecraftAccountPolicy.select(
                List.of(account(current, NOW.minusSeconds(60), 10000), account(override, NOW, 1)),
                Optional.of(current),
                Optional.of(override)
        );

        assertEquals(override, selected.playerId());
        assertEquals(MainAccountSelectionSource.STAFF_OVERRIDE, selected.source());
    }

    @Test
    void staleStaffOverrideFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> MainMinecraftAccountPolicy.select(
                List.of(account(UUID.randomUUID(), NOW, 10)),
                Optional.empty(),
                Optional.of(UUID.randomUUID())
        ));
    }

    private static LinkedMinecraftAccount account(UUID playerId, Instant linkedAt, long activeMinutes) {
        return new LinkedMinecraftAccount(playerId, linkedAt, activeMinutes);
    }
}
