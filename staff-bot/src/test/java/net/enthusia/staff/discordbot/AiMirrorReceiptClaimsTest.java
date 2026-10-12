package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiMirrorReceiptClaimsTest {
    private static final long GUILD = 1234567890123456789L;
    private static final long CHANNEL = 1234567890123456788L;

    @Test
    void onlyOneWorkerCanOwnReceiptAndTransientCanRetry() {
        AiMirrorReceiptClaims claims = new AiMirrorReceiptClaims();
        var receipt = receipt(1);
        assertTrue(claims.tryClaim(receipt));
        assertFalse(claims.tryClaim(receipt));
        assertEquals(1, claims.activeCount());
        claims.finish(receipt, false);
        assertTrue(claims.tryClaim(receipt));
        claims.finish(receipt, true);
        assertFalse(claims.tryClaim(receipt));
        assertEquals(0, claims.activeCount());
        assertEquals(1, claims.recentAcknowledgedCount());
    }

    @Test
    void cannotFinishUnclaimedReceiptOrClaimAcknowledgedSnapshot() {
        AiMirrorReceiptClaims claims = new AiMirrorReceiptClaims();
        var receipt = receipt(2);
        assertThrows(IllegalStateException.class, () -> claims.finish(receipt, true));
        assertTrue(claims.tryClaim(receipt));
        claims.finish(receipt, true);
        assertFalse(claims.tryClaim(receipt));
    }

    @Test
    void parallelFastAndSlowClaimsDoNotOverlap() throws InterruptedException {
        AiMirrorReceiptClaims claims = new AiMirrorReceiptClaims();
        var receipt = receipt(3);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger();
        Runnable tryAcquire = () -> {
            ready.countDown();
            try {
                if (go.await(5, TimeUnit.SECONDS) && claims.tryClaim(receipt)) {
                    acquired.incrementAndGet();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        Thread fast = new Thread(tryAcquire);
        Thread slow = new Thread(tryAcquire);
        fast.start();
        slow.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        fast.join(5000);
        slow.join(5000);
        assertFalse(fast.isAlive());
        assertFalse(slow.isAlive());
        assertEquals(1, acquired.get());
        claims.finish(receipt, false);
    }

    @Test
    void keepsAcknowledgedNamesBoundedWithoutRetainingChatText() {
        AiMirrorReceiptClaims claims = new AiMirrorReceiptClaims();
        for (int i = 0; i < 4100; i++) {
            var receipt = receipt(i + 10);
            assertTrue(claims.tryClaim(receipt));
            claims.finish(receipt, true);
        }
        assertEquals(0, claims.activeCount());
        assertEquals(4096, claims.recentAcknowledgedCount());
    }

    private static AiMirrorEncryptedOutbox.Saved receipt(int n) {
        String file = "00000000-0000-0000-0000-" + String.format("%012x", n) + ".mir";
        var publication = new DiscordChatPublication(GUILD, CHANNEL,
                "1234567890123456787", "rosechat:synthetic:claimed",
                "synthetic chat text never retained by claim guard");
        return new AiMirrorEncryptedOutbox.Saved(file, publication, 1000L, 0, 0L);
    }
}
