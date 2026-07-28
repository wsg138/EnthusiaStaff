package net.enthusia.staff.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LiteBansReaderTest {
    @Test
    void parsesLiteralIpv4AndIpv6WithoutAcceptingHostnames() {
        assertArrayEquals(
                new byte[]{(byte) 192, (byte) 168, 1, 25},
                LiteBansReader.parseNetworkAddress("192.168.1.25").addressBytes()
        );
        assertArrayEquals(
                new byte[]{0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                LiteBansReader.parseNetworkAddress("2001:db8::1").addressBytes()
        );
        assertArrayEquals(
                new byte[]{(byte) 192, 0, 2, 1},
                LiteBansReader.parseNetworkAddress("::ffff:192.0.2.1").addressBytes()
        );
        assertThrows(IllegalArgumentException.class, () -> LiteBansReader.parseNetworkAddress("localhost"));
        assertThrows(IllegalArgumentException.class, () -> LiteBansReader.parseNetworkAddress("999.1.1.1"));
    }

    @Test
    void quotesOnlySingleInspectedSqlIdentifiers() {
        assertEquals("`litebans_bans`", LiteBansReader.quoteInspectedIdentifier("litebans_bans"));

        for (String unsafe : new String[]{
                "bans` WHERE 1=1 --",
                "bans; DROP TABLE bans",
                "schema.bans",
                "bans/*comment*/",
                "bans name"
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LiteBansReader.quoteInspectedIdentifier(unsafe)
            );
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> LiteBansReader.quoteInspectedIdentifier(null)
        );
    }
}
