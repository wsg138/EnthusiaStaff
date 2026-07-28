package net.enthusia.staff.paper.sanction;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class SanctionChangeAccess {
    public static final List<String> CENTRAL_ACTIONS = List.of(
            "end", "reduce", "replace-expiration", "revoke", "full-overturn",
            "remove-escalation", "restore-escalation", "request-overturn",
            "approve-overturn", "deny-overturn"
    );

    private static final Set<SanctionType> ALL_TYPES = Set.copyOf(EnumSet.allOf(SanctionType.class));

    private SanctionChangeAccess() {
    }

    public static SanctionChangeAction aliasAction(String label) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "unban", "unmute" -> SanctionChangeAction.END_EARLY;
            case "removewarning", "unwarn" -> SanctionChangeAction.REVOKE;
            default -> null;
        };
    }

    public static Set<SanctionType> aliasTypes(String label) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "unban" -> Set.of(
                    SanctionType.BAN, SanctionType.NETWORK_BAN, SanctionType.NETWORK_IDENTITY_BAN
            );
            case "unmute" -> Set.of(SanctionType.MUTE);
            case "removewarning", "unwarn" -> Set.of(SanctionType.WARNING);
            default -> ALL_TYPES;
        };
    }

    public static SanctionChangeAction parseAction(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "end", "end-early" -> SanctionChangeAction.END_EARLY;
            case "reduce" -> SanctionChangeAction.REDUCE_DURATION;
            case "expiration", "replace-expiration" -> SanctionChangeAction.REPLACE_EXPIRATION;
            case "revoke" -> SanctionChangeAction.REVOKE;
            case "overturn", "full-overturn" -> SanctionChangeAction.FULL_OVERTURN;
            case "remove-escalation" -> SanctionChangeAction.REMOVE_ESCALATION_CONTRIBUTION;
            case "restore-escalation" -> SanctionChangeAction.RESTORE_ESCALATION_CONTRIBUTION;
            case "request-overturn" -> SanctionChangeAction.REQUEST_FULL_OVERTURN;
            case "approve-overturn" -> SanctionChangeAction.APPROVE_FULL_OVERTURN;
            case "deny-overturn" -> SanctionChangeAction.DENY_FULL_OVERTURN;
            default -> null;
        };
    }

    public static boolean canChangeAnything(AuthorizationPolicy authorization, Actor actor) {
        return Arrays.stream(SanctionChangeAction.values())
                .anyMatch(action -> authorization.permits(actor, action.requiredModerationAction()));
    }

    public static String permissionFor(SanctionChangeAction action) {
        return switch (action) {
            case END_EARLY -> "enthusiastaff.remove.end";
            case REVOKE -> "enthusiastaff.remove.revoke";
            case REDUCE_DURATION, REMOVE_ESCALATION_CONTRIBUTION -> "enthusiastaff.remove.lower";
            case REPLACE_EXPIRATION -> "enthusiastaff.remove.custom-duration";
            case RESTORE_ESCALATION_CONTRIBUTION -> "enthusiastaff.remove.raise";
            case FULL_OVERTURN -> "enthusiastaff.remove.full-overturn";
            case REQUEST_FULL_OVERTURN -> "enthusiastaff.remove.request-overturn";
            case APPROVE_FULL_OVERTURN, DENY_FULL_OVERTURN -> "enthusiastaff.remove.approve-overturn";
        };
    }
}
