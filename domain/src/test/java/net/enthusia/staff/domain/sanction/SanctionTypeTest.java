package net.enthusia.staff.domain.sanction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SanctionTypeTest {
    @Test
    void altInheritanceIncludesEveryBanTypeAndMuteOnly() {
        Set<SanctionType> inheritable = Stream.of(SanctionType.values())
                .filter(SanctionType::inheritsAcrossAltRelationships)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SanctionType.class)));

        assertEquals(EnumSet.of(
                SanctionType.BAN,
                SanctionType.NETWORK_BAN,
                SanctionType.NETWORK_IDENTITY_BAN,
                SanctionType.MUTE
        ), inheritable);
    }
}
