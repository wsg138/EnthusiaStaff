package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.ports.StaffSessionStore;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException;
import net.enthusia.staff.domain.staff.StaffSessionInspectionException.Reason;
import net.enthusia.staff.domain.staff.StaffSessionInspection;
import net.enthusia.staff.domain.staff.StaffSessionState;
import net.enthusia.staff.paper.staff.StaffStateCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

class StaffSessionInspectionCommandTest {
    private static final String INSPECT = "inspect";
    private static final UUID TARGET = UUID.fromString("74000000-0000-0000-0000-000000000031");

    @Test
    void consoleCanInspectOfflineExactUuidWithoutPlayerOrRecoveryDependency() throws Exception {
        Fixture fixture = new Fixture();
        fixture.snapshot = snapshot("__network_detached__", "HUB");
        fixture.invoke(INSPECT, TARGET.toString());
        assertEquals(0, fixture.reads, "database must not run on command thread");
        fixture.work.removeFirst().run();
        assertTrue(fixture.output.isEmpty(), "reply must use the reply scheduler");
        fixture.replies.removeFirst().run();
        String output = fixture.output.getFirst();
        assertTrue(output.contains("status=METADATA_VERIFIED"));
        assertTrue(output.contains("owner_backend=__network_detached__ snapshot_backend=HUB"));
        assertTrue(output.contains("SHA256_AND_HEADER_ONLY"));
        assertTrue(output.contains("recovery=NOT_AUTHORIZED"));
        assertFalse(output.contains("SECRET_ITEM_PAYLOAD"));
        assertEquals(1, fixture.reads);
    }

    @Test
    void playersCannotInspectEvenWithPermission() {
        Fixture fixture = new Fixture();
        CommandSender playerLike = sender(CommandSender.class, fixture.output);
        fixture.command.onCommand(playerLike, null, "staffsession", new String[]{INSPECT, TARGET.toString()});
        assertTrue(fixture.work.isEmpty());
        assertEquals(0, fixture.reads);
    }

    @Test
    void invalidOrAbbreviatedUuidNeverQueries() {
        Fixture fixture = new Fixture();
        for (String target : List.of("PlayerName", "1-1-1-1-1", TARGET + " extra")) {
            fixture.invoke(INSPECT, target);
        }
        fixture.invoke("recover", TARGET.toString());
        assertTrue(fixture.work.isEmpty());
    }

    @Test
    void overloadIsRejectedAndProviderFailureLeaksNoDetails() {
        Fixture fixture = new Fixture();
        fixture.failure = new IllegalStateException("jdbc://private-host password=secret");
        fixture.invoke(INSPECT, TARGET.toString());
        fixture.invoke(INSPECT, TARGET.toString());
        assertEquals(1, fixture.work.size());
        assertTrue(fixture.output.getFirst().contains("BUSY"));
        fixture.finish();
        assertTrue(fixture.output.getLast().contains("UNAVAILABLE"));
        assertFalse(fixture.output.toString().contains("password"));
        fixture.invoke(INSPECT, TARGET.toString());
        assertEquals(1, fixture.work.size(), "failure must release the inspection fence");
    }

    @Test
    void absenceAndInvalidEvidenceRemainDistinct() {
        Fixture fixture = new Fixture();
        fixture.invoke(INSPECT, TARGET.toString()); fixture.finish();
        assertTrue(fixture.output.getLast().contains("NO_ACTIVE_SESSION"));
        fixture.failure = new StaffSessionInspectionException(Reason.MISSING_SNAPSHOT);
        fixture.invoke(INSPECT, TARGET.toString()); fixture.finish();
        assertTrue(fixture.output.getLast().contains("status=INVALID reason=MISSING_SNAPSHOT"));
    }

    @Test
    void mismatchedOwnerHeaderIsNotReportedVerified() throws Exception {
        Fixture fixture = new Fixture();
        fixture.snapshot = snapshot("TEST", "HUB");
        fixture.invoke(INSPECT, TARGET.toString()); fixture.finish();
        assertTrue(fixture.output.getLast().contains("status=INVALID"));
        assertFalse(fixture.output.getLast().contains("METADATA_VERIFIED"));
    }

    @Test
    void rejectedWorkerDoesNotQueryAndDoesNotLeaveBusyFence() {
        Fixture fixture = new Fixture();
        StaffSessionInspectionCommand rejected = new StaffSessionInspectionCommand(() -> fixture.store,
                task -> { throw new java.util.concurrent.RejectedExecutionException("bounded queue full"); },
                Runnable::run, Clock.systemUTC(), "TEST");
        String[] arguments = {INSPECT, TARGET.toString()};
        for (int invocation = 0; invocation < 2; invocation++) {
            rejected.onCommand(fixture.console, null, "staffsession", arguments);
        }
        assertEquals(0, fixture.reads);
        assertEquals(2, fixture.output.size());
        assertTrue(fixture.output.stream().allMatch(line -> line.contains("UNAVAILABLE")));
    }

    private static StaffSessionInspection snapshot(String owner, String source) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x45535331); out.writeInt(1); out.writeUTF(source);
            out.writeUTF("SECRET_ITEM_PAYLOAD");
        }
        byte[] payload = bytes.toByteArray();
        return new StaffSessionInspection(UUID.randomUUID(), TARGET, owner, StaffSessionState.ACTIVE,
                3, Instant.EPOCH, 1, payload.length, new StaffStateCodec().checksum(payload), payload);
    }

    private static final class Fixture {
        final ArrayDeque<Runnable> work = new ArrayDeque<>();
        final ArrayDeque<Runnable> replies = new ArrayDeque<>();
        final List<String> output = new ArrayList<>();
        final ConsoleCommandSender console = sender(ConsoleCommandSender.class, output);
        StaffSessionInspection snapshot;
        RuntimeException failure;
        int reads;
        final StaffSessionStore store = (StaffSessionStore) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(), new Class<?>[]{StaffSessionStore.class},
                (proxy, method, args) -> {
                    assertEquals(INSPECT, method.getName(), "no mutation or ordinary lookup allowed");
                    assertEquals(TARGET, args[0]); reads++;
                    if (failure != null) throw failure;
                    return Optional.ofNullable(snapshot);
                });
        final StaffSessionInspectionCommand command = new StaffSessionInspectionCommand(() -> store,
                work::addLast, replies::addLast, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "TEST");

        void invoke(String... args) { command.onCommand(console, null, "staffsession", args); }
        void finish() { work.removeFirst().run(); replies.removeFirst().run(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T sender(Class<T> type, List<String> output) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("sendMessage") && args[0] instanceof Component component) {
                output.add(PlainTextComponentSerializer.plainText().serialize(component)); return null;
            }
            if (method.getName().equals("hasPermission")) return true;
            throw new AssertionError(method.getName());
        });
    }
}
