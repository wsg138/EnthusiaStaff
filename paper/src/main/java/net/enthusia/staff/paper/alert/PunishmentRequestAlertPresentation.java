package net.enthusia.staff.paper.alert;

import java.util.Objects;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.kyori.adventure.text.Component;

public record PunishmentRequestAlertPresentation(
        PunishmentRequestAlertClaim claim,
        PunishmentApprovalRequest request,
        Component message
) {
    public PunishmentRequestAlertPresentation {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(message, "message");
    }
}
