package net.enthusia.staff.discordbot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Explicit allowlisted DTOs for the internal Enthusia AI moderation-state reader. */
final class AiModerationReadApiModel {
    private AiModerationReadApiModel() {
    }

    record Request(String target) {
        Request {
            if (target == null || !target.matches("^[A-Za-z0-9_]{3,16}$")) {
                throw new IllegalArgumentException("target must be a Minecraft username");
            }
        }
    }

    record TargetDto(String requested, String playerId, Optional<String> username) {
        TargetDto {
            username = username == null ? Optional.empty() : username;
        }
    }

    record ActiveSanctionDto(
            String sanctionId,
            String caseId,
            String type,
            String publicReason,
            Instant issuedAt,
            Optional<Instant> expiresAt
    ) {
        ActiveSanctionDto {
            expiresAt = expiresAt == null ? Optional.empty() : expiresAt;
        }
    }

    record CaseDto(
            String caseId,
            String exactReasonId,
            String sanctionFamily,
            String state,
            String publicReason,
            Instant issuedAt,
            boolean hasActiveSanctions,
            String configurationVersion
    ) {
    }

    record Response(
            String service,
            String api,
            String contractVersion,
            TargetDto target,
            List<ActiveSanctionDto> activeSanctions,
            List<CaseDto> recentCases,
            Instant fetchedAt
    ) {
        Response {
            activeSanctions = List.copyOf(activeSanctions);
            recentCases = List.copyOf(recentCases);
        }
    }

    record ErrorResponse(String code, String message) {
    }
}
