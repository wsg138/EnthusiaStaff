package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.sanction.ActiveSanction;

/** Bounded read-only projection of authoritative Minecraft moderation state for Enthusia AI. */
final class AiModerationReadService {
    private final StaffModerationReadService reads;
    private final Clock clock;

    AiModerationReadService(StaffModerationReadService reads, Clock clock) {
        if (reads == null || clock == null) {
            throw new IllegalArgumentException("AI moderation read dependencies must be present");
        }
        this.reads = reads;
        this.clock = clock;
    }

    AiModerationReadApiModel.SubjectStateResponse subjectState(
            AiModerationReadApiModel.SubjectStateRequest request
    ) {
        StaffModerationReadService.MinecraftResolution resolution =
                reads.resolveMinecraft(request.minecraftUsername());
        if (resolution instanceof StaffModerationReadService.MinecraftResolution.Missing) {
            return empty("MISSING", request.minecraftUsername());
        }
        if (resolution instanceof StaffModerationReadService.MinecraftResolution.Ambiguous) {
            return empty("AMBIGUOUS", request.minecraftUsername());
        }
        StaffModerationReadService.MinecraftResolution.Resolved resolved =
                (StaffModerationReadService.MinecraftResolution.Resolved) resolution;
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(resolved.target());
        String playerId = resolved.target().minecraftId().orElseThrow().toString();
        return new AiModerationReadApiModel.SubjectStateResponse(
                "RESOLVED",
                request.minecraftUsername(),
                Optional.of(playerId),
                snapshot.activeMinecraftSanctions().stream().map(this::sanction).toList(),
                snapshot.recentCases().stream().map(AiModerationReadService::caseDto).toList(),
                clock.instant()
        );
    }

    private AiModerationReadApiModel.SubjectStateResponse empty(String status, String username) {
        return new AiModerationReadApiModel.SubjectStateResponse(
                status, username, Optional.empty(), List.of(), List.of(), clock.instant());
    }

    private AiModerationReadApiModel.ActiveSanctionDto sanction(ActiveSanction sanction) {
        Optional<CaseReview> review = reads.caseReview(sanction.caseId());
        return new AiModerationReadApiModel.ActiveSanctionDto(
                sanction.sanctionId().toString(),
                sanction.caseId().toString(),
                sanction.type().name(),
                sanction.publicReason(),
                review.map(CaseReview::exactReasonId),
                review.map(CaseReview::sanctionFamily),
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
                review.issuedAt()
        );
    }
}
