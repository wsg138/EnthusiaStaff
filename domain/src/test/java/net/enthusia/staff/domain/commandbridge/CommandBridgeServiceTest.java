package net.enthusia.staff.domain.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationSubjectId;
import net.enthusia.staff.domain.ports.CommandBridgeAuditStore;
import net.enthusia.staff.domain.ports.CommandBridgeAuthorityResolver;
import net.enthusia.staff.domain.ports.CommandBridgeExecutor;
import org.junit.jupiter.api.Test;

class CommandBridgeServiceTest {
    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBJECT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final DiscordUserId DISCORD_ID = new DiscordUserId("123456789012345678");
    private static final UUID ACTOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-30T19:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC);
    private static final String TARGET_SERVER = TARGET_SERVER;
    private static final String LIST_COMMAND = LIST_COMMAND;
    private static final CommandBridgeRule LIST_RULE =
            new CommandBridgeRule(LIST_COMMAND, StaffRank.MOD, "enthusia.console.list", 0);

    @Test
    void authorizedRequestExecutesOnceAndReturnsSafeOutput() {
        FakeAudit audit = new FakeAudit();
        AtomicInteger executions = new AtomicInteger();
        CommandBridgeService service = service(
                audit,
                true,
                authorized(StaffRank.ADMIN),
                command -> {
                    executions.incrementAndGet();
                    return CommandBridgeExecutor.Execution.accepted("players=2 password=hunter2");
                }
        );

        CommandBridgeResponse response = service.handle(request(TARGET_SERVER, LIST_COMMAND));

        assertEquals(CommandBridgeOutcome.SUCCESS, response.outcome());
        assertEquals(1, executions.get());
        assertTrue(response.redacted());
        assertFalse(response.output().contains("hunter2"));
        assertEquals(CommandBridgeOutcome.SUCCESS, audit.terminal(REQUEST_ID));
    }

    @Test
    void rejectsUnsupportedCommandInvalidServerUnlinkedAndUnauthorizedActors() {
        assertEquals(CommandBridgeOutcome.UNSUPPORTED_COMMAND,
                service(new FakeAudit(), true, authorized(StaffRank.ADMIN), acceptedExecutor())
                        .handle(request(TARGET_SERVER, "stop")).outcome());
        assertEquals(CommandBridgeOutcome.INVALID_SERVER,
                service(new FakeAudit(), true, authorized(StaffRank.ADMIN), acceptedExecutor())
                        .handle(request("events", LIST_COMMAND)).outcome());
        assertEquals(CommandBridgeOutcome.UNLINKED_ACTOR,
                service(new FakeAudit(), false, authorized(StaffRank.ADMIN), acceptedExecutor())
                        .handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
        assertEquals(CommandBridgeOutcome.UNAUTHORIZED_ACTOR,
                service(new FakeAudit(), true, authorized(StaffRank.MOD, false), acceptedExecutor())
                        .handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
    }

    @Test
    void revokedRankAtMinecraftExecutionIsRejected() {
        AtomicInteger executions = new AtomicInteger();
        CommandBridgeService service = service(
                new FakeAudit(),
                true,
                authorized(StaffRank.HELPER),
                command -> {
                    executions.incrementAndGet();
                    return CommandBridgeExecutor.Execution.accepted("should not run");
                }
        );

        assertEquals(CommandBridgeOutcome.UNAUTHORIZED_ACTOR, service.handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
        assertEquals(0, executions.get());
    }

    @Test
    void executionRejectionAndAmbiguousFailureAreNeverRetried() {
        AtomicInteger rejectedCalls = new AtomicInteger();
        CommandBridgeService rejected = service(new FakeAudit(), true, authorized(StaffRank.MOD), command -> {
            rejectedCalls.incrementAndGet();
            return CommandBridgeExecutor.Execution.rejected();
        });
        assertEquals(CommandBridgeOutcome.EXECUTION_REJECTED, rejected.handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
        assertEquals(1, rejectedCalls.get());

        AtomicInteger ambiguousCalls = new AtomicInteger();
        CommandBridgeService ambiguous = service(new FakeAudit(), true, authorized(StaffRank.MOD), command -> {
            ambiguousCalls.incrementAndGet();
            throw new IllegalStateException("post-dispatch transport state unknown");
        });
        assertEquals(CommandBridgeOutcome.EXECUTION_UNKNOWN, ambiguous.handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
        assertEquals(1, ambiguousCalls.get());
    }

    @Test
    void duplicateConcurrentRequestIdExecutesOnlyOnce() throws Exception {
        FakeAudit audit = new FakeAudit();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        CommandBridgeService service = service(audit, true, authorized(StaffRank.MOD), command -> {
            executions.incrementAndGet();
            entered.countDown();
            await(release);
            return CommandBridgeExecutor.Execution.accepted("ok");
        });

        CompletableFuture<CommandBridgeResponse> first = CompletableFuture.supplyAsync(
                () -> service.handle(request(TARGET_SERVER, LIST_COMMAND)));
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CommandBridgeResponse duplicate = service.handle(request(TARGET_SERVER, LIST_COMMAND));
        release.countDown();

        assertEquals(CommandBridgeOutcome.EXECUTION_UNKNOWN, duplicate.outcome());
        assertEquals(CommandBridgeOutcome.SUCCESS, first.get(2, TimeUnit.SECONDS).outcome());
        assertEquals(1, executions.get());
    }

    @Test
    void restartRecoveryNeverReexecutesUnresolvedClaim() {
        FakeAudit audit = new FakeAudit();
        audit.failCompletions = true;
        AtomicInteger executions = new AtomicInteger();
        CommandBridgeService firstProcess = service(audit, true, authorized(StaffRank.MOD), command -> {
            executions.incrementAndGet();
            return CommandBridgeExecutor.Execution.accepted("ok");
        });

        assertEquals(CommandBridgeOutcome.EXECUTION_UNKNOWN, firstProcess.handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
        audit.failCompletions = false;
        CommandBridgeService restarted = service(audit, true, authorized(StaffRank.MOD), command -> {
            executions.incrementAndGet();
            return CommandBridgeExecutor.Execution.accepted("should not run");
        });

        assertEquals(CommandBridgeOutcome.EXECUTION_UNKNOWN, restarted.handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());
        assertEquals(1, executions.get());
    }

    @Test
    void reusedRequestIdWithChangedBodyIsRejected() {
        FakeAudit audit = new FakeAudit();
        CommandBridgeService service = service(audit, true, authorized(StaffRank.MOD), acceptedExecutor());
        assertEquals(CommandBridgeOutcome.SUCCESS, service.handle(request(TARGET_SERVER, LIST_COMMAND)).outcome());

        CommandBridgeRequest changed = new CommandBridgeRequest(
                REQUEST_ID,
                new ModerationSubjectId(SUBJECT_ID),
                DISCORD_ID,
                ACTOR_ID,
                TARGET_SERVER,
                "LIST ",
                NOW.plusSeconds(1)
        );
        assertEquals(CommandBridgeOutcome.REQUEST_ID_CONFLICT, service.handle(changed).outcome());
    }

    private static CommandBridgeService service(
            FakeAudit audit,
            boolean linked,
            CommandBridgeAuthorityResolver authority,
            CommandBridgeExecutor executor
    ) {
        return new CommandBridgeService(
                new CommandBridgePolicy(Set.of(TARGET_SERVER), List.of(LIST_RULE)),
                (subject, discord, actor) -> linked,
                authority,
                audit,
                executor,
                new CommandBridgeOutputSanitizer(),
                CLOCK
        );
    }

    private static CommandBridgeAuthorityResolver authorized(StaffRank rank) {
        return authorized(rank, true);
    }

    private static CommandBridgeAuthorityResolver authorized(StaffRank rank, boolean permission) {
        return (actor, requiredPermission) -> Optional.of(new CommandBridgeAuthorityResolver.Snapshot(rank, permission));
    }

    private static CommandBridgeExecutor acceptedExecutor() {
        return command -> CommandBridgeExecutor.Execution.accepted("ok");
    }

    private static CommandBridgeRequest request(String server, String command) {
        return new CommandBridgeRequest(
                REQUEST_ID,
                new ModerationSubjectId(SUBJECT_ID),
                DISCORD_ID,
                ACTOR_ID,
                server,
                command,
                NOW
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", failure);
        }
    }

    private static final class FakeAudit implements CommandBridgeAuditStore {
        private final Map<UUID, Entry> requests = new ConcurrentHashMap<>();
        private volatile boolean failCompletions;

        @Override
        public Claim claim(RequestAudit request) {
            Entry created = new Entry(request.requestFingerprint());
            Entry existing = requests.putIfAbsent(request.requestId(), created);
            if (existing == null) {
                return Claim.claimed();
            }
            if (!existing.fingerprint.equals(request.requestFingerprint())) {
                return Claim.conflict();
            }
            CommandBridgeOutcome terminal = existing.terminal;
            return terminal == null ? Claim.unresolved() : Claim.terminal(terminal);
        }

        @Override
        public void complete(UUID requestId, CommandBridgeOutcome outcome, Instant completedAt) {
            if (failCompletions) {
                throw new IllegalStateException("simulated persistence failure");
            }
            Entry entry = requests.get(requestId);
            if (entry == null) {
                throw new IllegalStateException("request was not claimed");
            }
            entry.terminal = outcome;
        }

        CommandBridgeOutcome terminal(UUID requestId) {
            Entry entry = requests.get(requestId);
            return entry == null ? null : entry.terminal;
        }

        private static final class Entry {
            private final String fingerprint;
            private volatile CommandBridgeOutcome terminal;

            private Entry(String fingerprint) {
                this.fingerprint = fingerprint;
            }
        }
    }
}
