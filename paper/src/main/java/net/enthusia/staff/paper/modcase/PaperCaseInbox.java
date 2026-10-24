package net.enthusia.staff.paper.modcase;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;
import net.enthusia.staff.paper.staff.StaffModeManager;
import org.bukkit.entity.Player;

/** Read-only backing for a future inventory/list. Invoke on the viewer's Folia entity thread. */
public final class PaperCaseInbox {
    private final CaseLedger ledger;
    private final StaffModeManager staffMode;
    private final Clock clock;
    private final Predicate<CaseNotice> scopeAccess;

    public PaperCaseInbox(CaseLedger ledger, StaffModeManager staffMode, Clock clock,
                          Predicate<CaseNotice> scopeAccess) {
        this.ledger = Objects.requireNonNull(ledger);
        this.staffMode = Objects.requireNonNull(staffMode);
        this.clock = Objects.requireNonNull(clock);
        this.scopeAccess = Objects.requireNonNull(scopeAccess);
    }

    public List<CaseNotice> list(Player player, int limit) {
        if (player == null || !player.isOnline()
                || !player.hasPermission("enthusiastaff.ai-review.queue")
                || !staffMode.authorityActiveOrUnrestricted(player.getUniqueId())) {
            return List.of();
        }
        return ledger.active(clock.instant(), limit).stream()
                .filter(scopeAccess).toList();
    }
}
