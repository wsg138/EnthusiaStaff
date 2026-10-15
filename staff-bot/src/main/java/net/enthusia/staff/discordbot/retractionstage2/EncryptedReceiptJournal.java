package net.enthusia.staff.discordbot.retractionstage2;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;
import static net.enthusia.staff.discordbot.retractionstage2.FakeRetractionEngine.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Bounded AES-GCM v1 file snapshot with atomic rename and fsynced ciphertext.
 * The key is injected, never stored here. Any unreadable/corrupt file fails closed.
 * Standalone synthetic proof only: no cross-process writer locking or live adapter.
 */
public final class EncryptedReceiptJournal {
    private static final byte[] MAGIC = {'R', 'T', 'R', '2'};
    private static final int NONCE_BYTES = 12;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_TEXT_BYTES = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private final Path file;
    private final SecretKey key;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public EncryptedReceiptJournal(Path file, SecretKey key) {
        this.file = file.toAbsolutePath().normalize();
        this.key = java.util.Objects.requireNonNull(key);
        if (!"AES".equals(key.getAlgorithm()) || key.getEncoded() == null
                || key.getEncoded().length != 32) {
            throw new IllegalArgumentException("external AES-256 key required");
        }
        if (Files.isSymbolicLink(this.file) || Files.isSymbolicLink(this.file.getParent())) {
            throw new IllegalArgumentException("symlink journal path forbidden");
        }
        if (Files.exists(this.file, LinkOption.NOFOLLOW_LINKS)) {
            readSnapshot();
        }
    }

    public synchronized Entry get(String operationId) {
        return entries.get(operationId);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void put(Entry entry) {
        String id = entry.record().target().operationId();
        if (!entries.containsKey(id) && entries.size() >= MAX_RECORDS) {
            throw new IllegalStateException("receipt capacity exceeded");
        }
        Map<String, Entry> updated = new LinkedHashMap<>(entries);
        updated.put(id, entry);
        writeSnapshot(updated);
        entries.clear();
        entries.putAll(updated);
    }

    private void readSnapshot() {
        try {
            long fileSize = Files.size(file);
            if (fileSize < MAGIC.length + NONCE_BYTES + 16 || fileSize > MAX_FILE_BYTES) {
                throw new IllegalStateException("invalid receipt journal size");
            }
            byte[] raw = Files.readAllBytes(file);
            if (raw.length != fileSize) {
                throw new IllegalStateException("invalid receipt journal size");
            }
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] header = new byte[MAGIC.length];
            buffer.get(header);
            if (!java.util.Arrays.equals(MAGIC, header)) {
                throw new IllegalStateException("invalid receipt journal version");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            decode(decrypt(ciphertext, nonce));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("receipt journal cannot be safely opened", e);
        }
    }

    private void decode(byte[] plain) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(plain))) {
            int count = input.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new IOException("invalid entry count");
            }
            for (int i = 0; i < count; i++) {
                Entry entry = readEntry(input);
                String id = entry.record().target().operationId();
                if (entries.putIfAbsent(id, entry) != null) {
                    throw new IOException("duplicate operation");
                }
            }
            if (input.available() != 0) {
                throw new IOException("trailing journal data");
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("invalid receipt journal fields", e);
        }
    }

    private void writeSnapshot(Map<String, Entry> rows) {
        try {
            byte[] encrypted = encrypt(encode(rows));
            if (encrypted.length > MAX_FILE_BYTES) {
                throw new IllegalStateException("receipt ciphertext exceeds bounded size");
            }
            Path parent = file.getParent();
            if (Files.isSymbolicLink(parent)) {
                throw new IllegalStateException("symlink journal parent forbidden");
            }
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "retraction-", ".tmp");
            try {
                protectPosix(temp);
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    ByteBuffer pending = ByteBuffer.wrap(encrypted);
                    while (pending.hasRemaining()) {
                        channel.write(pending);
                    }
                    channel.force(true);
                }
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("cannot durably persist receipt snapshot", e);
        }
    }

    private static void protectPosix(Path temp) throws IOException {
        if (Files.getFileStore(temp).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(temp, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        // Windows ACL and parent directory durability require separate platform acceptance.
    }

    private byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
        byte[] ciphertext = cipher.doFinal(plain);
        return ByteBuffer.allocate(MAGIC.length + NONCE_BYTES + ciphertext.length)
                .put(MAGIC).put(nonce).put(ciphertext).array();
    }

    private byte[] decrypt(byte[] ciphertext, byte[] nonce) throws GeneralSecurityException {
        return cipher(Cipher.DECRYPT_MODE, nonce).doFinal(ciphertext);
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(MAGIC);
        return cipher;
    }

    private static byte[] encode(Map<String, Entry> rows) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            output.writeInt(rows.size());
            for (Entry row : rows.values()) {
                writeEntry(output, row);
            }
        }
        return buffer.toByteArray();
    }

    private static void writeEntry(DataOutputStream out, Entry row) throws IOException {
        ReceiptRecord r = row.record();
        put(out, r.caseId());
        out.writeLong(r.revision());
        writeSource(out, r.current());
        writeSource(out, r.original());
        writeReceipt(out, r.publication());
        put(out, r.target().operationId());
        put(out, r.publishedAt().toString());
        put(out, r.receivedAt().toString());
        out.writeInt(row.status().ordinal());
        out.writeInt(row.attempts());
        out.writeBoolean(row.uncertain());
        put(out, row.nextAttempt().toString());
    }

    private static Entry readEntry(DataInputStream in) throws IOException {
        String caseId = get(in);
        long revision = in.readLong();
        SourceEvent current = readSource(in);
        SourceEvent original = readSource(in);
        PublicationReceipt receipt = readReceipt(in);
        String operationId = get(in);
        Plan plan = plan(current, original, "BLOCK", List.of(original.eventId()),
                List.of(receipt), Set.of(receipt.destination()));
        if (plan.targets().size() != 1
                || !plan.targets().getFirst().operationId().equals(operationId)) {
            throw new IOException("journal planner provenance mismatch");
        }
        ReceiptRecord r = new ReceiptRecord(caseId, revision, current, original,
                receipt, plan.targets().getFirst(), Instant.parse(get(in)),
                Instant.parse(get(in)));
        int status = in.readInt();
        if (status < 0 || status >= DeliveryStatus.values().length) {
            throw new IOException("invalid result status");
        }
        return new Entry(r, DeliveryStatus.values()[status], in.readInt(),
                in.readBoolean(), Instant.parse(get(in)));
    }

    private static void writeSource(DataOutputStream out, SourceEvent s) throws IOException {
        for (String part : List.of(s.eventId(), s.platform(), s.serverScope(),
                s.channelProfile(), s.channelId(), optional(s.conversationId()),
                s.senderId(), s.occurredAt().toString())) {
            put(out, part);
        }
    }

    private static SourceEvent readSource(DataInputStream in) throws IOException {
        return new SourceEvent(get(in), get(in), get(in), get(in), get(in),
                getOptional(in), get(in), Instant.parse(get(in)));
    }

    private static void writeReceipt(DataOutputStream out, PublicationReceipt r) throws IOException {
        put(out, r.sourceEventId());
        put(out, r.sourcePlatform());
        put(out, r.sourceServerScope());
        put(out, r.sourceChannelProfile());
        put(out, r.sourceChannelId());
        put(out, optional(r.sourceConversationId()));
        put(out, r.sourceSenderId());
        put(out, r.destination().surface().name());
        put(out, r.destination().scopeId());
        put(out, r.destination().channelId());
        put(out, r.remoteMessageId());
        put(out, optional(r.signedMessageSignature()));
        out.writeBoolean(r.actualPostSendReceipt());
    }

    private static PublicationReceipt readReceipt(DataInputStream in) throws IOException {
        String sourceId = get(in);
        String platform = get(in);
        String server = get(in);
        String profile = get(in);
        String channel = get(in);
        String conversation = getOptional(in);
        String sender = get(in);
        Surface surface = Surface.valueOf(get(in));
        Destination dest = new Destination(surface, get(in), get(in));
        return new PublicationReceipt(sourceId, platform, server, profile, channel,
                conversation, sender, dest, get(in), getOptional(in), in.readBoolean());
    }

    private static String optional(String value) {
        return value == null ? "" : value;
    }

    private static String getOptional(DataInputStream in) throws IOException {
        String value = get(in);
        return value.isEmpty() ? null : value;
    }

    private static void put(DataOutputStream out, String value) throws IOException {
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IOException("bounded receipt field too large");
        }
        out.writeUTF(value);
    }

    private static String get(DataInputStream in) throws IOException {
        String value = in.readUTF();
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IOException("receipt field exceeds cap");
        }
        return value;
    }
}
