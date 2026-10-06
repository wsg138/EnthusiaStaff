package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Explicit allowlisted DTOs for the Enthusia AI moderation-state read surface. */
final class AiModerationReadApiModel {
    private AiModerationReadApiModel() {
    }

    record SubjectStateRequest(String minecraftUsername) {
        SubjectStateRequest {
            if (minecraftUsername == null || !minecraftUsername.matches("[A-Za-z0-9_]{3,16}")) {
                throw new IllegalArgumentException("Minecraft username is invalid");
            }
        }
    }

    record SubjectStateResponse(
            String resolution,
            String requestedUsername,
            Optional<String> playerId,
            List<ActiveSanctionDto> activeSanctions,
            List<CaseDto> recentCases,
            Instant observedAt
    ) {
        SubjectStateResponse {
            playerId = playerId == null ? Optional.empty() : playerId;
            activeSanctions = List.copyOf(activeSanctions);
            recentCases = List.copyOf(recentCases);
            if (resolution == null || resolution.isBlank()
                    || requestedUsername == null || requestedUsername.isBlank()
                    || observedAt == null) {
                throw new IllegalArgumentException("AI moderation state fields must be present");
            }
        }
    }

    record ActiveSanctionDto(
            String sanctionId,
            String caseId,
            String type,
            String reason,
            Optional<String> exactReasonId,
            Optional<String> sanctionFamily,
            Instant issuedAt,
            Optional<Instant> expiresAt
    ) {
        ActiveSanctionDto {
            exactReasonId = exactReasonId == null ? Optional.empty() : exactReasonId;
            sanctionFamily = sanctionFamily == null ? Optional.empty() : sanctionFamily;
            expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        }
    }

    record CaseDto(
            String caseId,
            String exactReasonId,
            String sanctionFamily,
            String state,
            Instant issuedAt
    ) {
    }

    record ErrorResponse(String code, String message) {
    }
}
