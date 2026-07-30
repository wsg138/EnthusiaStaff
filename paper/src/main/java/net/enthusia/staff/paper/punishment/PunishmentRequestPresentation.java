package net.enthusia.staff.paper.punishment;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.kyori.adventure.text.format.NamedTextColor;

public final class PunishmentRequestPresentation {
    private PunishmentRequestPresentation() {
    }

    public static String targetName(PlayerDirectory directory, UUID targetId) {
        if (directory == null || targetId == null) {
            return "Offline player (name unavailable)";
        }
        return directory.find(targetId.toString())
                .flatMap(PlayerIdentity::currentUsername)
                .filter(name -> !name.isBlank())
                .orElse("Offline player (name unavailable)");
    }

    public static String sanctions(List<SanctionSpec> sanctions) {
        if (sanctions == null || sanctions.isEmpty()) {
            return "no sanction";
        }
        return sanctions.stream()
                .map(PunishmentRequestPresentation::sanction)
                .collect(Collectors.joining(", "));
    }

    public static String status(PunishmentRequestStatus status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status) {
            case PENDING -> "pending";
            case APPROVED -> "approved";
            case DENIED -> "denied";
            case EXPIRED -> "expired";
            case FULFILLED_EXTERNALLY -> "externally fulfilled";
        };
    }

    public static NamedTextColor statusColor(PunishmentRequestStatus status) {
        if (status == null) {
            return NamedTextColor.GRAY;
        }
        return switch (status) {
            case PENDING -> NamedTextColor.AQUA;
            case APPROVED, FULFILLED_EXTERNALLY -> NamedTextColor.GREEN;
            case DENIED -> NamedTextColor.YELLOW;
            case EXPIRED -> NamedTextColor.RED;
        };
    }

    public static String resolution(PunishmentApprovalRequest request) {
        if (request == null) {
            return "Request details unavailable";
        }
        return switch (request.status()) {
            case PENDING -> "Awaiting an authorized reviewer";
            case APPROVED -> caseResolution(request, "Approved", "Approved as case ");
            case FULFILLED_EXTERNALLY -> caseResolution(
                    request,
                    "Fulfilled by another authoritative punishment",
                    "Fulfilled by case "
            );
            case DENIED, EXPIRED -> noteResolution(request);
        };
    }

    private static String caseResolution(
            PunishmentApprovalRequest request,
            String fallback,
            String casePrefix
    ) {
        return request.resultingCaseId() == null
                ? fallback
                : casePrefix + request.resultingCaseId().value();
    }

    private static String noteResolution(PunishmentApprovalRequest request) {
        String note = request.resolutionNote();
        return note == null || note.isBlank() ? status(request.status()) : note;
    }

    private static String sanction(SanctionSpec specification) {
        String type = specification.type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (specification.length().isPermanent()) {
            return type + " permanent";
        }
        if (specification.length().isInstant()) {
            return type;
        }
        return type + ' ' + humanDuration(specification.length().temporary().orElseThrow());
    }

    private static String humanDuration(Duration duration) {
        if (duration.toDays() > 0 && duration.minusDays(duration.toDays()).isZero()) {
            return duration.toDays() + "d";
        }
        if (duration.toHours() > 0 && duration.minusHours(duration.toHours()).isZero()) {
            return duration.toHours() + "h";
        }
        if (duration.toMinutes() > 0 && duration.minusMinutes(duration.toMinutes()).isZero()) {
            return duration.toMinutes() + "m";
        }
        return duration.toSeconds() + "s";
    }
}
