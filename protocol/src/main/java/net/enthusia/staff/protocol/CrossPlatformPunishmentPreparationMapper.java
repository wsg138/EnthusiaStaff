package net.enthusia.staff.protocol;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;

/** Explicit mapping for the private Both-plan boundary. */
public final class CrossPlatformPunishmentPreparationMapper {
    private CrossPlatformPunishmentPreparationMapper() { }

    public static CrossPlatformPunishmentPreparationWire.Request request(CaseId caseId, CreatePunishmentRequest request) {
        if (caseId == null || request == null) throw new IllegalArgumentException("preparation request is required");
        return new CrossPlatformPunishmentPreparationWire.Request(
                CrossPlatformPunishmentPreparationWire.VERSION, caseId.value(), request.idempotencyKey().value(),
                request.actor().id(), request.actor().displayName(), request.targetId(), request.reasonId(),
                request.internalExplanation());
    }

    public static CrossPlatformPunishmentPreparationWire.PreparedPlan plan(PunishmentPlan plan) {
        if (plan == null) throw new IllegalArgumentException("punishment plan is required");
        return new CrossPlatformPunishmentPreparationWire.PreparedPlan(
                plan.caseId().value(), plan.idempotencyKey().value(), plan.targetId(), plan.actor().id(),
                plan.actor().displayName(), plan.actor().rank(), plan.reasonId(), plan.family(), plan.publicReason(),
                plan.internalExplanation(), plan.configurationVersion(), plan.visibility(), plan.issuedAt().toString(),
                escalation(plan.escalation()), plan.sanctions().stream().map(CrossPlatformPunishmentPreparationMapper::sanction).toList());
    }

    public static PunishmentPlan plan(CrossPlatformPunishmentPreparationWire.PreparedPlan plan) {
        if (plan == null) throw new IllegalArgumentException("prepared plan is required");
        return new PunishmentPlan(new CaseId(plan.caseId()), new IdempotencyKey(plan.idempotencyKey()), plan.targetId(),
                new Actor(plan.actorId(), plan.actorName(), plan.actorRank()), plan.reasonId(), plan.family(),
                plan.publicReason(), plan.internalExplanation(), plan.configurationVersion(), plan.visibility(),
                Instant.parse(plan.issuedAt()), escalation(plan.escalation()),
                plan.sanctions().stream().map(CrossPlatformPunishmentPreparationMapper::sanction).toList());
    }

    private static CrossPlatformPunishmentPreparationWire.Escalation escalation(EscalationDecision value) {
        return new CrossPlatformPunishmentPreparationWire.Escalation(
                value.rawOrdinal(), value.effectiveOrdinal(), value.recencyBonus(),
                value.contributions().stream().map(item -> new CrossPlatformPunishmentPreparationWire.Contribution(
                        item.priorSeverity(), item.base(), item.decayEligibility(), item.decayedBy(), item.effective())).toList(),
                value.resultingOffenseDecayEligibility(), value.selectedStep().ordinal(), value.selectedStep().label(),
                value.selectedStep().sanctions().stream().map(CrossPlatformPunishmentPreparationMapper::sanction).toList());
    }

    private static EscalationDecision escalation(CrossPlatformPunishmentPreparationWire.Escalation value) {
        List<EscalationDecision.Contribution> contributions = value.contributions().stream()
                .map(item -> new EscalationDecision.Contribution(item.priorSeverity(), item.base(),
                        item.decayEligibility(), item.decayedBy(), item.effective())).toList();
        PunishmentStep selected = new PunishmentStep(value.selectedOrdinal(), value.selectedLabel(),
                value.selectedSanctions().stream().map(CrossPlatformPunishmentPreparationMapper::sanction).toList());
        return new EscalationDecision(value.rawOrdinal(), value.effectiveOrdinal(), value.recencyBonus(),
                contributions, value.resultingDecayEligibility(), selected);
    }

    private static CrossPlatformPunishmentPreparationWire.Sanction sanction(SanctionSpec value) {
        return new CrossPlatformPunishmentPreparationWire.Sanction(value.type(), value.length().kind(),
                value.length().temporary().map(Duration::toSeconds).orElse(null));
    }

    private static SanctionSpec sanction(CrossPlatformPunishmentPreparationWire.Sanction value) {
        SanctionLength length = switch (value.lengthKind()) {
            case INSTANT -> SanctionLength.instant();
            case PERMANENT -> SanctionLength.permanent();
            case TEMPORARY -> SanctionLength.temporary(Duration.ofSeconds(value.durationSeconds()));
        };
        return new SanctionSpec(value.type(), length);
    }
}
