package net.enthusia.staff.discordbot.retraction;

import static net.enthusia.staff.discordbot.retraction.ExactRetractionPlanner.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Synthetic-only isolation tests: no real API, player, Discord or Paper calls. */
class ExactRetractionPlannerTest {
    private static final Instant START = Instant.parse("2026-10-14T12:00:00Z");
    private static final String CHANNEL_GLOBAL = "global";
    private static final String EVENT_FIRST = "first";
    private static final String EVENT_SECOND = "second";
    private static final String PLATFORM_MINECRAFT = "minecraft";
    private static final String PROFILE_PUBLIC = "minecraft_public";
    private static final String SOURCE_SMP = "smp";
    private static final String SENDER_A = "player-a";
    private static final String DISCORD_MESSAGE_ID = "1234567890123456787";
    private static final Destination DISCORD = new Destination(Surface.DISCORD,
            "1234567890123456789", "1234567890123456788");
    private static final Destination PAPER = new Destination(Surface.PAPER_SIGNED,
            SOURCE_SMP, CHANNEL_GLOBAL);
    private static final Destination SYSTEM = new Destination(Surface.MINECRAFT_SYSTEM,
            SOURCE_SMP, CHANNEL_GLOBAL);
    private static final Set<Destination> ALLOWED = Set.of(DISCORD, PAPER, SYSTEM);

    private static SourceEvent prior() {
        return source(EVENT_FIRST, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 0);
    }

    private static SourceEvent later() {
        return source(EVENT_SECOND, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 2);
    }

    private static SourceEvent source(String id, String sender, String scope,
                                      String channel, String profile,
                                      String conversation, long second) {
        return new SourceEvent(id, PLATFORM_MINECRAFT, scope, profile, channel,
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
                discord(first, DISCORD_MESSAGE_ID),
                receipt(first, PAPER, "signed-event-handle", "signed-proof", true),
                receipt(first, SYSTEM, "system-copy", null, true)));
        assertEquals(2, planned.targets().size());
        assertEquals(1, planned.unsupported());
        assertEquals(Refusal.NONE, planned.refusal());
        assertEquals(DISCORD, planned.targets().get(0).destination());
        assertEquals(DISCORD_MESSAGE_ID, planned.targets().get(0).exactRemoteMessageId());
        assertEquals(PAPER, planned.targets().get(1).destination());
        assertEquals("signed-proof", planned.targets().get(1).signedMessageSignature());
    }

    @Test
    void requiresIndependentBlockAndExplicitPriorEventId() {
        SourceEvent first = prior();
        var receipts = List.of(discord(first, DISCORD_MESSAGE_ID));
        for (String action : List.of("ALLOW", "REVIEW", "BLOCKED", "")) {
            assertEquals(Refusal.NOT_BLOCK, ExactRetractionPlanner.plan(later(), first,
                    action, List.of(EVENT_FIRST), receipts, ALLOWED).refusal());
        }
        assertEquals(Refusal.EVIDENCE_NOT_EXPLICIT, ExactRetractionPlanner.plan(
                later(), first, "BLOCK", List.of("other"), receipts, ALLOWED).refusal());
        assertEquals(Refusal.EVIDENCE_NOT_EXPLICIT, ExactRetractionPlanner.plan(
                later(), first, "BLOCK", List.of(), receipts, ALLOWED).refusal());
        assertEquals(Refusal.EVIDENCE_NOT_EXPLICIT, plan(first, first, receipts).refusal());
    }

    @Test
    void rejectsDifferentSenderEvenWhenEventIdAndMessageTextCouldLookIdentical() {
        SourceEvent before = source(EVENT_FIRST, "player-b", SOURCE_SMP, CHANNEL_GLOBAL,
                PROFILE_PUBLIC, null, 0);
        assertEquals(Refusal.SOURCE_CONTEXT_MISMATCH,
                plan(later(), before, List.of(discord(before, DISCORD_MESSAGE_ID))).refusal());
    }

    @Test
    void neverCrossesServersChannelsProfilesConversationsOrPlatforms() {
        List<SourceEvent> foreign = List.of(
                source(EVENT_FIRST, SENDER_A, "other-server", CHANNEL_GLOBAL,
                        PROFILE_PUBLIC, null, 0),
                source(EVENT_FIRST, SENDER_A, SOURCE_SMP, "trade",
                        PROFILE_PUBLIC, null, 0),
                source(EVENT_FIRST, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                        "discord_public", null, 0),
                source(EVENT_FIRST, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                        PROFILE_PUBLIC, "private-thread", 0),
                new SourceEvent(EVENT_FIRST, "discord", SOURCE_SMP, PROFILE_PUBLIC,
                        CHANNEL_GLOBAL, null, SENDER_A, START));
        for (SourceEvent before : foreign) {
            Plan planned = plan(later(), before,
                    List.of(discord(before, DISCORD_MESSAGE_ID)));
            Refusal expected = PLATFORM_MINECRAFT.equals(before.platform())
                    && PROFILE_PUBLIC.equals(before.channelProfile())
                    ? Refusal.SOURCE_CONTEXT_MISMATCH : Refusal.NONPUBLIC_SOURCE;
            assertEquals(expected, planned.refusal());
            assertTrue(planned.targets().isEmpty());
        }
    }

    @Test
    void rejectsPrivateAndStaffSourcesEvenIfModelReturnsBlock() {
        SourceEvent before = source(EVENT_FIRST, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                "staff_private", null, 0);
        SourceEvent current = source(EVENT_SECOND, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                "staff_private", null, 2);
        assertEquals(Refusal.NONPUBLIC_SOURCE, plan(current, before,
                List.of(discord(before, DISCORD_MESSAGE_ID))).refusal());
    }

    @Test
    void rejectsArbitraryOriginPlatformClaimingPublicMinecraftProfile() {
        SourceEvent before = new SourceEvent(EVENT_FIRST, "staff_private_transport", SOURCE_SMP,
                PROFILE_PUBLIC, CHANNEL_GLOBAL, null, SENDER_A, START);
        SourceEvent current = new SourceEvent(EVENT_SECOND, "staff_private_transport", SOURCE_SMP,
                PROFILE_PUBLIC, CHANNEL_GLOBAL, null, SENDER_A, START.plusSeconds(2));
        assertEquals(Refusal.NONPUBLIC_SOURCE,
                plan(current, before, List.of(discord(before, DISCORD_MESSAGE_ID))).refusal());
    }

    @Test
    void requiresBoundedForwardOnlyFifteenSecondWindow() {
        SourceEvent first = prior();
        List<PublicationReceipt> receipts = List.of(discord(first, DISCORD_MESSAGE_ID));
        assertEquals(1, plan(source(EVENT_SECOND, SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                PROFILE_PUBLIC, null, 15), first, receipts).targets().size());
        assertEquals(Refusal.OUTSIDE_WINDOW, plan(source(EVENT_SECOND, SENDER_A, SOURCE_SMP,
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 16), first, receipts).refusal());
        assertEquals(Refusal.OUTSIDE_WINDOW, plan(source(EVENT_SECOND, SENDER_A, SOURCE_SMP,
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, -1), first, receipts).refusal());
    }

    @Test
    void neverUsesPreSendGuessesOrUnapprovedDestination() {
        SourceEvent first = prior();
        Destination otherGuild = new Destination(Surface.DISCORD,
                "1234567890123456700", DISCORD.channelId());
        Plan planned = plan(later(), first, List.of(
                receipt(first, DISCORD, DISCORD_MESSAGE_ID, null, false),
                receipt(first, otherGuild, DISCORD_MESSAGE_ID, null, true)));
        assertTrue(planned.targets().isEmpty());
        assertEquals(0, planned.unsupported());
    }

    @Test
    void rejectsReceiptMismatchingCanonicalOwnershipEvenWithExactEventId() {
        SourceEvent first = prior();
        PublicationReceipt matching = discord(first, DISCORD_MESSAGE_ID);
        List<PublicationReceipt> forgedMetadata = List.of(
                new PublicationReceipt(EVENT_FIRST, PLATFORM_MINECRAFT, "different-smp",
                        PROFILE_PUBLIC, CHANNEL_GLOBAL, null, SENDER_A,
                        DISCORD, DISCORD_MESSAGE_ID, null, true),
                new PublicationReceipt(EVENT_FIRST, PLATFORM_MINECRAFT, SOURCE_SMP,
                        PROFILE_PUBLIC, CHANNEL_GLOBAL, null, "other-player",
                        DISCORD, DISCORD_MESSAGE_ID, null, true),
                new PublicationReceipt(EVENT_FIRST, PLATFORM_MINECRAFT, SOURCE_SMP,
                        PROFILE_PUBLIC, "other-channel", null, SENDER_A,
                        DISCORD, DISCORD_MESSAGE_ID, null, true),
                new PublicationReceipt("other-event", PLATFORM_MINECRAFT, SOURCE_SMP,
                        PROFILE_PUBLIC, CHANNEL_GLOBAL, null, SENDER_A,
                        DISCORD, DISCORD_MESSAGE_ID, null, true));
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
                discord(first, DISCORD_MESSAGE_ID)));
        assertEquals(2, planned.unsupported());
        assertEquals(1, planned.targets().size());
    }

    @Test
    void exactReceiptDeduplicatesWithStableOperationAcrossModelReevaluation() {
        SourceEvent first = prior();
        PublicationReceipt publication = discord(first, DISCORD_MESSAGE_ID);
        List<PublicationReceipt> duplicated = List.of(publication, publication, publication);
        Plan firstAttempt = plan(later(), first, duplicated);
        Plan nextContext = plan(source("third", SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                PROFILE_PUBLIC, null, 5), first, duplicated);
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
        SourceEvent another = source("other-first", SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                PROFILE_PUBLIC, null, 0);
        String firstOperation = plan(later(), first,
                List.of(discord(first, DISCORD_MESSAGE_ID)))
                .targets().getFirst().operationId();
        String otherOperation = plan(later(), another,
                List.of(discord(another, DISCORD_MESSAGE_ID)))
                .targets().getFirst().operationId();
        assertNotEquals(firstOperation, otherOperation);
    }

    @Test
    void sourceOrReceiptMayNotContainBlankIdentityFields() {
        assertThrows(IllegalArgumentException.class,
                () -> source("", SENDER_A, SOURCE_SMP, CHANNEL_GLOBAL,
                        PROFILE_PUBLIC, null, 0));
        assertThrows(IllegalArgumentException.class,
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
    void operationIdsIncludeFullCanonicalSourceNotJustEventId() {
        SourceEvent first = prior();
        SourceEvent differentServer = source(EVENT_FIRST, SENDER_A, "other-smp",
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 0);
        SourceEvent differentSender = source(EVENT_FIRST, "player-b", SOURCE_SMP,
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 0);
        SourceEvent differentTimestamp = source(EVENT_FIRST, SENDER_A, SOURCE_SMP,
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 1);
        String original = plan(later(), first,
                List.of(discord(first, DISCORD_MESSAGE_ID))).targets()
                .getFirst().operationId();
        String otherServer = plan(source(EVENT_SECOND, SENDER_A, "other-smp",
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 2), differentServer,
                List.of(discord(differentServer, DISCORD_MESSAGE_ID))).targets()
                .getFirst().operationId();
        String otherSender = plan(source(EVENT_SECOND, "player-b", SOURCE_SMP,
                CHANNEL_GLOBAL, PROFILE_PUBLIC, null, 2), differentSender,
                List.of(discord(differentSender, DISCORD_MESSAGE_ID))).targets()
                .getFirst().operationId();
        String laterTimestamp = plan(later(), differentTimestamp,
                List.of(discord(differentTimestamp, DISCORD_MESSAGE_ID))).targets()
                .getFirst().operationId();
        assertNotEquals(original, otherServer);
        assertNotEquals(original, otherSender);
        assertNotEquals(original, laterTimestamp);
    }

    @Test
    void boundedInputsFailClosedBeforeProcessingUntrustedReceipts() {
        SourceEvent first = prior();
        List<PublicationReceipt> tooMany = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            tooMany.add(discord(first, DISCORD_MESSAGE_ID));
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
        receipts.add(discord(first, DISCORD_MESSAGE_ID));
        Plan planned = plan(later(), first, receipts);
        receipts.clear();
        assertEquals(1, planned.targets().size());
        assertThrows(UnsupportedOperationException.class,
                () -> planned.targets().clear());
    }
}
