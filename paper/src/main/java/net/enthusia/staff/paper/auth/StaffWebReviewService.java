package net.enthusia.staff.paper.auth;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.auth.StaffTargetHierarchyPolicy;
import net.enthusia.staff.domain.ports.StaffSessionStore;

/** Private, authoritative review actions; only the existing durable request service may commit. */
public final class StaffWebReviewService {
    private static final int MAX_NOTE = 500;

    public record Dependencies(Clock clock, Supplier<OperationalMode> mode,
            Supplier<PunishmentRequestService> service, Supplier<StaffSessionStore> staffSessions) {
        public Dependencies {
            Objects.requireNonNull(clock);
            Objects.requireNonNull(mode);
            Objects.requireNonNull(service);
            Objects.requireNonNull(staffSessions);
        }
    }

    public record Request(UUID actorId, UUID requestId, String note) {
        public Request {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    public record Decision(String state, String requestId, String caseId) { }

    private final Dependencies dependencies;
    private final Function<UUID, Actor> actorResolver;
    private final Function<UUID, Optional<StaffRank>> targetRanks;
    private final StaffTargetHierarchyPolicy hierarchy = new StaffTargetHierarchyPolicy();

    public StaffWebReviewService(Dependencies dependencies, Function<UUID, Actor> actorResolver,
            Function<UUID, Optional<StaffRank>> targetRanks) {
        this.dependencies = Objects.requireNonNull(dependencies);
        this.actorResolver = Objects.requireNonNull(actorResolver);
        this.targetRanks = Objects.requireNonNull(targetRanks);
    }

    public Decision execute(String operation, Request request) {
        if (request == null || (!"approve".equals(operation) && !"deny".equals(operation))) {
            throw new IllegalArgumentException("unsupported staff review operation");
        }
        if (dependencies.mode().get() != OperationalMode.ACTIVE) {
            throw new SecurityException("staff review authority is not active");
        }
        Actor actor = Objects.requireNonNull(actorResolver.apply(request.actorId()), "staff actor");
        if (actor.rank() != StaffRank.MOD && actor.rank() != StaffRank.ADMIN
                && actor.rank() != StaffRank.FOUNDER) {
            throw new SecurityException("current moderator authority is required");
        }
        StaffSessionStore staffSessions = dependencies.staffSessions().get();
        if (staffSessions == null || staffSessions.active(actor.id())
                .filter(session -> session.state()
                        == net.enthusia.staff.domain.staff.StaffSessionState.ACTIVE).isEmpty()) {
            throw new SecurityException("current active Minecraft Staff Mode is required");
        }
        PunishmentRequestService service = dependencies.service().get();
        if (service == null) {
            throw new IllegalStateException("punishment request service is unavailable");
        }
        PunishmentApprovalRequest current = service.find(request.requestId())
                .orElseThrow(() -> new IllegalArgumentException("request was not found"));
        if (!service.mayReview(actor, current)) {
            throw new SecurityException("current staff actor may not review this request");
        }
        Optional<StaffRank> targetRank = Objects.requireNonNull(
                targetRanks.apply(current.proposal().targetId()), "target rank lookup");
        if (targetRank.isPresent() && !hierarchy.permits(actor.rank(), targetRank.orElseThrow())) {
            throw new SecurityException("target is protected by current Minecraft staff hierarchy");
        }
        String note = request.note() == null ? "" : request.note().strip();
        if ("deny".equals(operation) && (note.isEmpty() || note.length() > MAX_NOTE)) {
            throw new IllegalArgumentException("denial requires a note up to 500 characters");
        }
        PunishmentRequestResult lease = service.acquire(request.requestId(), actor);
        if (!(lease instanceof PunishmentRequestResult.Leased acquired)) {
            return rejected(lease);
        }
        // Re-read rank, Staff Mode, and target hierarchy after acquiring the lease.
        Actor currentActor = Objects.requireNonNull(actorResolver.apply(request.actorId()), "current reviewer");
        if (currentActor.rank() != actor.rank() || !currentActor.id().equals(actor.id())
                || staffSessions.active(actor.id()).filter(session -> session.state()
                        == net.enthusia.staff.domain.staff.StaffSessionState.ACTIVE).isEmpty()
                || !service.mayReview(currentActor, current)
                || targetRanks.apply(current.proposal().targetId())
                        .filter(rank -> !hierarchy.permits(currentActor.rank(), rank)).isPresent()) {
            throw new SecurityException("reviewer authority changed during request claim");
        }
        PunishmentRequestResult result = "approve".equals(operation)
                ? service.approve(acquired.lease(), currentActor)
                : service.deny(acquired.lease(), currentActor, note);
        if (result instanceof PunishmentRequestResult.Approved approved) {
            return new Decision("APPROVED", request.requestId().toString(), approved.caseId().value());
        }
        if (result instanceof PunishmentRequestResult.Denied) {
            return new Decision("DENIED", request.requestId().toString(), "");
        }
        return rejected(result);
    }

    private static Decision rejected(PunishmentRequestResult result) {
        if (result instanceof PunishmentRequestResult.Rejected denied) {
            throw new IllegalStateException("review was not committed: " + denied.code());
        }
        throw new IllegalStateException("review was not committed");
    }
}
