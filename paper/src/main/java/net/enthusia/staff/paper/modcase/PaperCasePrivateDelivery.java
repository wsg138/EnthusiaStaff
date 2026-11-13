package net.enthusia.staff.paper.modcase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseDispatchWorker;
import net.enthusia.staff.domain.modcase.CaseNotice;
import net.enthusia.staff.paper.staff.StaffModeManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Dormant Folia-safe private notice; checks permission and active staff duty on each entity. */
public final class PaperCasePrivateDelivery implements CaseDispatchWorker.Destination {
    private static final String PERMISSION = "enthusiastaff.ai-review.queue";
    private final JavaPlugin plugin;
    private final StaffModeManager staffMode;
    private final Predicate<CaseNotice> exactScope;

    public PaperCasePrivateDelivery(JavaPlugin plugin, StaffModeManager staffMode,
                                    Predicate<CaseNotice> exactScope) {
        this.plugin = Objects.requireNonNull(plugin);
        this.staffMode = Objects.requireNonNull(staffMode);
        this.exactScope = Objects.requireNonNull(exactScope);
    }

    @Override
    public CompletionStage<CaseDispatchWorker.Result> send(CaseNotice notice, CaseDelivery.Key key) {
        if (!notice.caseId().equals(key.caseId()) || notice.origin() != key.origin()
                || !notice.scope().equals(key.scope()) || notice.revision() != key.revision()
                || !exactScope.test(notice)) {
            return CompletableFuture.completedFuture(CaseDispatchWorker.Result.notSent("SCOPE_DENIED"));
        }
        CompletableFuture<CaseDispatchWorker.Result> outcome = new CompletableFuture<>();
        try {
            plugin.getServer().getGlobalRegionScheduler().execute(plugin,
                    () -> sendToOnlineStaff(notice, key, outcome));
        } catch (RuntimeException error) {
            outcome.complete(CaseDispatchWorker.Result.notSent("SCHEDULER_UNAVAILABLE"));
        }
        return outcome;
    }

    private void sendToOnlineStaff(CaseNotice notice, CaseDelivery.Key key,
                                   CompletableFuture<CaseDispatchWorker.Result> outcome) {
        List<CompletableFuture<Boolean>> receipts = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            receipts.add(sendOnEntityScheduler(player, notice));
        }
        if (receipts.isEmpty()) {
            outcome.complete(CaseDispatchWorker.Result.notSent("STAFF_OFFLINE"));
            return;
        }
        CompletableFuture.allOf(receipts.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> complete(outcome, receipts, key, error));
    }

    private CompletableFuture<Boolean> sendOnEntityScheduler(Player player, CaseNotice notice) {
        CompletableFuture<Boolean> receipt = new CompletableFuture<>();
        try {
            var scheduled = player.getScheduler().run(plugin, ignored -> {
                try {
                    if (!player.isOnline() || !player.hasPermission(PERMISSION)
                            || !staffMode.authorityActiveOrUnrestricted(player.getUniqueId())) {
                        receipt.complete(false);
                        return;
                    }
                    player.sendMessage(message(notice));
                    receipt.complete(true);
                } catch (RuntimeException error) {
                    receipt.completeExceptionally(error);
                }
            }, () -> receipt.complete(false));
            if (scheduled == null) {
                receipt.complete(false);
            }
        } catch (RuntimeException error) {
            receipt.completeExceptionally(error);
        }
        return receipt;
    }

    private static void complete(CompletableFuture<CaseDispatchWorker.Result> outcome,
                                 List<CompletableFuture<Boolean>> receipts,
                                 CaseDelivery.Key key, Throwable error) {
        if (error != null) {
            outcome.complete(CaseDispatchWorker.Result.possiblySent("STAFF_SEND_UNCERTAIN"));
        } else if (receipts.stream().anyMatch(CompletableFuture::join)) {
            outcome.complete(CaseDispatchWorker.Result.confirmed(
                    "paper:send-called:" + key.caseId() + ":" + key.revision()));
        } else {
            outcome.complete(CaseDispatchWorker.Result.notSent("NO_AUTHORIZED_STAFF_ONLINE"));
        }
    }

    public static String message(CaseNotice notice) {
        return "[AI Review] " + notice.safeTitle() + " · Case " + notice.caseId()
                + " · Revision " + notice.revision() + " · " + notice.state()
                + ". Open private review; no automatic punishment.";
    }
}
