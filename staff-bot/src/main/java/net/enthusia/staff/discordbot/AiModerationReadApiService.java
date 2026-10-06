package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.List;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.sanction.ActiveSanction;

/**
 * Narrow read-only projection of authoritative moderation state for Enthusia AI.
 *
 * No mutation path, notes, internal explanations, staff identities, or broad
 * moderation history are exposed here.
 */
final class AiModerationReadApiService {
    private static final int MAX_CASES = 8;

    private final StaffModerationReadService reads;
    private final Clock clock;

    AiModerationReadApiService(StaffModerationRuntime moderation) {
        this(moderation, Clock.systemUTC());
    }

    AiModerationReadApiService(StaffModerationRuntime moderation, Clock clock) {
        this(moderation == null ? null : moderation.reads(), clock);
    }

    AiModerationReadApiService(StaffModerationReadService reads, Clock clock) {
        if (reads == null || clock == null) {
            throw new IllegalArgumentException("AI moderation read dependencies must be present");
        }
        this.reads = reads;
        this.clock = clock;
    }

    AiModerationReadApiModel.Response read(AiModerationReadApiModel.Request request) {
        StaffModerationReadService.MinecraftResolution resolution = reads.resolveMinecraft(request.target());
        if (resolution instanceof StaffModerationReadService.MinecraftResolution.Missing) {
            throw new MissingTargetException();
        }
        if (resolution instanceof StaffModerationReadService.MinecraftResolution.Ambiguous) {
            throw new AmbiguousTargetException();
        }

        StaffModerationReadService.Target target =
                ((StaffModerationReadService.MinecraftResolution.Resolved) resolution).target();
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        var playerId = target.minecraftId().orElseThrow();
        var username = snapshot.linkedMinecraft().stream()
                .filter(linked -> linked.playerId().equals(playerId))
                .findFirst()
                .flatMap(StaffModerationReadService.LinkedMinecraft::username);

        return new AiModerationReadApiModel.Response(
                "enthusia-staff",
                "ai-moderation-state",
                "v1",
                new AiModerationReadApiModel.TargetDto(
                        request.target(),
                        playerId.toString(),
                        username
                ),
                snapshot.activeMinecraftSanctions().stream()
                        .map(AiModerationReadApiService::sanction)
                        .toList(),
                snapshot.recentCases().stream()
                        .limit(MAX_CASES)
                        .map(AiModerationReadApiService::caseDto)
                        .toList(),
                clock.instant()
        );
    }

    private static AiModerationReadApiModel.ActiveSanctionDto sanction(ActiveSanction sanction) {
        return new AiModerationReadApiModel.ActiveSanctionDto(
                sanction.sanctionId().toString(),
                sanction.caseId().toString(),
                sanction.type().name(),
                sanction.publicReason(),
                sanction.issuedAt(),
                sanction.expiresAt()
        );
    }

    private static AiModerationReadApiModel.CaseDto caseDto(CaseReview review) {
        return new AiModerationReadApiModel.CaseDto(
                review.caseId().toString(),
                review.exactReasonId(),
                review.sanctionFamily(),
                review.state().name(),
                review.publicReason(),
                review.issuedAt(),
                review.hasActiveSanctions(),
                review.configurationVersion()
        );
    }

    static final class MissingTargetException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static final class AmbiguousTargetException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
