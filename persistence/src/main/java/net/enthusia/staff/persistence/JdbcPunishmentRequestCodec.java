package net.enthusia.staff.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;

final class JdbcPunishmentRequestCodec {
    private static final TypeReference<List<EscalationDecision.Contribution>> CONTRIBUTIONS =
            new TypeReference<>() {
            };

    private final ObjectMapper json;
    private final PunishmentDraftSanctionCodec sanctions;

    JdbcPunishmentRequestCodec(ObjectMapper json) {
        if (json == null) {
            throw new IllegalArgumentException("json mapper must be present");
        }
        this.json = json;
        this.sanctions = new PunishmentDraftSanctionCodec(json);
    }

    String encodeContributions(List<EscalationDecision.Contribution> contributions)
            throws JsonProcessingException {
        return json.writeValueAsString(contributions);
    }

    String encodeSanctions(PunishmentProposal proposal) throws JsonProcessingException {
        return sanctions.encode(proposal.sanctions());
    }

    PunishmentApprovalRequest read(ResultSet result) throws SQLException {
        try {
            PunishmentProposal proposal = proposal(result);
            Timestamp resolvedTimestamp = result.getTimestamp("resolved_at");
            byte[] resolvedBy = result.getBytes("resolved_by");
            String resultingCase = result.getString("resulting_case_id");
            return new PunishmentApprovalRequest(
                    UuidBytes.fromBytes(result.getBytes("request_id")),
                    new IdempotencyKey(result.getString("submission_key")),
                    proposal,
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("expires_at").toInstant(),
                    PunishmentRequestStatus.valueOf(result.getString("status")),
                    result.getLong("revision"),
                    resolvedBy == null ? null : UuidBytes.fromBytes(resolvedBy),
                    result.getString("resolution_note"),
                    resultingCase == null ? null : new CaseId(resultingCase),
                    resolvedTimestamp == null ? null : resolvedTimestamp.toInstant()
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SQLException("Stored punishment request is invalid", exception);
        }
    }

    private PunishmentProposal proposal(ResultSet result) throws SQLException, JsonProcessingException {
        List<net.enthusia.staff.domain.sanction.SanctionSpec> decodedSanctions =
                sanctions.decode(result.getString("sanctions_json"));
        PunishmentStep selected = new PunishmentStep(
                result.getInt("selected_ordinal"),
                result.getString("step_label"),
                decodedSanctions
        );
        EscalationDecision escalation = new EscalationDecision(
                result.getInt("raw_ordinal"),
                result.getInt("effective_ordinal"),
                result.getInt("recency_bonus"),
                json.readValue(result.getString("contribution_json"), CONTRIBUTIONS),
                readDecayEligibility(result),
                selected
        );
        UUID requesterId = UuidBytes.fromBytes(result.getBytes("requester_id"));
        StaffRank requesterRank = StaffRank.valueOf(result.getString("requester_rank"));
        return new PunishmentProposal(
                UuidBytes.fromBytes(result.getBytes("target_id")),
                new Actor(requesterId, result.getString("requester_name"), requesterRank),
                result.getString("reason_id"),
                result.getString("sanction_family"),
                result.getString("public_reason"),
                result.getString("internal_explanation"),
                result.getString("configuration_version"),
                CaseVisibility.valueOf(result.getString("visibility")),
                StaffRank.valueOf(result.getString("required_rank")),
                escalation,
                decodedSanctions
        );
    }

    private static DecayEligibility readDecayEligibility(ResultSet result) throws SQLException {
        Boolean stored = result.getObject("decay_eligible", Boolean.class);
        if (stored == null) {
            return DecayEligibility.UNKNOWN;
        }
        return stored ? DecayEligibility.ELIGIBLE : DecayEligibility.INELIGIBLE;
    }
}
