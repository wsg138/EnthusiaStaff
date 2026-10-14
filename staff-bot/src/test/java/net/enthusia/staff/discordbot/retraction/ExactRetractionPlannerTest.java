package net.enthusia.staff.discordbot.retraction;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Synthetic-only isolation tests: no real API, player, Discord or Paper calls. */
class ExactRetractionPlannerTest {
    private static final Instant START = Instant.parse("2026-10-14T12:00:00Z");
    private static final Destination DISCORD = new Destination(Surface.DISCORD,
            "1234567890123456789", "1234567890123456788");
    private static final Destination PAPER = new Destination(Surface.PAPER_SIGNED,
            "smp", "global");
    private static final Destination SYSTEM = new Destination(Surface.MINECRAFT_SYSTEM,
            "smp", "global");
    private static final Set<Destination> ALLOWED = Set.of(DISCORD, PAPER, SYSTEM);

    private static SourceEvent prior() {
        return source("first", "player-a", "smp", "global", "minecraft_public", null, 0);
    }

    private static SourceEvent later() {
        return source("second", "player-a", "smp", "global", "minecraft_public", null, 2);
    }

    private static SourceEvent source(String id, String sender, String scope,
                                      String channel, String profile,
                                      String conversation, long second) {
        return new SourceEvent(id, "minecraft", scope, profile, channel,
                conversation, sender, START.plusSeconds(second));
    }

    private static PublicationReceipt receipt(SourceEvent source, Destination to, String id,
                                               String signature, boolean postSend) {
        return new PublicationReceipt(source.eventId(), source.platform(),
                source.serverScope(), source.channelProfile(), source.channelId(),
                source.conversationId(), source.senderId(), to, id, signature, postSend);
    }

    private static PublicationReceipt discord(SourceEvent source, String id) {
        return receipt(source, DISCORD, id, null, true);
    }

    private static Plan plan(SourceEvent current, SourceEvent before,
                             List<PublicationReceipt> receipts) {
        return ExactRetractionPlanner.plan(
                current, before, "BLOCK", List.of(before.eventId()), receipts, ALLOWED);
    }

    @Test
    void selectsOnlyExactPostSendDiscordSnowflakeAndSignedPaperHandle() {
        SourceEvent first = prior();
        Plan planned = plan(later(), first, List.of(
                discord(first, "1234567890123456787"),
                receipt(first, PAPER, "signed-event-handle", "signed-proof", true),
                receipt(first, SYSTEM, "system-copy", null, true)));
        assertEquals(2, planned.targets().size());
        assertEquals(1, planned.unsupported());
        assertEquals(Refusal.NONE, planned.refusal());
        assertEquals(DISCORD, planned.targets().get(0).destination());
        assertEquals("1234567890123456787", planned.targets().get(0).exactRemoteMessageId());
        assertEquals(PAPER, planned.targets().get(1).destination());
        assertEquals("signed-proof", planned.targets().get(1).signedMessageSignature());
    }

    @Test
    void requiresIndependentBlockAndExplicitPriorEventId() {
        SourceEvent first = prior();
        var receipts = List.of(discord(first, "1234567890123456787"));
        for (String action : List.of("ALLOW", "REVIEW", "BLOCKED", "")) {
            assertEquals(Refusal.NOT_BLOCK, ExactRetractionPlanner.plan(later(), first,
                    action, List.of("first"), receipts, ALLOWED).refusal());
        }
        assertEquals(Refusal.EVIDENCE_NOT_EXPLICIT, ExactRetractionPlanner.plan(
                later(), first, "BLOCK", List.of("other"), receipts, ALLOWED).refusal());
        assertEquals(Refusal.EVIDENCE_NOT_EXPLICIT, ExactRetractionPlanner.plan(
                later(), first, "BLOCK", List.of(), receipts, ALLOWED).refusal());
        assertEquals(Refusal.EVIDENCE_NOT_EXPLICIT, plan(first, first, receipts).refusal());
    }

    @Test
    void rejectsDifferentSenderEvenWhenEventIdAndMessageTextCouldLookIdentical() {
        SourceEvent before = source("first", "player-b", "smp", "global",
                "minecraft_public", null, 0);
        assertEquals(Refusal.SOURCE_CONTEXT_MISMATCH,
                plan(later(), before, List.of(discord(before, "1234567890123456787"))).refusal());
    }

    @Test
    void neverCrossesServersChannelsProfilesConversationsOrPlatforms() {
        SourceEvent first = prior();
        List<SourceEvent> foreign = List.of(
                source("first", "player-a", "other-server", "global",
                        "minecraft_public", null, 0),
                source("first", "player-a", "smp", "trade",
                        "minecraft_public", null, 0),
                source("first", "player-a", "smp", "global",
                        "discord_public", null, 0),
                source("first", "player-a", "smp", "global",
                        "minecraft_public", "private-thread", 0),
                new SourceEvent("first", "discord", "smp", "minecraft_public",
                        "global", null, "player-a", START));
        for (SourceEvent before : foreign) {
            Plan planned = plan(later(), before,
                    List.of(discord(before, "1234567890123456787")));
            assertEquals(Refusal.SOURCE_CONTEXT_MISMATCH, planned.refusal());
            assertTrue(planned.targets().isEmpty());
        }
    }

    @Test
    void rejectsPrivateAndStaffSourcesEvenIfModelReturnsBlock() {
        SourceEvent before = source("first", "player-a", "smp", "global",
                "staff_private", null, 0);
        SourceEvent current = source("second", "player-a", "smp", "global",
                "staff_private", null, 2);
        assertEquals(Refusal.NONPUBLIC_SOURCE, plan(current, before,
                List.of(discord(before, "1234567890123456787"))).refusal());
    }

    @Test
    void requiresBoundedForwardOnlyFifteenSecondWindow() {
        SourceEvent first = prior();
        List<PublicationReceipt> receipts = List.of(discord(first, "1234567890123456787"));
        assertEquals(1, plan(source("second", "player-a", "smp", "global",
                "minecraft_public", null, 15), first, receipts).targets().size());
        assertEquals(Refusal.OUTSIDE_WINDOW, plan(source("second", "player-a", "smp",
                "global", "minecraft_public", null, 16), first, receipts).refusal());
        assertEquals(Refusal.OUTSIDE_WINDOW, plan(source("second", "player-a", "smp",
                "global", "minecraft_public", null, -1), first, receipts).refusal());
    }

    @Test
    void neverUsesPreSendGuessesOrUnapprovedDestination() {
        SourceEvent first = prior();
        Destination otherGuild = new Destination(Surface.DISCORD,
                "1234567890123456700", DISCORD.channelId());
        Plan planned = plan(later(), first, List.of(
                receipt(first, DISCORD, "1234567890123456787", null, false),
                receipt(first, otherGuild, "1234567890123456787", null, true)));
        assertTrue(planned.targets().isEmpty());
        assertEquals(0, planned.unsupported());
    }

    @Test
    void rejectsReceiptMismatchingCanonicalOwnershipEvenWithExactEventId() {
        SourceEvent first = prior();
        PublicationReceipt matching = discord(first, "1234567890123456787");
        List<PublicationReceipt> forgedMetadata = List.of(
                new PublicationReceipt("first", "minecraft", "different-smp",
                        "minecraft_public", "global", null, "player-a",
                        DISCORD, "1234567890123456787", null, true),
                new PublicationReceipt("first", "minecraft", "smp",
                        "minecraft_public", "global", null, "other-player",
                        DISCORD, "1234567890123456787", null, true),
                new PublicationReceipt("first", "minecraft", "smp",
                        "minecraft_public", "other-channel", null, "player-a",
                        DISCORD, "1234567890123456787", null, true),
                new PublicationReceipt("other-event", "minecraft", "smp",
                        "minecraft_public", "global", null, "player-a",
                        DISCORD, "1234567890123456787", null, true));
        assertTrue(plan(later(), first, forgedMetadata).targets().isEmpty());
        assertEquals(1, plan(later(), first, List.of(matching)).targets().size());
    }

    @Test
    void unsignedAndSystemMinecraftCopiesAreExplicitlyUnsupported() {
        SourceEvent first = prior();
        Plan planned = plan(later(), first, List.of(
                receipt(first, PAPER, "unsigned", null, true),
                receipt(first, SYSTEM, "system", "a-signature-does-not-help", true)));
        assertTrue(planned.targets().isEmpty());
        assertEquals(2, planned.unsupported());
    }

    @Test
    void rejectsInvalidDiscordSnowflakesRatherThanGuessingIds() {
        SourceEvent first = prior();
        Plan planned = plan(later(), first, List.of(
                discord(first, "fake-post-id"),
                discord(first, "000000000000000000"),
                discord(first, "1234567890123456787")));
        assertEquals(2, planned.unsupported());
        assertEquals(1, planned.targets().size());
    }

    @Test
    void exactReceiptDeduplicatesWithStableOperationAcrossModelReevaluation() {
        SourceEvent first = prior();
        PublicationReceipt publication = discord(first, "1234567890123456787");
        List<PublicationReceipt> duplicated = List.of(publication, publication, publication);
        Plan firstAttempt = plan(later(), first, duplicated);
        Plan nextContext = plan(source("third", "player-a", "smp", "global",
                "minecraft_public", null, 5), first, duplicated);
        assertEquals(1, firstAttempt.targets().size());
        assertEquals(1, nextContext.targets().size());
        assertEquals(firstAttempt.targets().getFirst().operationId(),
                nextContext.targets().getFirst().operationId());
        assertEquals(64, firstAttempt.targets().getFirst().operationId().length());
        assertNotEquals(firstAttempt.targets().getFirst().operationId(),
                plan(later(), first, List.of(discord(first, "1234567890123456701")))
                        .targets().getFirst().operationId());
    }

    @Test
    void operationDiffersAcrossDestinationAndSourceEvent() {
        SourceEvent first = prior();
        SourceEvent another = source("other-first", "player-a", "smp", "global",
                "minecraft_public", null, 0);
        String firstOperation = plan(later(), first,
                List.of(discord(first, "1234567890123456787")))
                .targets().getFirst().operationId();
        String otherOperation = plan(later(), another,
                List.of(discord(another, "1234567890123456787")))
                .targets().getFirst().operationId();
        assertNotEquals(firstOperation, otherOperation);
    }

    @Test
    void sourceOrReceiptMayNotContainBlankIdentityFields() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> source("", "player-a", "smp", "global",
                        "minecraft_public", null, 0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> receipt(prior(), PAPER, "", "signed", true));
    }

    @Test
    void signaturesArePartOfExactSignedOperationIdentity() {
        SourceEvent first = prior();
        var original = plan(later(), first, List.of(
                receipt(first, PAPER, "paper-msg", "signature-A", true)));
        var differentSignedMessage = plan(later(), first, List.of(
                receipt(first, PAPER, "paper-msg", "signature-B", true)));
        assertNotEquals(original.targets().getFirst().operationId(),
                differentSignedMessage.targets().getFirst().operationId());
    }

    @Test
    void boundedInputsFailClosedBeforeProcessingUntrustedReceipts() {
        SourceEvent first = prior();
        List<PublicationReceipt> tooMany = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            tooMany.add(discord(first, "1234567890123456787"));
        }
        assertEquals(Refusal.INPUT_CAPACITY_EXCEEDED,
                plan(later(), first, tooMany).refusal());
        assertTrue(plan(later(), first, tooMany).targets().isEmpty());
    }

    @Test
    void statusContractIsExplicitlyFiveWayAndPlanImmutable() {
        assertEquals(Set.of(DeliveryStatus.CONFIRMED, DeliveryStatus.RETRYABLE,
                DeliveryStatus.UNSUPPORTED, DeliveryStatus.EXPIRED,
                DeliveryStatus.FAILED), Set.of(DeliveryStatus.values()));
        SourceEvent first = prior();
        List<PublicationReceipt> receipts = new ArrayList<>();
        receipts.add(discord(first, "1234567890123456787"));
        Plan planned = plan(later(), first, receipts);
        receipts.clear();
        assertEquals(1, planned.targets().size());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> planned.targets().clear());
    }
}
