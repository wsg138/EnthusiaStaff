package net.enthusia.staff.protocol;

import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.application.CreatePunishmentRequest;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.application.PunishmentResult;

/** Conversion between the D08 commit wire document and authoritative domain values. */
public final class MinecraftPunishmentCommitMapper {
    private MinecraftPunishmentCommitMapper() {
    }

    public static MinecraftPunishmentCommitWire.Request request(
            CaseId caseId,
            CreatePunishmentRequest request,
            PunishmentExpectation expectation
    ) {
        if (expectation == null) {
            throw new IllegalArgumentException("punishment expectation must be present");
        }
        return new MinecraftPunishmentCommitWire.Request(
                MinecraftPunishmentCommitWire.VERSION,
                MinecraftPunishmentPreparationMapper.request(caseId, request),
                expectation(expectation)
        );
    }

    public static PunishmentExpectation expectation(MinecraftPunishmentCommitWire.Expectation value) {
        if (value == null) {
            throw new IllegalArgumentException("commit expectation must be present");
        }
        return new PunishmentExpectation(
                value.configurationVersion(), value.stepOrdinal(), value.stepLabel(),
                value.sanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }

    public static MinecraftPunishmentCommitWire.Response response(PunishmentResult result) {
        if (result instanceof PunishmentResult.Accepted accepted) {
            return MinecraftPunishmentCommitWire.Response.accepted(
                    accepted.caseId().value(), accepted.replayed()
            );
        }
        PunishmentResult.Rejected rejected = (PunishmentResult.Rejected) result;
        return MinecraftPunishmentCommitWire.Response.rejected(rejected.code(), rejected.message());
    }

    public static PunishmentResult result(MinecraftPunishmentCommitWire.Response response) {
        if (response == null) {
            throw new IllegalArgumentException("commit response must be present");
        }
        if (response.outcome() == MinecraftPunishmentCommitWire.Outcome.REJECTED) {
            return new PunishmentResult.Rejected(response.code(), response.message());
        }
        return new PunishmentResult.Accepted(
                new CaseId(response.accepted().caseId()), response.accepted().replayed()
        );
    }

    private static MinecraftPunishmentCommitWire.Expectation expectation(PunishmentExpectation value) {
        return new MinecraftPunishmentCommitWire.Expectation(
                value.configurationVersion(), value.stepOrdinal(), value.stepLabel(),
                value.sanctions().stream().map(MinecraftPunishmentPreparationMapper::sanction).toList()
        );
    }
}
