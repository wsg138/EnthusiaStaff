package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheatTesterCommandTest {
    @Test
    void filterIsCaseInsensitiveAndPreservesOrder() {
        assertEquals(
                List.of("run", "reports"),
                CheatTesterCommand.filter(List.of("select", "run", "reports", "cancel"), "R")
        );
    }

    @Test
    void filterTreatsNullPrefixAsEmpty() {
        assertEquals(
                List.of("one", "two"),
                CheatTesterCommand.filter(List.of("one", "two"), null)
        );
    }

    @Test
    void filterReturnsEmptyForNoMatches() {
        assertEquals(
                List.of(),
                CheatTesterCommand.filter(List.of("select", "run"), "x")
        );
    }
}
