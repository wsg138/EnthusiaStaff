package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.ActiveDutyAuthorizationPolicy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Actual entity-scheduler handoff tests plus the command-worker decision contract. */
class PunishmentRequestCommandAuthorityTest {
    private static final UUID STAFF = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void staffModeExitBetweenCommandAndWorkerOwnerCheckFailsClosed() {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicReference<Runnable> dispatched = new AtomicReference<>();
        AuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy(
                new DefaultAuthorizationPolicy(), id -> STAFF.equals(id) && active.get());
        Player player = player(dispatched, new AtomicReference<>(StaffRank.ADMIN));
        CompletableFuture<Actor> result = PunishmentRequestCommandHandler.requestReviewerSnapshot(
                player, plugin(), policy);
        active.set(false);
        dispatched.get().run();
        assertNull(result.join());
    }

    @Test
    void rankDemotionBetweenCommandAndWorkerOwnerCheckUsesNewRank() {
        AtomicReference<Runnable> dispatched = new AtomicReference<>();
        AtomicReference<StaffRank> rank = new AtomicReference<>(StaffRank.ADMIN);
        AuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy(
                new DefaultAuthorizationPolicy(), id -> STAFF.equals(id));
        CompletableFuture<Actor> result = PunishmentRequestCommandHandler.requestReviewerSnapshot(
                player(dispatched, rank), plugin(), policy);
        rank.set(StaffRank.HELPER);
        dispatched.get().run();
        assertNull(result.join(), "Helper cannot approve punishment requests");
    }

    @Test
    void activeApproverUsesCurrentEntityThreadSnapshot() {
        AtomicReference<Runnable> dispatched = new AtomicReference<>();
        AtomicReference<StaffRank> rank = new AtomicReference<>(StaffRank.ADMIN);
        AuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy(
                new DefaultAuthorizationPolicy(), id -> STAFF.equals(id));
        CompletableFuture<Actor> result = PunishmentRequestCommandHandler.requestReviewerSnapshot(
                player(dispatched, rank), plugin(), policy);
        rank.set(StaffRank.MOD);
        dispatched.get().run();
        assertEquals(StaffRank.MOD, result.join().rank());
    }

    @Test
    void commandWorkerRechecksAfterLeaseAndAbandonsOnRevocation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/PunishmentRequestCommandHandler.java"));
        String operation = source.substring(source.indexOf("private void decideStored("),
                source.indexOf("private PunishmentRequestService readyService("));
        int first = operation.indexOf("currentDecisionActor(sender, actor)");
        int acquire = operation.indexOf("service.acquire(requestId, current)");
        int second = operation.indexOf("currentDecisionActor(sender, current)");
        int decide = operation.indexOf("decideLeased(service, leased, fresh, approve, denialNote)");
        assertTrue(first >= 0 && acquire > first && second > acquire && decide > second,
                "No request lease/decision may use only an actor captured at command admission");
        assertTrue(operation.contains("service.abandon(leased.lease())"),
                "If reauthorization fails after acquire, return the lease for other reviewers");
        assertFalse(operation.contains("decideLeased(service, leased, actor, approve, denialNote)"));
    }

    @Test
    void typedApprovalChecksCurrentTargetHierarchyAfterLeaseBeforeDurableDecision() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/command/PunishmentRequestCommandHandler.java"));
        String operation = source.substring(source.indexOf("private void decideStored("),
                source.indexOf("private PunishmentRequestService readyService("));
        int current = operation.indexOf("currentDecisionActor(sender, current)");
        int target = operation.indexOf("decisionTargetCheck(");
        int decision = operation.indexOf("decideLeased(service, leased, fresh, approve, denialNote)");
        assertTrue(current >= 0 && target > current && decision > target,
                "Typed approve and deny must recheck current target hierarchy after the lease");
        assertTrue(operation.contains("abandonLeaseSafely(service, leased)"),
                "A newly protected target must leave the request pending and release the claim");
        assertTrue(source.contains("LuckPermsStaffTargetGuard.discover(plugin)"),
                "Production command constructor must not silently use a permissive guard");
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (instance, method, args) -> { throw new AssertionError(method.getName()); });
    }

    private static Player player(AtomicReference<Runnable> dispatched, AtomicReference<StaffRank> rank) {
        EntityScheduler scheduler = (EntityScheduler) Proxy.newProxyInstance(
                EntityScheduler.class.getClassLoader(), new Class<?>[]{EntityScheduler.class},
                (instance, method, args) -> {
                    if (method.getName().equals("execute")) {
                        dispatched.set((Runnable) args[1]);
                        return true;
                    }
                    throw new AssertionError(method.getName());
                });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (instance, method, args) -> switch (method.getName()) {
                    case "getScheduler" -> scheduler;
                    case "getUniqueId" -> STAFF;
                    case "getName" -> "Reviewer";
                    case "isOnline" -> true;
                    case "hasPermission" -> {
                        String permission = (String) args[0];
                        yield switch (rank.get()) {
                            case ADMIN -> permission.equals("enthusiastaff.identity.admin");
                            case MOD -> permission.equals("enthusiastaff.identity.mod");
                            case HELPER -> permission.equals("enthusiastaff.identity.helper");
                            default -> false;
                        };
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }
}
