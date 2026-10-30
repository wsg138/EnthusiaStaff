package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DiscordGatewayReconnectDiagnosticsTest {
    @Test
    void initialReadyIsNotCountedAsRecovery() {
        DiscordGatewayReconnectDiagnostics state =
                new DiscordGatewayReconnectDiagnostics(() -> 123456789L);
        assertTrue(state.restored().isEmpty());
    }

    @Test
    void recordsRepeatedDisconnectionsAsOneOutageUntilResumed() {
        AtomicLong time = new AtomicLong(TimeUnit.SECONDS.toNanos(10));
        DiscordGatewayReconnectDiagnostics state =
                new DiscordGatewayReconnectDiagnostics(time::get);

        assertEquals(new DiscordGatewayReconnectDiagnostics.Disconnection(1, 1),
                state.disconnected());
        time.addAndGet(TimeUnit.MILLISECONDS.toNanos(350));
        assertEquals(new DiscordGatewayReconnectDiagnostics.Disconnection(2, 2),
                state.disconnected());
        time.addAndGet(TimeUnit.MILLISECONDS.toNanos(800));
        assertEquals(new DiscordGatewayReconnectDiagnostics.Restoration(1150, 2, 2),
                state.restored().orElseThrow());
        assertTrue(state.restored().isEmpty());

        time.addAndGet(TimeUnit.SECONDS.toNanos(5));
        assertEquals(new DiscordGatewayReconnectDiagnostics.Disconnection(3, 1),
                state.disconnected());
        time.addAndGet(TimeUnit.MILLISECONDS.toNanos(500));
        assertEquals(new DiscordGatewayReconnectDiagnostics.Restoration(500, 1, 3),
                state.restored().orElseThrow());
    }

    @Test
    void negativeClockDeltaCannotGenerateNegativeDowntime() {
        AtomicLong time = new AtomicLong(100L);
        DiscordGatewayReconnectDiagnostics state =
                new DiscordGatewayReconnectDiagnostics(time::get);
        state.disconnected();
        time.set(0);
        assertEquals(0, state.restored().orElseThrow().downtimeMillis());
    }
}
