package net.enthusia.staff.velocity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.SanctionChangeService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.ModerationAction;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.ports.WebsiteModerationStore;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;
import net.enthusia.staff.domain.website.AppealAcceptancePreparation;

final class WebsiteAppealEndpoint {
    private static final int MINIMUM_REASON_LENGTH = 10;
    private static final String MODE_BLOCKED = "MODE_BLOCKED";

    private final WebsiteModerationStore store;
    private final AuthorizationPolicy authorization;
    private final SanctionChangeService sanctionChanges;
    private final Supplier<OperationalMode> authorityMode;
    private final Clock clock;
    private final WebsiteApiRequestDecoder decoder;

    WebsiteAppealEndpoint(
            WebsiteModerationStore store,
            AuthorizationPolicy authorization,
            SanctionChangeService sanctionChanges,
            Supplier<OperationalMode> authorityMode,
            Clock clock,
            WebsiteApiRequestDecoder decoder
    ) {
        if (store == null || authorization == null || sanctionChanges == null
                || authorityMode == null || clock == null || decoder == null) {
            throw new IllegalArgumentException("Website appeal endpoint dependencies are required");
        }
        this.store = store;
        this.authorization = authorization;
        this.sanctionChanges = sanctionChanges;
        this.authorityMode = authorityMode;
        this.clock = clock;
        this.decoder = decoder;
    }

    Object accept(Headers headers, ObjectNode input) {
        String idempotencyKey = decoder.singleHeader(headers, "idempotency-key", 120);
        UUID appealId = decoder.uuid(input, "appealId");
        UUID punishmentId = decoder.uuid(input, "punishmentId");
        CaseId caseId = caseId(input);
        String playerAccountId = decoder.uuidText(input, "playerAccountId");
        UUID reviewerAccountId = decoder.uuid(input, "actorAccountId");
        Actor reviewer = websiteActor(reviewerAccountId, decoder.text(input, "actorRank", 16));
        requireMutationAccess(reviewer);
        String reason = decoder.text(input, "reason", 1_000).trim();
        if (reason.length() < MINIMUM_REASON_LENGTH) {
            throw badRequest("INVALID_REASON", "The appeal decision reason is too short");
        }
        AppealAcceptancePreparation preparation = store.prepareAppealAcceptance(
                appealId,
                punishmentId,
                caseId,
                playerAccountId,
                idempotencyKey,
                clock.instant()
        );
        requirePrepared(preparation);
        return applyChange(
                appealId,
                caseId,
                reviewer,
                reviewerAccountId,
                reason,
                idempotencyKey
        );
    }

    static Actor websiteActor(UUID actorId, String rankName) {
        if (actorId == null || rankName == null) {
            throw badRequest("INVALID_ACTOR", "The website reviewer identity is invalid");
        }
        StaffRank rank = switch (rankName) {
            case "MOD" -> StaffRank.MOD;
            case "DEVELOPER" -> StaffRank.DEVELOPER;
            case "ADMIN" -> StaffRank.ADMIN;
            case "FOUNDER" -> StaffRank.FOUNDER;
            default -> throw badRequest("INVALID_ACTOR_RANK", "The website reviewer rank is invalid");
        };
        return new Actor(actorId, "Website Reviewer", rank);
    }

    private Object applyChange(
            UUID appealId,
            CaseId caseId,
            Actor reviewer,
            UUID reviewerAccountId,
            String reason,
            String idempotencyKey
    ) {
        SanctionChangeRequest request = new SanctionChangeRequest(
                new IdempotencyKey("website-appeal:" + digestIdempotency(idempotencyKey)),
                caseId,
                reviewer,
                SanctionChangeAction.END_EARLY,
                Optional.empty(),
                "Appeal " + appealId + " accepted by website reviewer "
                        + reviewerAccountId + ": " + reason
        );
        SanctionChangeResult result = sanctionChanges.apply(request, authorityMode.get());
        if (result instanceof SanctionChangeResult.Applied applied) {
            String outcome = applied.replayed() ? "REPLAYED" : "APPLIED";
            store.completeAppealAcceptance(appealId, "APPLIED", outcome, clock.instant());
            return Map.of(
                    "applied", true,
                    "replayed", applied.replayed(),
                    "affectedSanctions", applied.affectedSanctions()
            );
        }
        throw rejectedChange(appealId, (SanctionChangeResult.Rejected) result);
    }

    private WebsiteApiException rejectedChange(
            UUID appealId,
            SanctionChangeResult.Rejected rejected
    ) {
        if (MODE_BLOCKED.equals(rejected.code())) {
            return new WebsiteApiException(
                    503,
                    "AUTHORITY_NOT_ACTIVE",
                    "Punishment changes are temporarily unavailable"
            );
        }
        String outcome = decoder.safeOutcomeCode(rejected.code());
        store.completeAppealAcceptance(appealId, "REJECTED", outcome, clock.instant());
        return new WebsiteApiException(
                409,
                outcome,
                "The accepted appeal could not change the punishment"
        );
    }

    private CaseId caseId(ObjectNode input) {
        try {
            return new CaseId(decoder.text(input, "caseId", 16));
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_CASE_ID", "The case ID is invalid");
        }
    }

    private void requireMutationAccess(Actor reviewer) {
        if (!authorization.permits(reviewer, ModerationAction.ACCEPT_APPEAL)) {
            throw new WebsiteApiException(
                    403,
                    "APPEAL_MUTATION_FORBIDDEN",
                    "The website reviewer has read-only punishment access"
            );
        }
    }

    private static void requirePrepared(AppealAcceptancePreparation preparation) {
        if (preparation instanceof AppealAcceptancePreparation.Rejected rejected) {
            int status = "PUNISHMENT_NOT_FOUND".equals(rejected.code()) ? 404 : 409;
            throw new WebsiteApiException(status, rejected.code(), rejected.message());
        }
    }

    private static String digestIdempotency(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static WebsiteApiException badRequest(String code, String message) {
        return new WebsiteApiException(400, code, message);
    }
}
