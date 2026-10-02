package net.enthusia.staff.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.enthusia.staff.domain.migration.LegacySanctionType;
import org.junit.jupiter.api.Test;

class LiteBansReaderTest {
    @Test
    void uuidOnlyLiteBansBansStayUuidOnlyWhenIpIsAbsent() {
        assertEquals(LegacySanctionType.BAN,
                LiteBansReader.effectiveSanctionType(LegacySanctionType.BAN, true, Optional.empty()));
        assertEquals(LegacySanctionType.BAN,
                LiteBansReader.effectiveSanctionType(LegacySanctionType.BAN, true, Optional.of("#")));
        assertEquals(LegacySanctionType.IP_BAN,
                LiteBansReader.effectiveSanctionType(
                        LegacySanctionType.BAN, true, Optional.of("192.0.2.1")));
    }

    @Test
    void liteBansHistoryNullSentinelIsNotAnIpObservation() {
        assertTrue(LiteBansReader.liteBansNullSentinel("#"));
        assertTrue(LiteBansReader.liteBansNullSentinel("#hidden"));
        assertFalse(LiteBansReader.liteBansNullSentinel(null));
        assertFalse(LiteBansReader.liteBansNullSentinel("192.0.2.1"));
    }

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
