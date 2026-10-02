package net.enthusia.staff.paper.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class LiveInventorySessionGateTest {
    @Test
    void twoStaffRacingOneTargetAllowsExactlyOneTransfer() throws Exception {
        LiveInventorySessionGate gate = new LiveInventorySessionGate();
        LiveInventoryTransferExecution first = LiveInventoryTransferExecutionTest.execution();
        LiveInventoryTransferExecution second = LiveInventoryTransferExecutionTest.execution();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = pool.submit(() -> beginAfter(start, gate, first));
            Future<Boolean> secondResult = pool.submit(() -> beginAfter(start, gate, second));
            start.countDown();

            boolean firstWon = get(firstResult);
            boolean secondWon = get(secondResult);
            assertEquals(1, (firstWon ? 1 : 0) + (secondWon ? 1 : 0));
            LiveInventoryTransferExecution winner = firstWon ? first : second;
            LiveInventoryTransferExecution loser = firstWon ? second : first;
            assertEquals(winner, gate.activeTransfer());
            assertTrue(gate.working());

            gate.finishTransfer(winner);
            assertFalse(gate.working());
            assertTrue(gate.beginEdit(loser));
        }
    }

    private static boolean beginAfter(
            CountDownLatch start,
            LiveInventorySessionGate gate,
            LiveInventoryTransferExecution transfer
    ) throws InterruptedException {
        start.await();
        return gate.beginEdit(transfer);
    }

    private static boolean get(Future<Boolean> result) throws ExecutionException, InterruptedException {
        return result.get();
    }
}
