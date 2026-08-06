package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.website.WebsiteAppealDecisionPreparation;
import net.enthusia.staff.domain.website.WebsiteAppealPage;
import net.enthusia.staff.domain.website.WebsiteAppealSubmission;

final class WebsiteAppealWorkflowEndpoint {
    private static final Set<String> REVIEWER_STATES = Set.of(
            "ALL",
            "OPEN",
            "INFORMATION_REQUESTED",
            "APPROVAL_PENDING",
            "APPLIED",
            "DENIED",
            "REJECTED"
    );

    private final WebsiteModerationStore store;
    private final AuthorizationPolicy authorization;
    private final Clock clock;
    private final WebsiteApiRequestDecoder decoder;
    private final WebsiteAppealEndpoint acceptance;
    private final ObjectMapper json = new ObjectMapper();

    WebsiteAppealWorkflowEndpoint(
            WebsiteModerationStore store,
            AuthorizationPolicy authorization,
            Clock clock,
            WebsiteApiRequestDecoder decoder,
            WebsiteAppealEndpoint acceptance
    ) {
        if (store == null || authorization == null || clock == null
                || decoder == null || acceptance == null) {
            throw new IllegalArgumentException("Website appeal workflow dependencies are required");
        }
        this.store = store;
        this.authorization = authorization;
        this.clock = clock;
        this.decoder = decoder;
        this.acceptance = acceptance;
    }

    Object eligible(ObjectNode input) {
        String accountId = decoder.uuidText(input, "accountId");
        return Map.of(
                "punishments",
                store.eligibleAppeals(accountId, 100, clock.instant())
                        .stream()
                        .map(WebsiteApiResponses::appealCandidate)
                        .toList()
        );
    }

    Object submit(ObjectNode input) {
        UUID punishmentId = decoder.uuid(input, "punishmentId");
        String accountId = decoder.uuidText(input, "accountId");
        String username = decoder.minecraftUsername(input, "username");
        String reason = decoder.text(input, "reason", 1_000).trim();
        if (reason.length() < 10) {
            throw badRequest("INVALID_REASON", "The appeal reason is too short");
        }
        String idempotencyKey = decoder.text(input, "idempotencyKey", 128);
        WebsiteAppealSubmission submission = store.submitAppeal(
                punishmentId,
                accountId,
                username,
                reason,
                idempotencyKey,
                clock.instant()
        );
        Map<String, Object> response = new LinkedHashMap<>(
                WebsiteApiResponses.appeal(submission.appeal())
        );
        response.put("replayed", submission.replayed());
        return response;
    }

    Object list(ObjectNode input) {
        Actor reviewer = reviewer(input);
        requireReviewAccess(reviewer);
        String state = decoder.text(input, "status", 32).toUpperCase(java.util.Locale.ROOT);
        if (!REVIEWER_STATES.contains(state)) {
            throw badRequest("INVALID_APPEAL_STATE", "The appeal state filter is invalid");
        }
        String cursor = optionalText(input, "cursor", 128);
        int limit = decoder.integer(input, "limit", 1, 100);
        WebsiteAppealPage page = store.listAppeals(
                state,
                Optional.ofNullable(cursor),
                limit,
                clock.instant()
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appeals", page.items().stream().map(WebsiteApiResponses::appeal).toList());
        response.put("nextCursor", page.nextCursor().orElse(null));
        return response;
    }

    Object decide(UUID appealId, ObjectNode input) {
        Actor reviewer = reviewer(input);
        requireReviewAccess(reviewer);
        String decision = decoder.text(input, "decision", 32);
        int expectedVersion = decoder.integer(input, "expectedVersion", 1, Integer.MAX_VALUE);
        String note = decoder.text(input, "note", 1_000).trim();
        if (note.length() < 3) {
            throw badRequest("INVALID_DECISION_NOTE", "The appeal decision note is too short");
        }
        String idempotencyKey = decoder.text(input, "idempotencyKey", 128);
        WebsiteAppealDecisionPreparation preparation = store.prepareAppealDecision(
                appealId,
                expectedVersion,
                decision,
                note,
                reviewer.id(),
                reviewer.rank().name(),
                idempotencyKey,
                clock.instant()
        );
        if (preparation.requiresAcceptance()) {
            return accept(preparation, reviewer, note, idempotencyKey);
        }
        Map<String, Object> response = new LinkedHashMap<>(
                WebsiteApiResponses.appeal(preparation.appeal())
        );
        response.put("replayed", preparation.replayed());
        return response;
    }

    private Object accept(
            WebsiteAppealDecisionPreparation preparation,
            Actor reviewer,
            String reason,
            String idempotencyKey
    ) {
        ObjectNode input = json.createObjectNode();
        input.put("appealId", preparation.appeal().appealId().toString());
        input.put("punishmentId", preparation.appeal().punishmentId().toString());
        input.put("caseId", preparation.appeal().caseId().value());
        input.put("playerAccountId", preparation.playerAccountId());
        input.put("actorAccountId", reviewer.id().toString());
        input.put("actorRank", reviewer.rank().name());
        input.put("reason", reason);
        Headers headers = new Headers();
        headers.add("idempotency-key", idempotencyKey);
        return acceptance.accept(headers, input);
    }

    private Actor reviewer(ObjectNode input) {
        UUID actorAccountId = decoder.uuid(input, "actorAccountId");
        String actorRank = decoder.text(input, "actorRank", 16);
        return WebsiteAppealEndpoint.websiteActor(actorAccountId, actorRank);
    }

    private void requireReviewAccess(Actor reviewer) {
        if (!authorization.permits(reviewer, ModerationAction.ACCEPT_APPEAL)) {
            throw new WebsiteApiException(
                    403,
                    "APPEAL_REVIEW_FORBIDDEN",
                    "The website reviewer is not authorized to review appeals"
            );
        }
    }

    private static String optionalText(ObjectNode input, String field, int maximumLength) {
        if (!input.has(field) || input.get(field).isNull()) {
            return null;
        }
        if (!input.get(field).isTextual() || input.get(field).textValue().isBlank()
                || input.get(field).textValue().length() > maximumLength) {
            throw badRequest("INVALID_APPEAL_CURSOR", "The appeal cursor is invalid");
        }
        return input.get(field).textValue();
    }

    private static WebsiteApiException badRequest(String code, String message) {
        return new WebsiteApiException(400, code, message);
    }
}
