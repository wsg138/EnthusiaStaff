package net.enthusia.staff.protocol;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/** Explicit conversion between D08 wire documents and domain values. */
public final class MinecraftPunishmentPreparationMapper {
    private MinecraftPunishmentPreparationMapper() {
    }

    public static MinecraftPunishmentPreparationWire.Request request(
            CaseId caseId,
            CreatePunishmentRequest request
    ) {
        if (caseId == null || request == null) {
            throw new IllegalArgumentException("D08 preparation request must be present");
        }
        return new MinecraftPunishmentPreparationWire.Request(
                MinecraftPunishmentPreparationWire.VERSION,
                caseId.value(),
                request.idempotencyKey().value(),
                request.actor().id(),
                request.actor().displayName(),
                request.targetId(),
                request.reasonId(),
                request.internalExplanation(),
                request.visibility(),
                request.overrideSanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }

    public static CreatePunishmentRequest request(
            MinecraftPunishmentPreparationWire.Request request,
            Actor actor
    ) {
        if (request == null || actor == null || !actor.id().equals(request.actorId())) {
            throw new IllegalArgumentException("prepared actor does not match the authenticated request actor");
        }
        return new CreatePunishmentRequest(
                new IdempotencyKey(request.idempotencyKey()),
                request.targetId(),
                actor,
                request.reasonId(),
                request.internalExplanation(),
                request.visibility(),
                request.overrideSanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }

    public static MinecraftPunishmentPreparationWire.PreparedPlan plan(PunishmentPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("punishment plan must be present");
        }
        return new MinecraftPunishmentPreparationWire.PreparedPlan(
                plan.caseId().value(), plan.idempotencyKey().value(), plan.targetId(),
                plan.actor().id(), plan.actor().displayName(), plan.actor().rank(),
                plan.reasonId(), plan.family(), plan.publicReason(), plan.internalExplanation(),
                plan.configurationVersion(), plan.visibility(), plan.issuedAt().toString(),
                escalation(plan.escalation()), plan.sanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }

    public static PunishmentPlan plan(MinecraftPunishmentPreparationWire.PreparedPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("prepared plan must be present");
        }
        return new PunishmentPlan(
                new CaseId(plan.caseId()), new IdempotencyKey(plan.idempotencyKey()), plan.targetId(),
                new Actor(plan.actorId(), plan.actorName(), plan.actorRank()),
                plan.reasonId(), plan.family(), plan.publicReason(), plan.internalExplanation(),
                plan.configurationVersion(), plan.visibility(), Instant.parse(plan.issuedAt()), escalation(plan.escalation()),
                plan.sanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }

    private static MinecraftPunishmentPreparationWire.Escalation escalation(EscalationDecision value) {
        return new MinecraftPunishmentPreparationWire.Escalation(
                value.rawOrdinal(), value.effectiveOrdinal(), value.recencyBonus(),
                value.contributions().stream().map(item -> new MinecraftPunishmentPreparationWire.Contribution(
                        item.priorSeverity(), item.base(), item.decayEligibility(), item.decayedBy(), item.effective()
                )).toList(),
                value.resultingOffenseDecayEligibility(),
                value.selectedStep().ordinal(), value.selectedStep().label(),
                value.selectedStep().sanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }

    private static EscalationDecision escalation(MinecraftPunishmentPreparationWire.Escalation value) {
        List<EscalationDecision.Contribution> contributions = value.contributions().stream()
                .map(item -> new EscalationDecision.Contribution(
                        item.priorSeverity(), item.base(), item.decayEligibility(), item.decayedBy(), item.effective()
                )).toList();
        PunishmentStep selected = new PunishmentStep(
                value.selectedOrdinal(), value.selectedLabel(),
                value.selectedSanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
        return new EscalationDecision(
                value.rawOrdinal(), value.effectiveOrdinal(), value.recencyBonus(), contributions,
                value.resultingDecayEligibility(), selected
        );
    }

    static MinecraftPunishmentPreparationWire.Sanction sanction(SanctionSpec value) {
        return new MinecraftPunishmentPreparationWire.Sanction(
                value.type(), value.length().kind(), value.length().temporary().map(Duration::toSeconds).orElse(null)
        );
    }

    static SanctionSpec sanction(MinecraftPunishmentPreparationWire.Sanction value) {
        SanctionLength length = switch (value.lengthKind()) {
            case INSTANT -> SanctionLength.instant();
            case PERMANENT -> SanctionLength.permanent();
            case TEMPORARY -> SanctionLength.temporary(Duration.ofSeconds(value.durationSeconds()));
        };
        return new SanctionSpec(value.type(), length);
    }
}
