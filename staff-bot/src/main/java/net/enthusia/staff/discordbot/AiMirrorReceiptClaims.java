package net.enthusia.staff.discordbot;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Session-local coordination between the immediate registration worker and
 * slow journal recovery. This guard is inert until explicitly installed in
 * the staging-only durable session; it performs no disk or network operation.
 *
 * <p>Only one worker may process a receipt file at a time. Receipts whose
 * exact on-disk revision was acknowledged are remembered briefly to reject
 * snapshots selected just before the other worker removed that receipt.
 * The journal, not this in-memory guard, remains authoritative across JVM
 * restarts. A caller must still authenticate the {@code Saved} against the
 * journal before making HTTP requests; this class is not a provenance check.</p>
 */
final class AiMirrorReceiptClaims {
    private static final int RECENT_ACK_LIMIT = 4096;

    private final Set<String> active = new HashSet<>();
    private final LinkedHashSet<String> acknowledged = new LinkedHashSet<>();

    /**
     * Claim a receipt before any HTTP or slow-attempt reservation. A false
     * result means another worker owns it or a previous call acknowledged it.
     */
    synchronized boolean tryClaim(AiMirrorEncryptedOutbox.Saved receipt) {
        String key = receiptKey(receipt);
        return !acknowledged.contains(key) && active.add(key);
    }

    /**
     * Release only a previously acquired claim. Mark acknowledged only once
     * durable deletion/ACK succeeded; transient HTTP leaves it retryable.
     */
    synchronized void finish(AiMirrorEncryptedOutbox.Saved receipt, boolean durableAck) {
        String key = receiptKey(receipt);
        if (!active.remove(key)) {
            throw new IllegalStateException("unclaimed mirror receipt completion");
        }
        if (durableAck) {
            acknowledged.add(key);
            if (acknowledged.size() > RECENT_ACK_LIMIT) {
                String oldest = acknowledged.iterator().next();
                acknowledged.remove(oldest);
            }
        }
    }

    synchronized int activeCount() {
        return active.size();
    }

    synchronized int recentAcknowledgedCount() {
        return acknowledged.size();
    }

    private static String receiptKey(AiMirrorEncryptedOutbox.Saved receipt) {
        Objects.requireNonNull(receipt, "receipt");
        // EncryptedOutbox validates UUID receipt filenames on disk. The
        // caller must independently verify the receipt came from that store.
        return Objects.requireNonNull(receipt.fileName(), "receipt filename");
    }
}
