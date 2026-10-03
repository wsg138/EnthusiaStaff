package net.enthusia.staff.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.staff.StaffTransferSnapshot;
import org.junit.jupiter.api.Test;

/** Cross-server transfer snapshot codec round-trips (overnight/cross-server). */
class TransferSnapshotMessagesTest {
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSFER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void roundTripPreservesEveryField() {
        StaffTransferSnapshot original = new StaffTransferSnapshot(
                PLAYER, TRANSFER, "temp", true, true, StaffRank.MOD, "SPECTATOR", 1_728_000_000_000L);

        StaffTransferSnapshot decoded = TransferSnapshotMessages.decode(TransferSnapshotMessages.encode(original));

        assertEquals(original, decoded);
    }

    @Test
    void roundTripWithNullOptionals() {
        StaffTransferSnapshot original = new StaffTransferSnapshot(
                PLAYER, TRANSFER, "hub", false, false, null, null, 0L);

        StaffTransferSnapshot decoded = TransferSnapshotMessages.decode(TransferSnapshotMessages.encode(original));

        assertEquals(original, decoded);
        assertNull(decoded.rank());
        assertNull(decoded.selectedGameMode());
    }

    @Test
    void decodeToleratesUnknownFieldsFromNewerBackends() {
        String json = "{\"playerId\":\"" + PLAYER + "\",\"transferId\":\"" + TRANSFER + "\","
                + "\"sourceServer\":\"temp\",\"vanished\":true,\"staffModeActive\":true,"
                + "\"capturedAtMillis\":42,\"futureField\":\"ignored\",\"another\":123}";

        StaffTransferSnapshot decoded = TransferSnapshotMessages.decode(json);

        assertEquals(PLAYER, decoded.playerId());
        assertTrue(decoded.vanished());
        assertTrue(decoded.staffModeActive());
        assertEquals(42L, decoded.capturedAtMillis());
    }

    @Test
    void decodeUnknownRankKeepsNullRankInsteadOfFailing() {
        String json = "{\"playerId\":\"" + PLAYER + "\",\"transferId\":\"" + TRANSFER + "\","
                + "\"sourceServer\":\"temp\",\"vanished\":true,\"rank\":\"SUPERADMIN\"}";

        StaffTransferSnapshot decoded = TransferSnapshotMessages.decode(json);

        assertNull(decoded.rank());
        assertTrue(decoded.vanished());
    }

    @Test
    void decodeMissingOrBlankPayloadReturnsNull() {
        assertNull(TransferSnapshotMessages.decode(null));
        assertNull(TransferSnapshotMessages.decode("  "));
        assertNull(TransferSnapshotMessages.decodeNode(null));
    }

    @Test
    void decodeInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class, () -> TransferSnapshotMessages.decode("{not json"));
    }

    @Test
    void encodeRejectsNullSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> TransferSnapshotMessages.encode(null));
    }

    @Test
    void decodeMissingRequiredIdsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TransferSnapshotMessages.decode("{\"sourceServer\":\"temp\"}"));
    }

    @Test
    void missingBooleansDefaultToFalse() {
        String json = "{\"playerId\":\"" + PLAYER + "\",\"transferId\":\"" + TRANSFER
                + "\",\"sourceServer\":\"temp\"}";

        StaffTransferSnapshot decoded = TransferSnapshotMessages.decode(json);

        assertFalse(decoded.vanished());
        assertFalse(decoded.staffModeActive());
    }
}
