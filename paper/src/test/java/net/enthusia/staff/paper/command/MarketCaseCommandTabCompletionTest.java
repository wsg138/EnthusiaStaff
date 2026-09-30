package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarketCaseCommandTabCompletionTest {
    @Test
    void zeroArgumentsOfferEveryMarketCaseAction() {
        assertEquals(
                List.of("prepare", "approve", "release", "restore", "blacklist", "unblacklist", "status"),
                MarketCaseCommand.tabCompletions(new String[0])
        );
    }
}
