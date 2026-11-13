package net.enthusia.staff.discordbot.retractionstage2;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;
import static net.enthusia.staff.discordbot.retractionstage2.FakeRetractionEngine.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptedReceiptJournalSecurityTest {
    private static final Instant TIME = Instant.parse("2026-10-15T00:00:30Z");
    private static final byte[] MAGIC = {'R', 'T', 'R', '2'};
    @TempDir Path directory;
    private Path file;
    private SecretKey key;

    @BeforeEach
    void setup() throws Exception {
        file = directory.resolve("synthetic.aes");
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        key = generator.generateKey();
    }

    private ReceiptRecord proof(String caseId) {
        SourceEvent original = new SourceEvent("old", "minecraft", "smp",
                "minecraft_public", "global", null, "player-a", TIME.minusSeconds(30));
        SourceEvent current = new SourceEvent("new", "minecraft", "smp",
                "minecraft_public", "global", null, "player-a", TIME.minusSeconds(28));
        Destination dest = new Destination(Surface.DISCORD,
                "1234567890123456789", "1234567890123456788");
        PublicationReceipt receipt = new PublicationReceipt(
                original.eventId(), original.platform(), original.serverScope(),
                original.channelProfile(), original.channelId(), original.conversationId(),
                original.senderId(), dest, "1234567890123456787", null, true);
        Plan plan = plan(current, original, "BLOCK", List.of(original.eventId()),
                List.of(receipt), Set.of(dest));
        return new ReceiptRecord(caseId, 7, current, original, receipt,
                plan.targets().getFirst(), TIME.minusSeconds(27), TIME);
    }

    private Entry entry(String caseId) {
        return new Entry(proof(caseId), DeliveryStatus.RETRYABLE, 1,
                true, TIME.plusSeconds(5));
    }

    private Cipher aead(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(MAGIC);
        return cipher;
    }

    private byte[] plaintext() throws Exception {
        byte[] raw = Files.readAllBytes(file);
        return aead(Cipher.DECRYPT_MODE, Arrays.copyOfRange(raw, 4, 16))
                .doFinal(Arrays.copyOfRange(raw, 16, raw.length));
    }

    private void corruptReopen(byte[] plain) throws Exception {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        byte[] sealed = aead(Cipher.ENCRYPT_MODE, nonce).doFinal(plain);
        Files.write(file, ByteBuffer.allocate(16 + sealed.length)
                .put(MAGIC).put(nonce).put(sealed).array());
        assertThrows(IllegalStateException.class,
                () -> new EncryptedReceiptJournal(file, key));
    }

    @Test
    void invalidMacVersionTruncationAndKeyFailClosed() throws Exception {
        new EncryptedReceiptJournal(file, key).put(entry("a"));
        byte[] valid = Files.readAllBytes(file);
        byte[] corrupted = valid.clone();
        corrupted[corrupted.length - 1] ^= 1;
        Files.write(file, corrupted);
        assertThrows(IllegalStateException.class, () -> new EncryptedReceiptJournal(file, key));
        corrupted = valid.clone();
        corrupted[0] ^= 1;
        Files.write(file, corrupted);
        assertThrows(IllegalStateException.class, () -> new EncryptedReceiptJournal(file, key));
        Files.write(file, Arrays.copyOf(valid, 10));
        assertThrows(IllegalStateException.class, () -> new EncryptedReceiptJournal(file, key));
        Files.write(file, new byte[256 * 1024 + 1]);
        assertThrows(IllegalStateException.class, () -> new EncryptedReceiptJournal(file, key));
        Files.write(file, valid);
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        assertThrows(IllegalStateException.class,
                () -> new EncryptedReceiptJournal(file, generator.generateKey()));
    }

    @Test
    void authenticatedInvalidCountLengthDuplicateAndTrailingDataFailClosed() throws Exception {
        new EncryptedReceiptJournal(file, key).put(entry("a"));
        byte[] plain = plaintext();
        assertTrue(plain.length > 4);
        byte[] count = plain.clone();
        ByteBuffer.wrap(count).putInt(65);
        corruptReopen(count);
        byte[] length = plain.clone();
        length[4] = 0x7f;
        length[5] = (byte) 0xff;
        corruptReopen(length);
        var duplicated = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(duplicated)) {
            out.writeInt(2);
            out.write(plain, 4, plain.length - 4);
            out.write(plain, 4, plain.length - 4);
        }
        corruptReopen(duplicated.toByteArray());
        corruptReopen(Arrays.copyOf(plain, plain.length + 1));
    }

    @Test
    void failedEncodingAndStaleWriterCannotDestroyGoodSnapshot() throws Exception {
        EncryptedReceiptJournal journal = new EncryptedReceiptJournal(file, key);
        Entry entry = entry("a");
        journal.put(entry);
        byte[] original = Files.readAllBytes(file);
        assertThrows(IllegalStateException.class, () -> journal.put(entry("🎵".repeat(100))));
        assertArrayEquals(original, Files.readAllBytes(file));
        EncryptedReceiptJournal stale = new EncryptedReceiptJournal(file, key);
        journal.put(new Entry(entry.record(), DeliveryStatus.CONFIRMED, 1,
                false, TIME.plusSeconds(10)));
        byte[] updated = Files.readAllBytes(file);
        assertThrows(IllegalStateException.class, () -> stale.put(entry));
        assertArrayEquals(updated, Files.readAllBytes(file));
    }

    @Test
    void immutableOperationAndUnexpectedRemovalFailClosed() throws Exception {
        EncryptedReceiptJournal journal = new EncryptedReceiptJournal(file, key);
        Entry entry = entry("a");
        journal.put(entry);
        assertThrows(IllegalStateException.class, () -> journal.put(entry("different")));
        Files.delete(file);
        assertThrows(IllegalStateException.class, () -> journal.put(entry));
        assertFalse(Files.exists(file));
    }

    @Test
    void uncertainEvidencePersistsAcrossReopen() {
        EncryptedReceiptJournal journal = new EncryptedReceiptJournal(file, key);
        Entry entry = entry("a");
        journal.put(entry);
        EncryptedReceiptJournal reopened = new EncryptedReceiptJournal(file, key);
        assertEquals(entry, reopened.get(entry.record().target().operationId()));
        assertTrue(reopened.get(entry.record().target().operationId()).uncertain());
        assertEquals(1, reopened.get(entry.record().target().operationId()).attempts());
    }

    @Test
    void directoryOrSymlinkSubstitutionRefused() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> new EncryptedReceiptJournal(directory, key));
        EncryptedReceiptJournal journal = new EncryptedReceiptJournal(file, key);
        Entry entry = entry("a");
        journal.put(entry);
        Path previous = directory.resolve("previous.aes");
        Files.move(file, previous);
        Files.createDirectory(file);
        assertThrows(IllegalStateException.class, () -> journal.put(entry));
        assertTrue(Files.exists(previous));
    }

    @Test
    void symlinkParentAndFileRefusedOnPosix() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path privateDir = directory.resolve("private");
        Files.createDirectory(privateDir);
        Path linked = directory.resolve("linked");
        Files.createSymbolicLink(linked, privateDir);
        assertThrows(IllegalStateException.class,
                () -> new EncryptedReceiptJournal(linked.resolve("x.aes"), key));
        EncryptedReceiptJournal journal = new EncryptedReceiptJournal(file, key);
        Entry entry = entry("a");
        journal.put(entry);
        Path previous = directory.resolve("previous.aes");
        Files.move(file, previous);
        Files.createSymbolicLink(file, previous);
        assertThrows(IllegalStateException.class, () -> journal.put(entry));
    }

    @Test
    void generatedGcmSnapshotNoncesDifferAcrossRepeatedWrites() throws Exception {
        EncryptedReceiptJournal journal = new EncryptedReceiptJournal(file, key);
        Entry row = entry("a");
        Set<String> nonces = new java.util.HashSet<>();
        for (int i = 0; i < 128; i++) {
            journal.put(row);
            byte[] snapshot = Files.readAllBytes(file);
            String nonce = java.util.HexFormat.of().formatHex(snapshot, 4, 16);
            assertTrue(nonces.add(nonce), "snapshot must use a fresh 96-bit nonce");
        }
        assertEquals(128, nonces.size());
    }

    @Test
    void worldReadableParentRefusedOnPosix() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(directory);
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OTHERS_READ));
            assertThrows(IllegalStateException.class,
                    () -> new EncryptedReceiptJournal(file, key));
        } finally {
            Files.setPosixFilePermissions(directory, original);
        }
    }
}
