package net.enthusia.staff.paper.auth;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffTargetHierarchyPolicy;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.report.ReportAction;
import net.enthusia.staff.domain.report.ReportStateChangeRequest;
import net.enthusia.staff.domain.report.ReportStateChangeResult;
import net.enthusia.staff.domain.staff.StaffSessionState;

/** Minecraft-authoritative Discord report transitions with current duty, rank, and revision checks. */
public final class StaffWebReportService {
    private static final int MAX_NOTE = 500;

    public record Dependencies(Clock clock, Supplier<OperationalMode> mode,
            Supplier<ReportStore> reports, Supplier<StaffSessionStore> staffSessions) {
        public Dependencies {
            Objects.requireNonNull(clock);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(reports);
            Objects.requireNonNull(staffSessions);
        }
    }

    public record Request(UUID actorId, UUID reportId, long expectedRevision,
            String note, String operationId) {
        public Request {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(reportId, "reportId");
        }
    }

    public record Decision(String state, long revision, boolean replayed) { }

    private final Dependencies dependencies;
    private final Function<UUID, Actor> actors;
    private final Function<UUID, Optional<StaffRank>> targetRanks;
    private final StaffTargetHierarchyPolicy hierarchy = new StaffTargetHierarchyPolicy();

    public StaffWebReportService(Dependencies dependencies, Function<UUID, Actor> actors,
            Function<UUID, Optional<StaffRank>> targetRanks) {
        this.dependencies = Objects.requireNonNull(dependencies);
        this.actors = Objects.requireNonNull(actors);
        this.targetRanks = Objects.requireNonNull(targetRanks);
    }

    public Decision execute(String operation, Request request) {
        if (request == null || request.expectedRevision() < 0) {
            throw new IllegalArgumentException("valid report action is required");
        }
        ReportAction action = switch (operation) {
            case "claim" -> ReportAction.CLAIM;
            case "close" -> ReportAction.CLOSE;
            case "no-violation" -> ReportAction.NO_VIOLATION;
            case "await-review" -> ReportAction.AWAIT_REVIEW;
            default -> throw new IllegalArgumentException("unsupported report action");
        };
        if (dependencies.mode().get() != OperationalMode.ACTIVE) {
            throw new SecurityException("Minecraft report authority is not active");
        }
        Actor actor = authorizedActor(request.actorId());
        StaffSessionStore sessions = Objects.requireNonNull(dependencies.staffSessions().get(),
                "staff session store unavailable");
        requireDuty(sessions, actor.id());
        String note = request.note() == null ? "" : request.note().strip();
        if (action == ReportAction.CLAIM && note.isEmpty()) {
            note = "Claimed from the private Discord review queue";
        }
        if (note.isEmpty() || note.length() > MAX_NOTE) {
            throw new IllegalArgumentException("private report note must be 1 to 500 characters");
        }
        if (request.operationId() == null || !request.operationId().matches("[0-9]{15,20}")) {
            throw new IllegalArgumentException("valid Discord operation ID is required");
        }
        ReportStore store = Objects.requireNonNull(dependencies.reports().get(), "report store unavailable");
        var summary = store.details(request.reportId())
                .orElseThrow(() -> new IllegalArgumentException("report was not found")).summary();
        // Preserve idempotent retries: the transactional store checks the recorded operation first,
        // then enforces the expected revision with a locked compare-and-swap.
        requireTargetAuthority(actor, summary.targetId());

        // Recheck the actor's live permissions and duty immediately before the CAS mutation.
        Actor current = authorizedActor(request.actorId());
        requireDuty(sessions, current.id());
        requireTargetAuthority(current, summary.targetId());
        if (dependencies.mode().get() != OperationalMode.ACTIVE) {
            throw new SecurityException("Minecraft report authority became inactive");
        }
        String operationKey = "discord-report:" + request.operationId();
        ReportStateChangeResult result = store.changeState(new ReportStateChangeRequest(
                request.reportId(), current.id(), action, request.expectedRevision(), note,
                new IdempotencyKey(operationKey), dependencies.clock().instant()));
        if (result instanceof ReportStateChangeResult.Applied applied) {
            return new Decision(applied.state().name(), applied.revision(), applied.replayed());
        }
        ReportStateChangeResult.Rejected rejected = (ReportStateChangeResult.Rejected) result;
        throw new IllegalStateException("report was not changed: " + rejected.code());
    }

    private Actor authorizedActor(UUID actorId) {
        Actor actor = Objects.requireNonNull(actors.apply(actorId), "current staff actor");
        if (!actor.id().equals(actorId) || (actor.rank() != StaffRank.MOD
                && actor.rank() != StaffRank.ADMIN && actor.rank() != StaffRank.FOUNDER)) {
            throw new SecurityException("current Minecraft moderator rank is required");
        }
        return actor;
    }

    private static void requireDuty(StaffSessionStore sessions, UUID actorId) {
        if (sessions.active(actorId).filter(session ->
                session.state() == StaffSessionState.ACTIVE).isEmpty()) {
            throw new SecurityException("active Minecraft Staff Mode is required");
        }
    }

    private void requireTargetAuthority(Actor actor, UUID targetId) {
        if (targetRanks.apply(targetId)
                .filter(rank -> !hierarchy.permits(actor.rank(), rank)).isPresent()) {
            throw new SecurityException("report target is protected by staff hierarchy");
        }
    }
}
