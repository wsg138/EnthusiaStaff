package net.enthusia.staff.domain.policyv2.publicview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2Store;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/**
 * Pure construction of the canonical public projection from the minimum
 * lifecycle data needed for presentation.
 */
public final class PolicyV2PublicProjector {
    public PolicyV2PublicProjection project(Source source) {
        List<PolicyV2PublicProjection.PublicSanction> sanctions = sanctions(source);
        List<PolicyV2PublicProjection.PublicRemedy> remedies = remedies(source);
        PolicyV2PublicProjection.AppealStatus appealStatus = appealStatus(source);
        return new PolicyV2PublicProjection(
                source.caseId(),
                source.metadata().currentPlayerName(),
                source.metadata().incidentPlayerName(),
                source.metadata().publicCategory(),
                offenseLabel(source, visibleOffenseId(source)),
                source.metadata().publicReason(),
                relatedHistory(source.metadata()),
                status(source, sanctions, remedies, appealStatus),
                appealStatus,
                source.issuedAt(),
                expiration(sanctions),
                sanctions,
                remedies,
                timeline(source),
                Optional.of(source.policyVersion()),
                source.revision()
        );
    }

    private static List<PolicyV2PublicProjection.PublicSanction> sanctions(Source source) {
        return source.currentSanctions().sanctions().stream()
                .map(spec -> publicSanction(source, spec))
                .toList();
    }

    private static PolicyV2PublicProjection.PublicSanction publicSanction(Source source, SanctionSpec spec) {
        PolicyV2PublicProjection.PublicSanctionType type =
                PolicyV2PublicProjection.publicType(spec.type());
        Optional<Instant> endsAt = spec.length().expirationFrom(source.currentSanctions().occurredAt());
        return new PolicyV2PublicProjection.PublicSanction(
                type,
                PolicyV2PublicProjection.publicSummary(type),
                sanctionStatus(source, spec.length(), endsAt),
                endsAt
        );
    }

    private static PolicyV2PublicProjection.PublicSanction.SanctionStatus sanctionStatus(
            Source source,
            SanctionLength length,
            Optional<Instant> endsAt
    ) {
        if (source.findingState() == BehavioralHistoryEntry.FindingState.OVERTURNED) {
            return PolicyV2PublicProjection.PublicSanction.SanctionStatus.REVOKED;
        }
        if (length.isInstant() || endsAt.filter(end -> !end.isAfter(source.now())).isPresent()) {
            return PolicyV2PublicProjection.PublicSanction.SanctionStatus.COMPLETED;
        }
        return PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE;
    }

    private static List<PolicyV2PublicProjection.PublicRemedy> remedies(Source source) {
        return source.remedies().stream().map(PolicyV2PublicProjector::publicRemedy).toList();
    }

    private static PolicyV2PublicProjection.PublicRemedy publicRemedy(PolicyV2Store.RemedyRecord record) {
        PolicyV2PublicProjection.PublicRemedyType type = remedyType(record.remedy().type());
        PolicyV2PublicProjection.PublicRemedy.RemedyStatus status = switch (record.status()) {
            case REQUIRED -> PolicyV2PublicProjection.PublicRemedy.RemedyStatus.REQUIRED;
            case SATISFIED -> PolicyV2PublicProjection.PublicRemedy.RemedyStatus.SATISFIED;
            case WAIVED -> PolicyV2PublicProjection.PublicRemedy.RemedyStatus.WAIVED;
        };
        return new PolicyV2PublicProjection.PublicRemedy(type, remedySummary(type), status);
    }

    private static PolicyV2PublicProjection.PublicRemedyType remedyType(RemedySpec.Type type) {
        return switch (type) {
            case REMOVE_CONTENT -> PolicyV2PublicProjection.PublicRemedyType.CONTENT_REMOVAL;
            case CONFISCATE -> PolicyV2PublicProjection.PublicRemedyType.CONFISCATION;
            case ACCESS_RESTRICTION -> PolicyV2PublicProjection.PublicRemedyType.ACCESS_RESTRICTION;
            case CORRECT_PROFILE -> PolicyV2PublicProjection.PublicRemedyType.PROFILE_COMPLIANCE;
            case OTHER -> PolicyV2PublicProjection.PublicRemedyType.OTHER;
        };
    }

    private static String remedySummary(PolicyV2PublicProjection.PublicRemedyType type) {
        return switch (type) {
            case CONTENT_REMOVAL -> "Content removal";
            case CONFISCATION -> "Confiscation";
            case ACCESS_RESTRICTION -> "Access compliance restriction";
            case PROFILE_COMPLIANCE -> "Profile compliance";
            case OTHER -> "Compliance remedy";
        };
    }

    private static Optional<String> relatedHistory(PublicMetadata metadata) {
        return metadata.relatedHistoryLabel().map(label ->
                "Repeat related " + label.toLowerCase(Locale.ROOT) + " violation"
        );
    }

    private static PolicyV2PublicProjection.AppealStatus appealStatus(Source source) {
        return source.appealEvents().stream()
                .max(Comparator.comparing(PolicyV2Store.AppealEvent::occurredAt))
                .map(event -> appealStatus(event.eventType()))
                .orElseGet(() -> source.metadata().appealAvailable()
                        ? PolicyV2PublicProjection.AppealStatus.AVAILABLE
                        : PolicyV2PublicProjection.AppealStatus.NONE);
    }

    private static PolicyV2PublicProjection.AppealStatus appealStatus(PolicyV2Store.AppealEventType type) {
        return switch (type) {
            case LINKED -> PolicyV2PublicProjection.AppealStatus.AVAILABLE;
            case SUBMITTED -> PolicyV2PublicProjection.AppealStatus.SUBMITTED;
            case INFORMATION_REQUESTED, OTHER -> PolicyV2PublicProjection.AppealStatus.UNDER_REVIEW;
            case APPROVED, REVISION_APPLIED -> PolicyV2PublicProjection.AppealStatus.APPROVED;
            case DENIED -> PolicyV2PublicProjection.AppealStatus.DENIED;
            case WITHDRAWN -> PolicyV2PublicProjection.AppealStatus.WITHDRAWN;
        };
    }

    private static PolicyV2PublicProjection.Status status(
            Source source,
            List<PolicyV2PublicProjection.PublicSanction> sanctions,
            List<PolicyV2PublicProjection.PublicRemedy> remedies,
            PolicyV2PublicProjection.AppealStatus appealStatus
    ) {
        if (source.findingState() == BehavioralHistoryEntry.FindingState.OVERTURNED) {
            return PolicyV2PublicProjection.Status.OVERTURNED;
        }
        if (appealStatus == PolicyV2PublicProjection.AppealStatus.SUBMITTED
                || appealStatus == PolicyV2PublicProjection.AppealStatus.UNDER_REVIEW) {
            return PolicyV2PublicProjection.Status.APPEALED;
        }
        if (isExpired(sanctions, remedies)) {
            return PolicyV2PublicProjection.Status.EXPIRED;
        }
        return revisionStatus(source);
    }

    private static boolean isExpired(
            List<PolicyV2PublicProjection.PublicSanction> sanctions,
            List<PolicyV2PublicProjection.PublicRemedy> remedies
    ) {
        boolean hasPublicAction = !sanctions.isEmpty() || !remedies.isEmpty();
        boolean sanctionsDone = sanctions.stream().noneMatch(sanction ->
                sanction.status() == PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE);
        boolean remediesDone = remedies.stream().noneMatch(remedy ->
                remedy.status() == PolicyV2PublicProjection.PublicRemedy.RemedyStatus.REQUIRED);
        return hasPublicAction && sanctionsDone && remediesDone;
    }

    private static PolicyV2PublicProjection.Status revisionStatus(Source source) {
        Instant reclassifiedAt = source.findingRevisions().stream()
                .filter(revision -> revision.changeKind() == PolicyV2Store.FindingChangeKind.RECLASSIFICATION)
                .map(PolicyV2Store.FindingRevisionRecord::occurredAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.MIN);
        boolean reduced = source.currentSanctions().changeKind() == PolicyV2Store.SanctionChangeKind.LENIENCY;
        if (!reclassifiedAt.equals(Instant.MIN)
                && !reclassifiedAt.isBefore(source.currentSanctions().occurredAt())) {
            return PolicyV2PublicProjection.Status.RECLASSIFIED;
        }
        return reduced ? PolicyV2PublicProjection.Status.REDUCED : PolicyV2PublicProjection.Status.ACTIVE;
    }

    private static Optional<Instant> expiration(List<PolicyV2PublicProjection.PublicSanction> sanctions) {
        boolean permanentActive = sanctions.stream().anyMatch(sanction ->
                sanction.status() == PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE
                        && sanction.endsAt().isEmpty());
        if (permanentActive) {
            return Optional.empty();
        }
        return sanctions.stream()
                .map(PolicyV2PublicProjection.PublicSanction::endsAt)
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder());
    }

    private static List<PolicyV2PublicProjection.PublicRevision> timeline(Source source) {
        List<PolicyV2PublicProjection.PublicRevision> entries = new ArrayList<>();
        entries.add(new PolicyV2PublicProjection.PublicRevision(
                PolicyV2PublicProjection.RevisionType.ISSUED, source.issuedAt(), "Case issued"));
        addFindingRevisions(source, entries);
        addSanctionRevisions(source, entries);
        addAppeals(source, entries);
        addRemedyUpdates(source, entries);
        entries.sort(Comparator.comparing(PolicyV2PublicProjection.PublicRevision::occurredAt)
                .thenComparing(revision -> revision.type().name()));
        return List.copyOf(entries);
    }

    private static void addFindingRevisions(
            Source source,
            List<PolicyV2PublicProjection.PublicRevision> entries
    ) {
        for (PolicyV2Store.FindingRevisionRecord revision : source.findingRevisions()) {
            boolean overturned = revision.changeKind() == PolicyV2Store.FindingChangeKind.OVERTURN;
            String summary = overturned
                    ? "Case overturned"
                    : "Reclassified to " + offenseLabel(source, revision.toOffenseId().orElseThrow());
            entries.add(new PolicyV2PublicProjection.PublicRevision(
                    overturned
                            ? PolicyV2PublicProjection.RevisionType.OVERTURNED
                            : PolicyV2PublicProjection.RevisionType.RECLASSIFIED,
                    revision.occurredAt(),
                    summary
            ));
        }
    }

    private static void addSanctionRevisions(
            Source source,
            List<PolicyV2PublicProjection.PublicRevision> entries
    ) {
        for (PolicyV2Store.SanctionRevisionRecord revision : source.sanctionRevisions()) {
            if (revision.changeKind() == PolicyV2Store.SanctionChangeKind.INITIAL) {
                continue;
            }
            boolean reduced = revision.changeKind() == PolicyV2Store.SanctionChangeKind.LENIENCY;
            entries.add(new PolicyV2PublicProjection.PublicRevision(
                    reduced
                            ? PolicyV2PublicProjection.RevisionType.REDUCED
                            : PolicyV2PublicProjection.RevisionType.SANCTION_UPDATED,
                    revision.occurredAt(),
                    reduced ? "Sanction reduced" : "Sanction updated"
            ));
        }
    }

    private static void addAppeals(
            Source source,
            List<PolicyV2PublicProjection.PublicRevision> entries
    ) {
        source.appealEvents().stream()
                .map(PolicyV2PublicProjector::publicAppealEvent)
                .flatMap(Optional::stream)
                .forEach(entries::add);
    }

    private static Optional<PolicyV2PublicProjection.PublicRevision> publicAppealEvent(
            PolicyV2Store.AppealEvent event
    ) {
        return switch (event.eventType()) {
            case SUBMITTED -> publicAppeal(event, false, "Appeal submitted");
            case INFORMATION_REQUESTED -> publicAppeal(event, false, "Appeal under review");
            case APPROVED -> publicAppeal(event, true, "Appeal approved");
            case DENIED -> publicAppeal(event, true, "Appeal denied");
            case WITHDRAWN -> publicAppeal(event, true, "Appeal withdrawn");
            case REVISION_APPLIED -> publicAppeal(event, true, "Appeal revision applied");
            case LINKED, OTHER -> Optional.empty();
        };
    }

    private static Optional<PolicyV2PublicProjection.PublicRevision> publicAppeal(
            PolicyV2Store.AppealEvent event,
            boolean resolved,
            String summary
    ) {
        return Optional.of(new PolicyV2PublicProjection.PublicRevision(
                resolved
                        ? PolicyV2PublicProjection.RevisionType.APPEAL_RESOLVED
                        : PolicyV2PublicProjection.RevisionType.APPEALED,
                event.occurredAt(),
                summary
        ));
    }

    private static void addRemedyUpdates(
            Source source,
            List<PolicyV2PublicProjection.PublicRevision> entries
    ) {
        for (PolicyV2Store.RemedyRecord remedy : source.remedies()) {
            if (remedy.status() == PolicyV2Store.RemedyStatus.REQUIRED) {
                continue;
            }
            String summary = remedySummary(remedyType(remedy.remedy().type()))
                    + (remedy.status() == PolicyV2Store.RemedyStatus.SATISFIED ? " satisfied" : " waived");
            entries.add(new PolicyV2PublicProjection.PublicRevision(
                    PolicyV2PublicProjection.RevisionType.REMEDY_UPDATED,
                    remedy.updatedAt(),
                    summary
            ));
        }
    }

    private static String visibleOffenseId(Source source) {
        return source.effectiveOffenseId().orElse(source.originalOffenseId());
    }

    private static String offenseLabel(Source source, String offenseId) {
        return source.publicOffenseLabels().getOrDefault(offenseId, "Policy violation");
    }

    public record PublicMetadata(
            Optional<String> currentPlayerName,
            Optional<String> incidentPlayerName,
            String publicCategory,
            String publicReason,
            Optional<String> relatedHistoryLabel,
            boolean appealAvailable
    ) {
        public PublicMetadata {
            if (currentPlayerName == null || incidentPlayerName == null || relatedHistoryLabel == null
                    || publicCategory == null || publicCategory.isBlank()
                    || publicReason == null || publicReason.isBlank()) {
                throw new IllegalArgumentException("public metadata is invalid");
            }
            relatedHistoryLabel = relatedHistoryLabel.map(String::trim);
            if (relatedHistoryLabel.filter(String::isBlank).isPresent()) {
                throw new IllegalArgumentException("related history label is invalid");
            }
        }
    }

    public record Source(
            String caseId,
            BehavioralHistoryEntry.FindingState findingState,
            String originalOffenseId,
            Optional<String> effectiveOffenseId,
            Instant issuedAt,
            PolicyV2Store.SanctionRevisionRecord currentSanctions,
            List<PolicyV2Store.RemedyRecord> remedies,
            List<PolicyV2Store.FindingRevisionRecord> findingRevisions,
            List<PolicyV2Store.SanctionRevisionRecord> sanctionRevisions,
            List<PolicyV2Store.AppealEvent> appealEvents,
            PublicMetadata metadata,
            Map<String, String> publicOffenseLabels,
            String policyVersion,
            Instant now,
            long revision
    ) {
        public Source {
            if (caseId == null || caseId.isBlank() || findingState == null
                    || originalOffenseId == null || originalOffenseId.isBlank()
                    || effectiveOffenseId == null || issuedAt == null || currentSanctions == null
                    || remedies == null || findingRevisions == null || sanctionRevisions == null
                    || appealEvents == null || metadata == null || publicOffenseLabels == null
                    || policyVersion == null || policyVersion.isBlank() || now == null || revision < 0) {
                throw new IllegalArgumentException("public projection source is invalid");
            }
            boolean overturned = findingState == BehavioralHistoryEntry.FindingState.OVERTURNED;
            if (overturned == effectiveOffenseId.isPresent() || now.isBefore(issuedAt)) {
                throw new IllegalArgumentException("public projection finding or time is invalid");
            }
            requireCase(caseId, currentSanctions.caseId());
            remedies.forEach(record -> requireCase(caseId, record.caseId()));
            findingRevisions.forEach(record -> requireCase(caseId, record.caseId()));
            sanctionRevisions.forEach(record -> requireCase(caseId, record.caseId()));
            appealEvents.forEach(record -> requireCase(caseId, record.caseId()));
            remedies = List.copyOf(remedies);
            findingRevisions = List.copyOf(findingRevisions);
            sanctionRevisions = List.copyOf(sanctionRevisions);
            appealEvents = List.copyOf(appealEvents);
            publicOffenseLabels = Map.copyOf(publicOffenseLabels);
        }

        private static void requireCase(String expected, String actual) {
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException("public projection source mixes case records");
            }
        }
    }
}
