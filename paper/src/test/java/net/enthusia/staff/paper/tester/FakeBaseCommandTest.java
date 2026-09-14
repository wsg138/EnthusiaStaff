package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class FakeBaseCommandTest {
    @Test
    void directArgumentsReuseTheExistingFakeBaseRoute() {
        assertArrayEquals(
                new String[]{"base", "create", "Target"},
                FakeBaseCommand.routedArguments(new String[]{"create", "Target"})
        );
    }

    @Test
    void emptyArgumentsReachTheExistingUsageRoute() {
        assertArrayEquals(
                new String[]{"base"},
                FakeBaseCommand.routedArguments(new String[0])
        );
    }
}
