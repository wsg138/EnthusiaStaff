package net.enthusia.staff.paper.alert;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestAlertIntent;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PunishmentRequestAlertRenderer {
    public PunishmentRequestAlertPresentation render(
            PunishmentRequestAlertClaim claim,
            PunishmentApprovalRequest request,
            String targetName,
            String lifecycleActorName
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(request, "request");
        PunishmentRequestAlertIntent intent = claim.intent();
        validate(intent, request);
        String target = fallback(targetName, request.proposal().targetId());
        String actor = fallback(lifecycleActorName, intent.occurrence().actorId());
        Component message = switch (intent.audience()) {
            case DIRECT_RECIPIENT -> direct(intent, request, target, actor);
            case ELIGIBLE_REVIEWERS -> reviewer(intent, request, target, actor);
            case OPERATIONAL_ADMINISTRATORS -> operational(intent, request, target, actor);
        };
        return new PunishmentRequestAlertPresentation(claim, request, message);
    }

    private static Component direct(
            PunishmentRequestAlertIntent intent,
            PunishmentApprovalRequest request,
            String target,
            String actor
    ) {
        Component heading = switch (intent.eventType()) {
            case REQUEST_SUBMITTED -> Component.text("Punishment request submitted", NamedTextColor.GREEN);
            case REQUEST_CLAIMED -> Component.text("Punishment request review started", NamedTextColor.GOLD);
            case REQUEST_APPROVED -> Component.text("Punishment request approved", NamedTextColor.GREEN);
            case REQUEST_DENIED -> Component.text("Punishment request denied", NamedTextColor.RED);
            case REQUEST_EXPIRED -> Component.text("Punishment request expired", NamedTextColor.YELLOW);
            case REQUEST_EXTERNALLY_FULFILLED -> Component.text(
                    "Punishment request fulfilled by another action",
                    NamedTextColor.GREEN
            );
        };
        Component details = baseDetails(request, target);
        if (intent.eventType() == PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED) {
            details = details
                    .append(line("Required reviewer: " + request.proposal().requiredRank()))
                    .append(line("Expires: " + request.expiresAt()));
        } else if (intent.eventType() == PunishmentRequestLifecycleEventType.REQUEST_CLAIMED) {
            details = details
                    .append(line("Reviewer: " + actor))
                    .append(line("A review has begun; this does not guarantee approval."));
        } else if (hasCase(intent.eventType())) {
            details = details.append(line("Case: " + request.resultingCaseId()));
        }
        return heading.append(details);
    }

    private static Component reviewer(
            PunishmentRequestAlertIntent intent,
            PunishmentApprovalRequest request,
            String target,
            String actor
    ) {
        Component heading = Component.text(reviewerHeading(intent.eventType()), NamedTextColor.GOLD);
        Component details = baseDetails(request, target)
                .append(line("Requester: " + request.proposal().requester().displayName()))
                .append(line("Required reviewer: " + request.proposal().requiredRank()))
                .append(line("Expires: " + request.expiresAt()));
        if (intent.eventType() == PunishmentRequestLifecycleEventType.REQUEST_CLAIMED) {
            details = details.append(line("Claimed by: " + actor));
        }
        if (hasCase(intent.eventType())) {
            details = details.append(line("Case: " + request.resultingCaseId()));
        }
        if (request.status() == PunishmentRequestStatus.PENDING) {
            String command = "/punish review " + request.requestId();
            details = details.append(Component.newline()).append(
                    Component.text("[Open request]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand(command))
            );
        }
        return heading.append(details);
    }

    private static Component operational(
            PunishmentRequestAlertIntent intent,
            PunishmentApprovalRequest request,
            String target,
            String actor
    ) {
        Component heading = Component.text(
                "Punishment request lifecycle: " + intent.eventType(),
                NamedTextColor.LIGHT_PURPLE
        );
        Component details = baseDetails(request, target)
                .append(line("Requester: " + request.proposal().requester().displayName()))
                .append(line("Status: " + request.status()))
                .append(line("Revision: " + request.revision()));
        if (intent.occurrence().actorId() != null) {
            details = details.append(line("Actor: " + actor));
        }
        if (hasCase(intent.eventType())) {
            details = details.append(line("Case: " + request.resultingCaseId()));
        }
        return heading.append(details);
    }

    private static Component baseDetails(PunishmentApprovalRequest request, String target) {
        return line("Target: " + target)
                .append(line("Reason: " + request.proposal().publicReason()
                        + " (" + request.proposal().reasonId() + ")"))
                .append(line("Request: " + request.requestId()));
    }

    private static Component line(String value) {
        return Component.newline().append(Component.text(value, NamedTextColor.GRAY));
    }

    private static String reviewerHeading(PunishmentRequestLifecycleEventType eventType) {
        return switch (eventType) {
            case REQUEST_SUBMITTED -> "New punishment request requires review";
            case REQUEST_CLAIMED -> "Punishment request review activity";
            case REQUEST_APPROVED -> "Punishment request approved";
            case REQUEST_DENIED -> "Punishment request denied";
            case REQUEST_EXPIRED -> "Punishment request expired";
            case REQUEST_EXTERNALLY_FULFILLED -> "Punishment request externally fulfilled";
        };
    }

    private static boolean hasCase(PunishmentRequestLifecycleEventType eventType) {
        return eventType == PunishmentRequestLifecycleEventType.REQUEST_APPROVED
                || eventType == PunishmentRequestLifecycleEventType.REQUEST_EXTERNALLY_FULFILLED;
    }

    private static String fallback(String name, UUID playerId) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return playerId == null ? "unknown" : playerId.toString();
    }

    private static void validate(PunishmentRequestAlertIntent intent, PunishmentApprovalRequest request) {
        if (!intent.requestId().equals(request.requestId())) {
            throw new IllegalArgumentException("alert intent request does not match loaded request");
        }
        if (intent.visibility() != request.proposal().visibility()) {
            throw new IllegalArgumentException("alert visibility does not match loaded request");
        }
        if (intent.audience() == PunishmentRequestAlertAudience.DIRECT_RECIPIENT
                && !intent.recipientId().equals(request.proposal().requester().id())) {
            throw new IllegalArgumentException("direct alert recipient is not the request submitter");
        }
        if (intent.eventType() == PunishmentRequestLifecycleEventType.REQUEST_CLAIMED
                && intent.occurrence().actorId() == null) {
            throw new IllegalArgumentException("claim presentation requires a reviewer identifier");
        }
        if (hasCase(intent.eventType()) && request.resultingCaseId() == null) {
            throw new IllegalArgumentException("fulfilled presentation requires a case identifier");
        }
        if ((intent.eventType() == PunishmentRequestLifecycleEventType.REQUEST_DENIED
                || intent.eventType() == PunishmentRequestLifecycleEventType.REQUEST_EXPIRED)
                && request.resultingCaseId() != null) {
            throw new IllegalArgumentException("denied or expired presentation cannot contain a case identifier");
        }
    }
}
