package net.enthusia.staff.paper.punishment;

import java.util.Objects;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import org.bukkit.plugin.Plugin;

/**
 * Common post-commit public announcement for GUI, command and web approvals.
 * Never announce pending requests, private outcomes, or idempotent replays.
 */
public final class PunishmentApprovalAnnouncement {
    private PunishmentApprovalAnnouncement() {
    }

    public static void publish(
            Plugin plugin,
            PlayerDirectory players,
            PunishmentRequestResult.Approved approved
    ) {
        Objects.requireNonNull(approved, "approved request");
        var proposal = approved.request().proposal();
        if (approved.replayed() || proposal.visibility() != CaseVisibility.PUBLIC) {
            return;
        }
        PunishmentPublicAnnouncement.publish(
                plugin, proposal.visibility(), approved.replayed(),
                PunishmentRequestPresentation.targetName(players, proposal.targetId()),
                proposal.sanctions(), proposal.publicReason()
        );
    }
}
