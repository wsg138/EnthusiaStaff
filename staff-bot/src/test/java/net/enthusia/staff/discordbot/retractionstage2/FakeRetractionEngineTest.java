package net.enthusia.staff.discordbot.retractionstage2;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;
import static net.enthusia.staff.discordbot.retractionstage2.FakeRetractionEngine.*;
import static org.junit.jupiter.api.Assertions.*;

import net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FakeRetractionEngineTest {
    private static final Instant START = Instant.parse("2026-10-15T00:00:00Z");
    private static final Instant NOW = START.plusSeconds(30);
    private static final Destination DISCORD = new Destination(Surface.DISCORD,
            "1234567890123456789", "1234567890123456788");
    private static final Destination PAPER = new Destination(Surface.PAPER_SIGNED, "smp", "global");
    private static final String MESSAGE = "1234567890123456787";

    @TempDir Path directory;
    private SecretKey key;
    private Path file;
    private FakeGateway discord;
    private FakeGateway paper;
    private AtomicReference<Authorization> authorization;

    private static final class FakeGateway implements FakeTransport {
        DeliveryStatus next = DeliveryStatus.CONFIRMED;
        boolean crashAfterCommit;
        int sends;
        int probes;
        final Set<String> actuallyDeleted = new HashSet<>();

        @Override
        public DeliveryStatus deleteSynthetic(ReceiptRecord record) {
            sends++;
            if (crashAfterCommit) {
                actuallyDeleted.add(record.target().operationId());
                throw new IllegalStateException("synthetic process crash after commit");
            }
            if (next == DeliveryStatus.CONFIRMED) {
                actuallyDeleted.add(record.target().operationId());
            }
            return next;
        }

        @Override
        public DeliveryStatus reconcileSynthetic(ReceiptRecord record) {
            probes++;
            return actuallyDeleted.contains(record.target().operationId())
                    ? DeliveryStatus.CONFIRMED : DeliveryStatus.RETRYABLE;
        }
    }

    @BeforeEach
    void setup() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        key = generator.generateKey();
        file = directory.resolve("test-only.aes");
        discord = new FakeGateway();
        paper = new FakeGateway();
        authorization = new AtomicReference<>();
    }

    private static SourceEvent source(String eventId, String server, String profile,
                                      String sender, long seconds) {
        return new SourceEvent(eventId, "minecraft", server, profile, "global",
                null, sender, START.plusSeconds(seconds));
    }

    private static ReceiptRecord evidence(Destination dest, String handle,
                                          String signature, Instant published) {
        SourceEvent original = source("original", "smp", "minecraft_public", "player-a", 0);
        SourceEvent current = source("later", "smp", "minecraft_public", "player-a", 2);
        PublicationReceipt publication = new PublicationReceipt(original.eventId(),
                original.platform(), original.serverScope(), original.channelProfile(),
                original.channelId(), original.conversationId(), original.senderId(),
                dest, handle, signature, true);
        Plan plan = ExactRetractionPlanner.plan(current, original, "BLOCK",
                List.of(original.eventId()), List.of(publication), Set.of(dest));
        return new ReceiptRecord("case-01", 7, current, original, publication,
                plan.targets().getFirst(), published, NOW);
    }

    private static ReceiptRecord discordEvidence() {
        return evidence(DISCORD, MESSAGE, null, START.plusSeconds(3));
    }

    private static ReceiptRecord paperEvidence() {
        return evidence(PAPER, "actual-signed-postsend-handle", "real-signature", START.plusSeconds(3));
    }

    private static Authorization permit(ReceiptRecord r) {
        return new Authorization(r.caseId(), r.revision(), r.current().eventId(),
                r.original().eventId(), r.target().operationId(), true, true,
                true, true, true, true, true);
    }

    private FakeRetractionEngine engine(Instant now, boolean enabled) {
        return new FakeRetractionEngine(new EncryptedReceiptJournal(file, key), discord,
                paper, r -> authorization.get(), Clock.fixed(now, ZoneOffset.UTC), enabled);
    }

    private String admitted(FakeRetractionEngine engine, ReceiptRecord receipt) {
        authorization.set(permit(receipt));
        assertTrue(engine.admit(receipt));
        return receipt.target().operationId();
    }

    @Test
    void defaultOffCannotAdmitOrDispatchAndDoesNotWrite() {
        FakeRetractionEngine disabled = engine(NOW, false);
        assertThrows(IllegalStateException.class, () -> disabled.admit(discordEvidence()));
        assertThrows(IllegalStateException.class, () -> disabled.tick("unknown"));
        assertFalse(Files.exists(file));
    }

    @Test
    void confirmedIsExactDurableAndDuplicateAdmissionNeverDeletesTwice() {
        ReceiptRecord proof = discordEvidence();
        FakeRetractionEngine worker = engine(NOW, true);
        String id = admitted(worker, proof);
        assertEquals(DeliveryStatus.CONFIRMED, worker.tick(id));
        assertFalse(worker.admit(proof));
        assertEquals(DeliveryStatus.CONFIRMED, engine(NOW.plusSeconds(10), true).tick(id));
        assertEquals(1, discord.sends);
        assertEquals(1, discord.actuallyDeleted.size());
        assertEquals(1, worker.inspect(id).attempts());
    }

    @Test
    void transientFailureRetriesAtDueTimeAndCapsAttempts() {
        discord.next = DeliveryStatus.RETRYABLE;
        String id = admitted(engine(NOW, true), discordEvidence());
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW, true).tick(id));
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW.plusSeconds(4), true).tick(id));
        assertEquals(1, discord.sends);
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW.plusSeconds(5), true).tick(id));
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW.plusSeconds(10), true).tick(id));
        assertEquals(DeliveryStatus.FAILED, engine(NOW.plusSeconds(15), true).tick(id));
        assertEquals(3, discord.sends);
        assertEquals(3, engine(NOW.plusSeconds(15), true).inspect(id).attempts());
    }

    @Test
    void transientThenConfirmedOnReopenNeverSendsEarly() {
        discord.next = DeliveryStatus.RETRYABLE;
        String id = admitted(engine(NOW, true), discordEvidence());
        engine(NOW, true).tick(id);
        discord.next = DeliveryStatus.CONFIRMED;
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW.plusSeconds(4), true).tick(id));
        assertEquals(DeliveryStatus.CONFIRMED, engine(NOW.plusSeconds(5), true).tick(id));
        assertEquals(2, discord.sends);
    }

    @Test
    void processCrashAfterFakeCommitRecoversByProbeNotSecondDelete() {
        discord.crashAfterCommit = true;
        String id = admitted(engine(NOW, true), discordEvidence());
        assertThrows(IllegalStateException.class, () -> engine(NOW, true).tick(id));
        assertEquals(1, discord.sends);
        assertTrue(engine(NOW, true).inspect(id).uncertain());
        assertEquals(DeliveryStatus.CONFIRMED, engine(NOW.plusSeconds(5), true).tick(id));
        assertEquals(1, discord.sends);
        assertEquals(1, discord.probes);
    }

    @Test
    void unresolvedCrashOnlyProbesAndEventuallyExpires() {
        discord.crashAfterCommit = true;
        String id = admitted(engine(NOW, true), discordEvidence());
        assertThrows(IllegalStateException.class, () -> engine(NOW, true).tick(id));
        discord.actuallyDeleted.clear();
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW.plusSeconds(5), true).tick(id));
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW.plusSeconds(10), true).tick(id));
        assertEquals(1, discord.sends);
        assertEquals(2, discord.probes);
        assertEquals(DeliveryStatus.EXPIRED,
                engine(NOW.plusSeconds(3600), true).tick(id));
        assertEquals(DeliveryStatus.EXPIRED,
                engine(NOW.plusSeconds(3700), true).tick(id));
    }

    @Test
    void independentlyChangedCaseRevisionBlockOrClosedStateStopsDispatch() {
        ReceiptRecord r = discordEvidence();
        String id = admitted(engine(NOW, true), r);
        authorization.set(new Authorization(r.caseId(), 8, r.current().eventId(),
                r.original().eventId(), id, true, true, true, true, true, true, true));
        assertEquals(DeliveryStatus.UNSUPPORTED, engine(NOW, true).tick(id));
        assertEquals(0, discord.sends);
        ReceiptRecord p = paperEvidence();
        String pid = admitted(engine(NOW, true), p);
        authorization.set(new Authorization(p.caseId(), p.revision(), p.current().eventId(),
                p.original().eventId(), pid, false, false, true, true, true, true, true));
        assertEquals(DeliveryStatus.UNSUPPORTED, engine(NOW, true).tick(pid));
        assertEquals(0, paper.sends);
    }

    @Test
    void mismatchedGuildOwnershipSenderPermissionAndSignatureNeverDispatch() {
        ReceiptRecord r = discordEvidence();
        String id = admitted(engine(NOW, true), r);
        authorization.set(new Authorization(r.caseId(), r.revision(), r.current().eventId(),
                r.original().eventId(), id, true, true, true, false, true, true, true));
        assertEquals(DeliveryStatus.UNSUPPORTED, engine(NOW, true).tick(id));
        assertEquals(0, discord.sends);
        ReceiptRecord p = paperEvidence();
        String pid = admitted(engine(NOW, true), p);
        authorization.set(new Authorization(p.caseId(), p.revision(), p.current().eventId(),
                p.original().eventId(), pid, true, true, true, true, true, true, false));
        assertEquals(DeliveryStatus.UNSUPPORTED, engine(NOW, true).tick(pid));
        assertEquals(0, paper.sends);
    }

    @Test
    void signedPaperFakeCanConfirmOnlyWithIndependentCapability() {
        ReceiptRecord p = paperEvidence();
        String id = admitted(engine(NOW, true), p);
        assertEquals(DeliveryStatus.CONFIRMED, engine(NOW, true).tick(id));
        assertEquals(0, discord.sends);
        assertEquals(1, paper.sends);
    }

    @Test
    void latePublicationAfterSourceFinalIsValidOnlyWithActualPostSendProof() {
        ReceiptRecord r = evidence(DISCORD, MESSAGE, null, START.plusSeconds(20));
        String id = admitted(engine(NOW, true), r);
        assertEquals(DeliveryStatus.CONFIRMED, engine(NOW, true).tick(id));
        PublicationReceipt forged = new PublicationReceipt(r.original().eventId(),
                r.original().platform(), r.original().serverScope(),
                r.original().channelProfile(), r.original().channelId(),
                r.original().conversationId(), r.original().senderId(),
                r.publication().destination(), MESSAGE, null, false);
        ReceiptRecord noCallback = new ReceiptRecord(r.caseId(), r.revision(),
                r.current(), r.original(), forged, r.target(), r.publishedAt(), r.receivedAt());
        assertThrows(IllegalArgumentException.class, () -> engine(NOW, true).admit(noCallback));
    }

    @Test
    void wrongSenderServerChannelPrivateAndUnsignedNeverAdmit() {
        ReceiptRecord r = discordEvidence();
        SourceEvent otherServer = source("original", "other-server", "minecraft_public", "player-a", 0);
        SourceEvent otherPlayer = source("original", "smp", "minecraft_public", "player-b", 0);
        SourceEvent privateSource = source("original", "smp", "staff_private", "player-a", 0);
        for (SourceEvent forged : List.of(otherServer, otherPlayer, privateSource)) {
            ReceiptRecord bad = new ReceiptRecord(r.caseId(), r.revision(),
                    r.current(), forged, r.publication(), r.target(), r.publishedAt(), r.receivedAt());
            assertThrows(IllegalArgumentException.class, () -> engine(NOW, true).admit(bad));
        }
        ReceiptRecord paperProof = paperEvidence();
        PublicationReceipt noSig = new PublicationReceipt(paperProof.original().eventId(),
                "minecraft", "smp", "minecraft_public", "global", null, "player-a",
                PAPER, "unsigned", null, true);
        ReceiptRecord unsigned = new ReceiptRecord(paperProof.caseId(), paperProof.revision(),
                paperProof.current(), paperProof.original(), noSig, paperProof.target(),
                paperProof.publishedAt(), paperProof.receivedAt());
        assertThrows(IllegalArgumentException.class, () -> engine(NOW, true).admit(unsigned));
    }

    @Test
    void publicationTimestampCannotPredateOriginOrClaimFuture() {
        ReceiptRecord r = discordEvidence();
        ReceiptRecord before = new ReceiptRecord(r.caseId(), r.revision(), r.current(),
                r.original(), r.publication(), r.target(), START.minusSeconds(1), NOW);
        ReceiptRecord future = new ReceiptRecord(r.caseId(), r.revision(), r.current(),
                r.original(), r.publication(), r.target(), NOW.plusSeconds(1), NOW);
        assertThrows(IllegalArgumentException.class, () -> engine(NOW, true).admit(before));
        assertThrows(IllegalArgumentException.class, () -> engine(NOW, true).admit(future));
    }

    @Test
    void tamperedEncryptedJournalAndWrongKeyFailClosed() throws Exception {
        String id = admitted(engine(NOW, true), discordEvidence());
        assertNotNull(id);
        byte[] ciphertext = Files.readAllBytes(file);
        assertFalse(new String(ciphertext, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("player-a"));
        byte[] altered = ciphertext.clone();
        altered[altered.length - 1] ^= 1;
        Files.write(file, altered);
        assertThrows(IllegalStateException.class, () -> engine(NOW, true));
        Files.write(file, ciphertext); // Wrong-key proof uses intact ciphertext.
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey wrongKey = generator.generateKey();
        assertThrows(IllegalStateException.class,
                () -> new EncryptedReceiptJournal(file, wrongKey));
    }

    @Test
    void collisionAcrossSourceIdentityRequiresNewPlannerOperation() {
        ReceiptRecord r = discordEvidence();
        SourceEvent other = source("original", "different", "minecraft_public", "player-a", 0);
        PublicationReceipt alternate = new PublicationReceipt(other.eventId(), other.platform(),
                other.serverScope(), other.channelProfile(), other.channelId(),
                other.conversationId(), other.senderId(), DISCORD, MESSAGE, null, true);
        SourceEvent current = source("later", "different", "minecraft_public", "player-a", 2);
        Target expected = ExactRetractionPlanner.plan(current, other, "BLOCK",
                List.of(other.eventId()), List.of(alternate), Set.of(DISCORD)).targets().getFirst();
        assertNotEquals(r.target().operationId(), expected.operationId());
    }

    @Test
    void unsupportedAndFailedFakeResponsesPersistIndependentlyBySurface() {
        discord.next = DeliveryStatus.UNSUPPORTED;
        paper.next = DeliveryStatus.FAILED;
        ReceiptRecord d = discordEvidence();
        ReceiptRecord p = paperEvidence();
        FakeRetractionEngine worker = engine(NOW, true);
        admitted(worker, d);
        admitted(worker, p);
        authorization.set(permit(d));
        assertEquals(DeliveryStatus.UNSUPPORTED, worker.tick(d.target().operationId()));
        authorization.set(permit(p));
        assertEquals(DeliveryStatus.FAILED, worker.tick(p.target().operationId()));
        assertEquals(DeliveryStatus.UNSUPPORTED,
                engine(NOW.plusSeconds(6), true).inspect(d.target().operationId()).status());
        assertEquals(DeliveryStatus.FAILED,
                engine(NOW.plusSeconds(6), true).inspect(p.target().operationId()).status());
    }

    @Test
    void changedVerdictBeforeRetryRejectsWithoutSecondSend() {
        discord.next = DeliveryStatus.RETRYABLE;
        ReceiptRecord r = discordEvidence();
        String id = admitted(engine(NOW, true), r);
        assertEquals(DeliveryStatus.RETRYABLE, engine(NOW, true).tick(id));
        authorization.set(new Authorization(r.caseId(), r.revision(), r.current().eventId(),
                r.original().eventId(), id, true, false, true, true, true, true, true));
        assertEquals(DeliveryStatus.UNSUPPORTED, engine(NOW.plusSeconds(5), true).tick(id));
        assertEquals(1, discord.sends);
    }

    @Test
    void intakeCannotRestartExpiredSourceWindow() {
        ReceiptRecord r = discordEvidence();
        assertThrows(IllegalArgumentException.class,
                () -> engine(NOW.plusSeconds(3601), true).admit(r));
        assertFalse(Files.exists(file));
    }

    @Test
    void capacityRefusesExtraReceiptRatherThanDroppingOrReusingOperation() {
        FakeRetractionEngine worker = engine(NOW, true);
        for (int i = 0; i < MAX_RECORDS; i++) {
            ReceiptRecord row = evidence(DISCORD,
                    String.format(java.util.Locale.ROOT, "1234567890123456%03d", i),
                    null, START.plusSeconds(3));
            assertTrue(worker.admit(row));
        }
        assertEquals(MAX_RECORDS, worker.size());
        ReceiptRecord overflow = evidence(DISCORD, "1234567890123456999", null,
                START.plusSeconds(3));
        assertThrows(IllegalStateException.class, () -> worker.admit(overflow));
        assertEquals(MAX_RECORDS, engine(NOW, true).size());
        assertEquals(0, discord.sends);
    }

    @Test
    void unknownCurrentAuthorityFailsClosed() {
        ReceiptRecord r = discordEvidence();
        String id = admitted(engine(NOW, true), r);
        authorization.set(null);
        assertEquals(DeliveryStatus.UNSUPPORTED, engine(NOW, true).tick(id));
        assertEquals(0, discord.sends);
    }
}
