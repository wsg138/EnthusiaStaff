package net.enthusia.staff.paper.sanction;

import static org.junit.jupiter.api.Assertions.*;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import net.enthusia.staff.domain.auth.*;
import net.enthusia.staff.domain.sanction.SanctionChangeAction;
import net.enthusia.staff.paper.auth.ActiveDutyAuthorizationPolicy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class SanctionChangeWorkerAuthorizationTest {
    static final UUID ID=UUID.fromString("11111111-2222-3333-4444-555555555555");
    static final SanctionChangeAction ACTION=SanctionChangeAction.END_EARLY;
    @Test void expiredDutyCannotEdit() {
        check(StaffRank.ADMIN,false,true,StaffRank.ADMIN);
    }
    @Test void revokedActionPermissionCannotEdit() {
        check(StaffRank.ADMIN,true,false,StaffRank.ADMIN);
    }
    @Test void demotedActorCannotEdit() {
        check(StaffRank.ADMIN,true,true,StaffRank.HELPER);
    }
    private void check(StaffRank prior,boolean dutyPresent,boolean permissionPresent,StaffRank after) {
        AtomicBoolean duty=new AtomicBoolean(true), permission=new AtomicBoolean(true);
        AtomicReference<StaffRank> rank=new AtomicReference<>(prior);
        AtomicReference<Runnable> queued=new AtomicReference<>();
        var result=SanctionChangeWorkerAuthorization.requestPlayerSnapshot(
            player(queued,rank,permission),plugin(),policy(duty),ACTION);
        duty.set(dutyPresent);permission.set(permissionPresent);rank.set(after);
        queued.get().run();
        assertNull(result.join());
    }
    @Test void validActorCheckedOnlyOnOwningThread() {
        AtomicReference<Runnable> queued=new AtomicReference<>();
        var result=SanctionChangeWorkerAuthorization.requestPlayerSnapshot(
            player(queued,new AtomicReference<>(StaffRank.MOD),new AtomicBoolean(true)),
            plugin(),policy(new AtomicBoolean(true)),ACTION);
        assertFalse(result.isDone());
        queued.get().run();
        assertEquals(StaffRank.MOD,result.join().rank());
    }
    @Test void commandAndGuiRecheckBeforeDurableEdit() throws Exception {
        String typed=Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/command/SanctionChangeCommand.java"));
        String t=typed.substring(typed.indexOf("private void apply("),typed.indexOf("private static CaseId resolveCase("));
        int first=t.indexOf("currentAuthorizedActor(sender, actor, action)");
        int lookup=t.indexOf("resolveCase(target, types, directory, lookup)");
        int last=t.indexOf("currentAuthorizedActor(sender, current, action)");
        int commit=t.indexOf("loadedService.apply(request, mode.get())");
        assertTrue(first>=0 && lookup>first && last>lookup && commit>last);
        assertTrue(t.contains("caseId,") && t.contains("fresh,"));
        String gui=Files.readString(Path.of("src/main/java/net/enthusia/staff/paper/sanction/SanctionChangeGuiController.java"));
        String g=gui.substring(gui.indexOf("private void confirm("),gui.indexOf("private void openState("));
        assertTrue(g.indexOf("SanctionChangeWorkerAuthorization.awaitCurrentActor(")>=0);
        assertTrue(g.indexOf("service.apply(request, mode.get())")>g.indexOf("SanctionChangeWorkerAuthorization.awaitCurrentActor("));
        assertTrue(g.contains("                        fresh,"));
    }
    static AuthorizationPolicy policy(AtomicBoolean duty) {
        return new ActiveDutyAuthorizationPolicy(new DefaultAuthorizationPolicy(),id->ID.equals(id)&&duty.get());
    }
    static Plugin plugin() {
        return (Plugin)Proxy.newProxyInstance(Plugin.class.getClassLoader(),new Class<?>[]{Plugin.class},
            (s,m,a)->{throw new AssertionError(m.getName());});
    }
    static Player player(AtomicReference<Runnable> queue,AtomicReference<StaffRank> rank,AtomicBoolean allowed) {
        EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),
            new Class<?>[]{EntityScheduler.class},(s,m,a)-> {
                if(m.getName().equals("execute")){queue.set((Runnable)a[1]);return true;}
                throw new AssertionError(m.getName());
            });
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
            (s,m,a)->switch(m.getName()) {
                case "getScheduler"->scheduler;
                case "getUniqueId"->ID;
                case "isOnline"->true;
                case "getName"->"Staff";
                case "hasPermission"->{
                    String permission=(String)a[0];
                    if(permission.equals("enthusiastaff.remove.end"))yield allowed.get();
                    yield switch(rank.get()){
                        case ADMIN->permission.equals("enthusiastaff.identity.admin");
                        case MOD->permission.equals("enthusiastaff.identity.mod");
                        case HELPER->permission.equals("enthusiastaff.identity.helper");
                        default->false;
                    };
                }
                default->throw new AssertionError(m.getName());
            });
    }
}
