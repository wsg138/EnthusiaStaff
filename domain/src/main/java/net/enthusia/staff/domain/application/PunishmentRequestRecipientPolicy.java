package net.enthusia.staff.domain.application;

import java.util.Objects;
import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.StaffRank;

public final class PunishmentRequestRecipientPolicy {
    public boolean mayReceiveReviewerAlert(Actor recipient, PunishmentApprovalRequest request) {
        if (request == null) {
            return false;
        }
        return mayReceiveReviewerAlert(
                recipient,
                request.proposal().requester().id(),
                request.proposal().requiredRank()
        );
    }

    public boolean mayReceiveReviewerAlert(Actor recipient, UUID requesterId, StaffRank minimumRank) {
        if (recipient == null || requesterId == null || minimumRank == null) {
            return false;
        }
        return !recipient.id().equals(requesterId)
                && recipient.rank().canApprovePunishmentRequests()
                && meetsMinimumRank(recipient.rank(), minimumRank);
    }

    public boolean mayReceiveOperationalAlert(Actor recipient) {
        return recipient != null
                && (recipient.rank() == StaffRank.ADMIN || recipient.rank() == StaffRank.FOUNDER);
    }

    public UUID requester(PunishmentApprovalRequest request) {
        Objects.requireNonNull(request, "request");
        return request.proposal().requester().id();
    }

    private static boolean meetsMinimumRank(StaffRank recipient, StaffRank required) {
        return switch (required) {
            case HELPER, MOD -> recipient.canApprovePunishmentRequests();
            case ADMIN -> recipient == StaffRank.ADMIN || recipient == StaffRank.FOUNDER;
            case FOUNDER -> recipient == StaffRank.FOUNDER;
            case DEVELOPER, SYSTEM -> false;
        };
    }
}
