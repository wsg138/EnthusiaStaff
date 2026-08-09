package net.enthusia.staff.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkAddressTextGuardTest {
    @Test
    void detectsIpv4AndIpv6LiteralsInsideStaffNotes() {
        assertTrue(NetworkAddressTextGuard.containsRawAddress("matched 203.0.113.42 during review"));
        assertTrue(NetworkAddressTextGuard.containsRawAddress("matched [2001:db8::42] during review"));
        assertTrue(NetworkAddressTextGuard.containsRawAddress("loopback ::1 should still be private"));
    }

    @Test
    void ignoresOrdinaryNumbersTimesAndInvalidAddressLikeText() {
        assertFalse(NetworkAddressTextGuard.containsRawAddress("case 2030 has 113 reports"));
        assertFalse(NetworkAddressTextGuard.containsRawAddress("reviewed at 12:34:56"));
        assertFalse(NetworkAddressTextGuard.containsRawAddress("999.999.999.999 is not an address"));
        assertFalse(NetworkAddressTextGuard.containsRawAddress("evidence id deadbeef"));
    }

    @Test
    void durableTextGuardRejectsRawAddresses() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NetworkAddressTextGuard.requireNoRawAddress("shared by 10.0.0.8")
        );
    }
}
