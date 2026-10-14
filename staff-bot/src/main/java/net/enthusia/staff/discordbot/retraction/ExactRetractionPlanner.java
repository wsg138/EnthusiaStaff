package net.enthusia.staff.discordbot.retraction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, default-inert eligibility check for deleting exact, previously published copies.
 *
 * <p>Inputs MUST be supplied from authenticated canonical decisions and a trusted
 * post-send receipt journal, not raw Discord commands, chat text or model-proposed
 * publication IDs. This class performs no network calls, chat deletion or sanctions.
 * A selected target is only an eligible candidate for a separately authorized,
 * idempotent and durable transport operation.</p>
 */
public final class ExactRetractionPlanner {
    private static final Duration MAX_AGE = Duration.ofSeconds(15);
    private static final int MAX_PLAN_RECORDS = 64;
    private static final String BLOCK_VERDICT = "BLOCK";

    private ExactRetractionPlanner() {}

    public enum Surface {
        DISCORD, PAPER_SIGNED, MINECRAFT_SYSTEM
    }

    /** The transport's result, not a claim that a recipient has forgotten a message. */
    public enum DeliveryStatus {
        CONFIRMED, RETRYABLE, UNSUPPORTED, EXPIRED, FAILED
    }

    public enum Refusal {
        NONE, NOT_BLOCK, EVIDENCE_NOT_EXPLICIT, SOURCE_CONTEXT_MISMATCH,
        OUTSIDE_WINDOW, NONPUBLIC_SOURCE, INPUT_CAPACITY_EXCEEDED
    }

    public record SourceEvent(
            String eventId,
            String platform,
            String serverScope,
            String channelProfile,
            String channelId,
            String conversationId,
            String senderId,
            Instant occurredAt) {
        public SourceEvent {
            eventId = nonblank(eventId, "event ID");
            platform = nonblank(platform, "platform");
            serverScope = nonblank(serverScope, "source scope");
            channelProfile = nonblank(channelProfile, "channel profile");
            channelId = nonblank(channelId, "source channel");
            senderId = nonblank(senderId, "sender");
            Objects.requireNonNull(occurredAt, "occurredAt");
            conversationId = normalizeOptional(conversationId);
        }
    }

    /** Destination is configured, not inferred from a sender, text or message name. */
    public record Destination(Surface surface, String scopeId, String channelId) {
        public Destination {
            Objects.requireNonNull(surface, "surface");
            scopeId = nonblank(scopeId, "destination scope");
            channelId = nonblank(channelId, "destination channel");
        }
    }

    /**
     * Only a post-send, independently verified publication can produce this record.
     * The source identity tuple is repeated deliberately so that an event-ID collision
     * cannot authorize deletion from another source/channel/server/sender.
     * No raw message text is stored.
     */
    public record PublicationReceipt(
            String sourceEventId,
            String sourcePlatform,
            String sourceServerScope,
            String sourceChannelProfile,
            String sourceChannelId,
            String sourceConversationId,
            String sourceSenderId,
            Destination destination,
            String remoteMessageId,
            String signedMessageSignature,
            boolean actualPostSendReceipt) {
        public PublicationReceipt {
            sourceEventId = nonblank(sourceEventId, "source event ID");
            sourcePlatform = nonblank(sourcePlatform, "source platform");
            sourceServerScope = nonblank(sourceServerScope, "source scope");
            sourceChannelProfile = nonblank(sourceChannelProfile, "source profile");
            sourceChannelId = nonblank(sourceChannelId, "source channel");
            sourceConversationId = normalizeOptional(sourceConversationId);
            sourceSenderId = nonblank(sourceSenderId, "source sender");
            Objects.requireNonNull(destination, "destination");
            remoteMessageId = nonblank(remoteMessageId, "remote ID");
            signedMessageSignature = normalizeOptional(signedMessageSignature);
        }
    }

    /** Exact publication handle; must be reconfirmed by the transport adapter. */
    public record Target(
            String operationId,
            String originalEventId,
            Destination destination,
            String exactRemoteMessageId,
            String signedMessageSignature) {}

    /** Unsupported surfaces are counted, never silently counted as successes. */
    public record Plan(List<Target> targets, int unsupported, Refusal refusal) {
        public Plan {
            targets = List.copyOf(targets);
        }
    }

    /**
     * Proposes retractions only when a later independent BLOCK explicitly cites the
     * earlier source event and both canonical events share the original conversation.
     * A trusted allowlist binds each publication destination to this source.
     */
    public static Plan plan(
            SourceEvent current,
            SourceEvent previous,
            String currentVerdict,
            Collection<String> explicitRelatedEventIds,
            Collection<PublicationReceipt> recordedPublications,
            Set<Destination> allowedDestinations) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(explicitRelatedEventIds, "related event IDs");
        Objects.requireNonNull(recordedPublications, "publication receipts");
        Objects.requireNonNull(allowedDestinations, "destinations");
        Refusal refusal = preflight(current, previous, currentVerdict,
                explicitRelatedEventIds, recordedPublications, allowedDestinations);
        if (refusal != Refusal.NONE) {
            return refused(refusal);
        }

        List<Target> selected = new ArrayList<>();
        Set<String> dedup = new HashSet<>();
        int unsupported = 0;
        for (PublicationReceipt receipt : recordedPublications) {
            if (receipt == null || !matchesCanonical(receipt, previous)
                    || !receipt.actualPostSendReceipt()
                    || !allowedDestinations.contains(receipt.destination())) {
                continue;
            }
            Destination destination = receipt.destination();
            if (!supportedPublication(receipt)) {
                unsupported++;
                continue;
            }
            String operationId = operationId(previous, receipt);
            if (dedup.add(operationId)) {
                selected.add(new Target(operationId, previous.eventId(), destination,
                        receipt.remoteMessageId(), receipt.signedMessageSignature()));
            }
        }
        return new Plan(selected, unsupported, Refusal.NONE);
    }

    private static Refusal preflight(
            SourceEvent current, SourceEvent previous, String verdict,
            Collection<String> related, Collection<PublicationReceipt> publications,
            Set<Destination> allowed) {
        if (related.size() > MAX_PLAN_RECORDS || publications.size() > MAX_PLAN_RECORDS
                || allowed.size() > MAX_PLAN_RECORDS) {
            return Refusal.INPUT_CAPACITY_EXCEEDED;
        }
        if (!BLOCK_VERDICT.equals(verdict)) {
            return Refusal.NOT_BLOCK;
        }
        if (current.eventId().equals(previous.eventId())
                || !related.contains(previous.eventId())) {
            return Refusal.EVIDENCE_NOT_EXPLICIT;
        }
        if (!isPublicOrigin(current) || !isPublicOrigin(previous)) {
            return Refusal.NONPUBLIC_SOURCE;
        }
        if (!sameConversation(current, previous)) {
            return Refusal.SOURCE_CONTEXT_MISMATCH;
        }
        Duration age = Duration.between(previous.occurredAt(), current.occurredAt());
        return age.isNegative() || age.compareTo(MAX_AGE) > 0
                ? Refusal.OUTSIDE_WINDOW : Refusal.NONE;
    }

    private static boolean supportedPublication(PublicationReceipt receipt) {
        Destination destination = receipt.destination();
        return switch (destination.surface()) {
            case MINECRAFT_SYSTEM -> false;
            case PAPER_SIGNED -> receipt.signedMessageSignature() != null;
            case DISCORD -> validSnowflake(destination.scopeId())
                    && validSnowflake(destination.channelId())
                    && validSnowflake(receipt.remoteMessageId());
        };
    }

    private static boolean isPublicOrigin(SourceEvent source) {
        return "minecraft".equals(source.platform())
                && "minecraft_public".equals(source.channelProfile())
                || "discord".equals(source.platform())
                && "discord_public".equals(source.channelProfile());
    }

    private static boolean sameConversation(SourceEvent a, SourceEvent b) {
        return a.platform().equals(b.platform())
                && a.serverScope().equals(b.serverScope())
                && a.channelProfile().equals(b.channelProfile())
                && a.channelId().equals(b.channelId())
                && Objects.equals(a.conversationId(), b.conversationId())
                && a.senderId().equals(b.senderId());
    }

    private static boolean matchesCanonical(PublicationReceipt receipt, SourceEvent source) {
        return source.eventId().equals(receipt.sourceEventId())
                && source.platform().equals(receipt.sourcePlatform())
                && source.serverScope().equals(receipt.sourceServerScope())
                && source.channelProfile().equals(receipt.sourceChannelProfile())
                && source.channelId().equals(receipt.sourceChannelId())
                && Objects.equals(source.conversationId(), receipt.sourceConversationId())
                && source.senderId().equals(receipt.sourceSenderId());
    }

    private static String operationId(SourceEvent origin, PublicationReceipt receipt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : List.of("exact-retraction-v2", origin.eventId(),
                    origin.platform(), origin.serverScope(), origin.channelProfile(),
                    origin.channelId(), origin.conversationId() == null
                            ? "" : origin.conversationId(), origin.senderId(),
                    origin.occurredAt().toString(), receipt.destination().surface().name(),
                    receipt.destination().scopeId(), receipt.destination().channelId(),
                    receipt.remoteMessageId(), receipt.signedMessageSignature() == null
                            ? "" : receipt.signedMessageSignature())) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private static boolean validSnowflake(String value) {
        return value != null && value.matches("[0-9]{16,20}") && !value.chars().allMatch(c -> c == '0');
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nonblank(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(label + " missing or invalid");
        }
        return value;
    }

    private static Plan refused(Refusal reason) {
        return new Plan(List.of(), 0, reason);
    }
}
