package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.paper.auth.ActiveDutyAuthorizationPolicy;
import org.junit.jupiter.api.Test;

class PunishmentGuiWorkerAuthorityTest {
    private static final UUID STAFF_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void queuedWorkerMustRecheckStaffModeAndActorIdentity() throws Exception {
        AtomicBoolean onDuty = new AtomicBoolean(true);
        AuthorizationPolicy policy = new ActiveDutyAuthorizationPolicy(
                new DefaultAuthorizationPolicy(), ignored -> onDuty.get());
        Actor actor = new Actor(STAFF_ID, "Moderator", StaffRank.MOD);

        assertTrue(check(policy, actor, STAFF_ID), "Active moderator can work");
        assertTrue(check(policy, new Actor(STAFF_ID, "Developer", StaffRank.DEVELOPER), STAFF_ID),
                "Request-only developers retain the approved request route while on duty");
        onDuty.set(false);
        assertFalse(check(policy, actor, STAFF_ID),
                "Staff Mode disabled after GUI click must prevent worker mutation");
        onDuty.set(true);
        assertFalse(check(policy, actor, OTHER_ID),
                "An actor captured for another viewer must not be reused");
        assertFalse(check(policy, null, STAFF_ID),
                "Missing actor must fail closed");
    }

    @Test
    void allQueuedGuiMutationPathsRecheckDutyBeforePersistence() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/punishment/PunishmentGuiController.java"));
        assertWorkerCheck(source, "private void evaluateGuided(", "private static String guidedExplanation(");
        assertWorkerCheck(source, "private void prepare(", "private static final String GUIDED_AUDIT_PREFIX");
        assertWorkerCheck(source, "private void reprepare(", "private void showPrepared(");
        assertWorkerCheck(source, "private void confirmOnce(", "private Optional<PunishmentDraftConfirmation> confirmDraft(");
    }

    private static void assertWorkerCheck(String source, String start, String end) {
        int from = source.indexOf(start);
        int until = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && until > from, "Worker section must exist: " + start);
        String section = source.substring(from, until);
        assertTrue(section.contains("workerAuthorityActive(viewer, actor)"),
                "Queued mutation must recheck current duty: " + start);
    }

    private static boolean check(AuthorizationPolicy policy, Actor actor, UUID viewerId)
            throws Exception {
        Method method = PunishmentGuiController.class.getDeclaredMethod(
                "currentWorkerAuthority", AuthorizationPolicy.class, Actor.class, UUID.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, policy, actor, viewerId);
    }
}
