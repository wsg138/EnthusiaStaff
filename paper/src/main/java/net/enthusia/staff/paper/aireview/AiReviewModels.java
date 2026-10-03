package net.enthusia.staff.paper.aireview;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AiReviewModels {
    private AiReviewModels() {
    }

    public enum MessageAction { ALLOW, BLOCK }
    public enum ReviewPriority { NONE, NORMAL, URGENT }
    public enum StrikeRecommendation { NONE, EVIDENCE, STRIKE }
    public enum Containment { NONE, MUTE }
    public enum SupportFlow { NONE, SELF_HARM_CHECK, TARGET_SAFETY_CHECK }
    public enum IncidentKind { HARASSMENT, DOGPILE, THREAT, DOXXING, BLACKMAIL, GROOMING, SAFETY, OTHER }
    public enum CorrectionAuthority { STAFF, ADMIN }
    public enum CorrectionStatus { PENDING_CONFIRMATION, ACCEPTED, REJECTED }

    public record ReviewItem(
            String eventId,
            Instant occurredAt,
            String platform,
            String channelProfile,
            String semanticLabel,
            MessageAction messageAction,
            ReviewPriority reviewPriority,
            List<String> reasonCodes,
            String incidentId
    ) {
        public ReviewItem {
            eventId = required(eventId, "eventId");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            platform = required(platform, "platform");
            channelProfile = required(channelProfile, "channelProfile");
            semanticLabel = required(semanticLabel, "semanticLabel");
            messageAction = Objects.requireNonNull(messageAction, "messageAction");
            reviewPriority = Objects.requireNonNull(reviewPriority, "reviewPriority");
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        }
    }

    public record MessageReference(
            String platform,
            String scopeId,
            String channelId,
            String externalMessageId
    ) {
        public MessageReference {
            platform = required(platform, "platform");
            scopeId = required(scopeId, "scopeId");
            externalMessageId = required(externalMessageId, "externalMessageId");
        }
    }

    public record IncidentSummary(
            String incidentId,
            IncidentKind kind,
            int severity,
            boolean coordinated,
            List<String> participantIds,
            List<String> targetIds
    ) {
        public IncidentSummary {
            incidentId = required(incidentId, "incidentId");
            kind = Objects.requireNonNull(kind, "kind");
            if (severity < 0 || severity > 100) {
                throw new IllegalArgumentException("incident severity must be between 0 and 100");
            }
            participantIds = List.copyOf(participantIds == null ? List.of() : participantIds);
            targetIds = List.copyOf(targetIds == null ? List.of() : targetIds);
        }
    }

    public record Decision(
            MessageAction messageAction,
            String semanticLabel,
            ReviewPriority reviewPriority,
            StrikeRecommendation strikeRecommendation,
            Containment containment,
            Integer containmentDurationSeconds,
            SupportFlow supportFlow,
            Map<String, Double> scores,
            Double confidence,
            List<String> ruleHits,
            List<String> reasonCodes,
            IncidentSummary incident,
            String localModelVersion,
            String policyVersion
    ) {
        public Decision {
            messageAction = Objects.requireNonNull(messageAction, "messageAction");
            semanticLabel = required(semanticLabel, "semanticLabel");
            reviewPriority = Objects.requireNonNull(reviewPriority, "reviewPriority");
            strikeRecommendation = Objects.requireNonNull(strikeRecommendation, "strikeRecommendation");
            containment = Objects.requireNonNull(containment, "containment");
            supportFlow = Objects.requireNonNull(supportFlow, "supportFlow");
            scores = Map.copyOf(scores == null ? Map.of() : scores);
            ruleHits = List.copyOf(ruleHits == null ? List.of() : ruleHits);
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
            localModelVersion = required(localModelVersion, "localModelVersion");
            policyVersion = required(policyVersion, "policyVersion");
        }
    }

    public record CorrectionDecision(
            String semanticLabel,
            MessageAction messageAction,
            ReviewPriority reviewPriority,
            StrikeRecommendation strikeRecommendation,
            Containment containment,
            Integer containmentDurationSeconds,
            SupportFlow supportFlow,
            List<String> reasonCodes
    ) {
        public CorrectionDecision {
            semanticLabel = required(semanticLabel, "semanticLabel");
            messageAction = Objects.requireNonNull(messageAction, "messageAction");
            reviewPriority = Objects.requireNonNull(reviewPriority, "reviewPriority");
            strikeRecommendation = Objects.requireNonNull(strikeRecommendation, "strikeRecommendation");
            containment = Objects.requireNonNull(containment, "containment");
            supportFlow = Objects.requireNonNull(supportFlow, "supportFlow");
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        }

        public static CorrectionDecision from(Decision decision) {
            return new CorrectionDecision(
                    decision.semanticLabel(),
                    decision.messageAction(),
                    decision.reviewPriority(),
                    decision.strikeRecommendation(),
                    decision.containment(),
                    decision.containmentDurationSeconds(),
                    decision.supportFlow(),
                    decision.reasonCodes()
            );
        }

        public CorrectionDecision withAction(MessageAction action) {
            return new CorrectionDecision(
                    semanticLabel, action, reviewPriority, strikeRecommendation, containment,
                    containmentDurationSeconds, supportFlow, reasonCodes
            );
        }

        public CorrectionDecision withReviewPriority(ReviewPriority priority) {
            return new CorrectionDecision(
                    semanticLabel, messageAction, priority, strikeRecommendation, containment,
                    containmentDurationSeconds, supportFlow, reasonCodes
            );
        }

        public CorrectionDecision withSemanticLabel(String label) {
            return new CorrectionDecision(
                    label, messageAction, reviewPriority, strikeRecommendation, containment,
                    containmentDurationSeconds, supportFlow, reasonCodes
            );
        }
    }

    public record Correction(
            String proposalId,
            String eventId,
            CorrectionStatus status,
            CorrectionDecision corrected,
            int approvals,
            int rejections,
            Instant createdAt,
            Instant resolvedAt
    ) {
        public Correction {
            proposalId = required(proposalId, "proposalId");
            eventId = required(eventId, "eventId");
            status = Objects.requireNonNull(status, "status");
            corrected = Objects.requireNonNull(corrected, "corrected");
            if (approvals < 0 || rejections < 0) {
                throw new IllegalArgumentException("correction vote counts cannot be negative");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record Advisory(
            String status,
            String model,
            Boolean flagged,
            Map<String, Double> scores,
            Map<String, Boolean> categories,
            String errorCode,
            Integer latencyMillis,
            Boolean disagreesWithLocal
    ) {
        public Advisory {
            status = required(status, "status");
            scores = Map.copyOf(scores == null ? Map.of() : scores);
            categories = Map.copyOf(categories == null ? Map.of() : categories);
        }
    }

    public record ContextEvidence(
            String eventId,
            MessageReference message,
            String senderId,
            Instant occurredAt,
            String text
    ) {
        public ContextEvidence {
            eventId = required(eventId, "eventId");
            message = Objects.requireNonNull(message, "message");
            senderId = required(senderId, "senderId");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            text = text == null ? "" : text;
        }
    }

    public record EventDetails(
            String eventId,
            String clientId,
            String platform,
            String channelProfile,
            String scopeId,
            String channelId,
            String conversationId,
            String externalMessageId,
            String canonicalMessageId,
            String senderId,
            Instant occurredAt,
            String text,
            String replyToMessageId,
            Decision decision,
            Advisory advisory,
            List<Correction> corrections,
            Correction acceptedCorrection,
            List<ContextEvidence> contextEvidence
    ) {
        public EventDetails {
            eventId = required(eventId, "eventId");
            clientId = required(clientId, "clientId");
            platform = required(platform, "platform");
            channelProfile = required(channelProfile, "channelProfile");
            scopeId = required(scopeId, "scopeId");
            externalMessageId = required(externalMessageId, "externalMessageId");
            senderId = required(senderId, "senderId");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            text = text == null ? "" : text;
            decision = Objects.requireNonNull(decision, "decision");
            corrections = List.copyOf(corrections == null ? List.of() : corrections);
            contextEvidence = List.copyOf(contextEvidence == null ? List.of() : contextEvidence);
        }

        public Correction pendingCorrection(String proposalId) {
            return corrections.stream()
                    .filter(item -> item.proposalId().equals(proposalId))
                    .filter(item -> item.status() == CorrectionStatus.PENDING_CONFIRMATION)
                    .findFirst()
                    .orElse(null);
        }

        public Correction latestPendingCorrection() {
            return corrections.stream()
                    .filter(item -> item.status() == CorrectionStatus.PENDING_CONFIRMATION)
                    .max(java.util.Comparator.comparing(Correction::createdAt))
                    .orElse(null);
        }
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
