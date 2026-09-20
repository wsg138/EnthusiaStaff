package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.common.SecureIdentifiers;
import net.enthusia.staff.domain.application.PunishmentApprovalLease;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentProposal;
import net.enthusia.staff.domain.application.PunishmentRequestResult;
import net.enthusia.staff.domain.application.PunishmentRequestService;
import net.enthusia.staff.domain.application.PunishmentRequestStatus;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.domain.ports.PunishmentRequestStore;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PunishmentRequestControllerSchedulingIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("62000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("62000000-0000-0000-0000-000000000002");
    private static final UUID REQUESTER_ID = UUID.fromString("62000000-0000-0000-0000-000000000003");
    private static final UUID REVIEWER_ID = UUID.fromString("62000000-0000-0000-0000-000000000004");

    @Test
    void queueCompletionSchedulesTheRealPresentationOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD);

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.store().pendingReads);
        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.scheduler().heldActions.size());
    }

    @Test
    void pendingReviewCompletionSchedulesReviewPresentationOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.HOLD);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().scheduled.get());
    }

    @Test
    void resolvedRequestCompletionSchedulesDetailsPresentationOnThePlayerScheduler() {
        Fixture fixture = fixture(deniedRequest(), SchedulerMode.HOLD);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(0, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().scheduled.get());
    }

    @Test
    void missingRequestMessageRunsOnThePlayerScheduler() {
        Fixture fixture = fixture(null, SchedulerMode.RUN);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
    }

    @Test
    void forbiddenRequestMessageRunsOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.ADMIN), SchedulerMode.RUN);

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(0, fixture.store().acquireCalls);
    }

    @Test
    void acquireConflictMessageRunsOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        fixture.store().rejectAcquire = true;

        assertTrue(fixture.controller().openReview(fixture.player().proxy(), REQUEST_ID));

        assertEquals(1, fixture.store().acquireCalls);
        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
    }

    @Test
    void storageFailureMessageReturnsThroughThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        fixture.store().failPending = true;

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.logger().severe.get());
    }

    @Test
    void retiredPlayerDropsQueueCallbackAndReconnectDoesNotReceiveIt() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE);
        TestScheduler reconnectScheduler = new TestScheduler(SchedulerMode.RUN);
        TestPlayer reconnect = new TestPlayer(REVIEWER_ID, reconnectScheduler);

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(0, fixture.player().openInventories.get());
        assertEquals(0, reconnectScheduler.scheduled.get());
        assertEquals(0, reconnect.messages.get());
    }

    @Test
    void schedulerRetirementPlusFalseReturnIsHandledExactlyOnceByControllerPath() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE_FALSE);

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(1, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.logger().retired.get());
        assertEquals(0, fixture.player().openInventories.get());
    }

    @Test
    void approvedDecisionCommitsOnceAndPresentsOnThePlayerScheduler() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        InventoryClickEvent approve = click(fixture.player().proxy(), review(lease), PunishmentRequestGuiRenderer.APPROVE_SLOT);

        fixture.controller().onInventoryClick(approve);

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.scheduler().scheduled.get());

        fixture.scheduler().mode = SchedulerMode.HOLD;
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                review(lease),
                PunishmentRequestGuiRenderer.APPROVE_SLOT
        ));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(2, fixture.scheduler().scheduled.get());
    }

    @Test
    void refreshAfterApprovedDecisionDoesNotCreateASecondDurableDecision() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        PunishmentApprovalLease lease = acquire(fixture);
        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                review(lease),
                PunishmentRequestGuiRenderer.APPROVE_SLOT
        ));
        PunishmentApprovalRequest approved = fixture.store().current;
        fixture.scheduler().mode = SchedulerMode.HOLD;

        fixture.controller().onInventoryClick(click(
                fixture.player().proxy(),
                new PunishmentRequestGuiState.Details(
                        new PunishmentRequestGuiState.RequestView(approved, "Target"),
                        0
                ),
                PunishmentRequestGuiRenderer.DETAILS_REFRESH_SLOT
        ));

        assertEquals(1, fixture.store().approveTransitions);
        assertEquals(0, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.APPROVED, fixture.store().current.status());
        assertEquals(2, fixture.scheduler().scheduled.get());
    }

    @Test
    void deniedDecisionSurvivesPresentationRetirementAndRetryDoesNotDuplicateIt() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RETIRE);
        PunishmentApprovalLease lease = acquire(fixture);
        PunishmentRequestGuiState.Denial denial = new PunishmentRequestGuiState.Denial(lease, "Target", 0);
        int presetSlot = PunishmentRequestDenialPreset.values()[0].slot();

        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial, presetSlot));

        assertEquals(1, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.DENIED, fixture.store().current.status());
        assertEquals(0, fixture.player().messages.get());
        assertEquals(1, fixture.scheduler().scheduled.get());

        fixture.controller().onInventoryClick(click(fixture.player().proxy(), denial, presetSlot));

        assertEquals(1, fixture.store().denyTransitions);
        assertEquals(PunishmentRequestStatus.DENIED, fixture.store().current.status());
        assertEquals(2, fixture.scheduler().scheduled.get());
    }

    @Test
    void workerAdmissionRejectionStartsNoStorageWork() {
        Fixture fixture = fixture(pending(StaffRank.MOD), SchedulerMode.RUN);
        fixture.executor().reject = true;

        assertTrue(fixture.controller().openQueue(fixture.player().proxy()));

        assertEquals(0, fixture.store().pendingReads);
        assertEquals(0, fixture.store().findReads);
        assertEquals(0, fixture.store().acquireCalls);
        assertEquals(0, fixture.scheduler().scheduled.get());
        assertEquals(1, fixture.player().messages.get());
        assertEquals(1, fixture.logger().warnings.get());
    }

    private static Fixture fixture(PunishmentApprovalRequest request, SchedulerMode mode) {
        TestStore store = new TestStore(request);
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        PunishmentRequestService service = service(store, authorization);
        TestScheduler scheduler = new TestScheduler(mode);
        TestPlayer player = new TestPlayer(REVIEWER_ID, scheduler);
        TestLogger logger = new TestLogger();
        Plugin plugin = plugin(logger);
        TestExecutor executor = new TestExecutor();
        PunishmentRequestGuiController controller = new PunishmentRequestGuiController(
                plugin,
                () -> service,
                () -> null,
                authorization,
                executor
        );
        return new Fixture(controller, store, scheduler, player, logger, executor, service);
    }

    private static PunishmentRequestService service(
            PunishmentRequestStore store,
            DefaultAuthorizationPolicy authorization
    ) {
        PunishmentService punishments = new PunishmentService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                proxy(ReasonPolicyRepository.class, (method, arguments) -> unexpected(method)),
                proxy(ModerationStore.class, (method, arguments) -> unexpected(method)),
                new EscalationEngine()
        );
        return new PunishmentRequestService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(2),
                Duration.ofMinutes(5),
                new SecureIdentifiers(new SecureRandom()),
                authorization,
                punishments,
                store
        );
    }

    private static PunishmentApprovalLease acquire(Fixture fixture) {
        return assertInstanceOf(
                PunishmentRequestResult.Leased.class,
                fixture.service().acquire(REQUEST_ID, reviewer())
        ).lease();
    }

    private static Actor reviewer() {
        return new Actor(REVIEWER_ID, "Reviewer", StaffRank.MOD);
    }

    private static PunishmentApprovalRequest pending(StaffRank requiredRank) {
        SanctionSpec sanction = new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.permanent());
        PunishmentStep step = new PunishmentStep(0, "Permanent", List.of(sanction));
        PunishmentProposal proposal = new PunishmentProposal(
                TARGET_ID,
                new Actor(REQUESTER_ID, "Requester", StaffRank.HELPER),
                "test.reason",
                "test",
                "Test reason",
                "Internal evidence",
                "test-v1",
                CaseVisibility.PUBLIC,
                requiredRank,
                new EscalationDecision(0, 0, 0, List.of(), step),
                List.of(sanction)
        );
        return PunishmentApprovalRequest.pending(
                REQUEST_ID,
                new IdempotencyKey("controller-scheduler-proof"),
                proposal,
                NOW,
                NOW.plus(Duration.ofHours(1))
        );
    }

    private static PunishmentApprovalRequest deniedRequest() {
        PunishmentApprovalRequest request = pending(StaffRank.MOD);
        return new PunishmentApprovalRequest(
                request.requestId(),
                request.submissionKey(),
                request.proposal(),
                request.createdAt(),
                request.expiresAt(),
                PunishmentRequestStatus.DENIED,
                1,
                REVIEWER_ID,
                "Denied in test",
                null,
                NOW.plusSeconds(1)
        );
    }

    private static PunishmentRequestGuiState.Review review(PunishmentApprovalLease lease) {
        return new PunishmentRequestGuiState.Review(lease, "Target", 0);
    }

    private static InventoryClickEvent click(Player player, PunishmentRequestGuiState state, int slot) {
        PunishmentRequestGuiHolder holder = new PunishmentRequestGuiHolder(state);
        Inventory inventory = inventory(holder);
        InventoryView view = inventoryView(player, inventory);
        return new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                slot,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
    }

    private static Inventory inventory(PunishmentRequestGuiHolder holder) {
        return proxy(Inventory.class, (method, arguments) -> switch (method.getName()) {
            case "getHolder" -> holder;
            case "getSize" -> 27;
            case "getType" -> InventoryType.CHEST;
            default -> defaultValue(method);
        });
    }

    private static InventoryView inventoryView(Player player, Inventory inventory) {
        return proxy(InventoryView.class, (method, arguments) -> switch (method.getName()) {
            case "getTopInventory", "getBottomInventory", "getInventory" -> inventory;
            case "getPlayer" -> player;
            case "getType" -> InventoryType.CHEST;
            case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            case "convertSlot" -> arguments[0];
            case "countSlots" -> 27;
            default -> defaultValue(method);
        });
    }

    private static Plugin plugin(TestLogger logger) {
        return proxy(Plugin.class, (method, arguments) -> switch (method.getName()) {
            case "getLogger" -> logger;
            case "getName" -> "PunishmentRequestControllerSchedulingTest";
            case "getServer" -> throw new AssertionError("legacy Bukkit scheduler path must not be used");
            default -> defaultValue(method);
        });
    }

    private record Fixture(
            PunishmentRequestGuiController controller,
            TestStore store,
            TestScheduler scheduler,
            TestPlayer player,
            TestLogger logger,
            TestExecutor executor,
            PunishmentRequestService service
    ) {
    }

    private enum SchedulerMode {
        RUN,
        HOLD,
        RETIRE,
        RETIRE_FALSE
    }

    private static final class TestScheduler {
        private final AtomicInteger scheduled = new AtomicInteger();
        private final java.util.ArrayList<Runnable> heldActions = new java.util.ArrayList<>();
        private SchedulerMode mode;
        private final EntityScheduler proxy;

        private TestScheduler(SchedulerMode mode) {
            this.mode = mode;
            proxy = proxy(EntityScheduler.class, (method, arguments) -> {
                if (!method.getName().equals("execute")) {
                    return defaultValue(method);
                }
                scheduled.incrementAndGet();
                Runnable action = (Runnable) arguments[1];
                Runnable retired = (Runnable) arguments[2];
                return switch (this.mode) {
                    case RUN -> {
                        action.run();
                        yield true;
                    }
                    case HOLD -> {
                        heldActions.add(action);
                        yield true;
                    }
                    case RETIRE -> {
                        retired.run();
                        yield true;
                    }
                    case RETIRE_FALSE -> {
                        retired.run();
                        yield false;
                    }
                };
            });
        }
    }

    private static final class TestPlayer {
        private final AtomicInteger messages = new AtomicInteger();
        private final AtomicInteger openInventories = new AtomicInteger();
        private final UUID id;
        private final Player proxy;

        private TestPlayer(UUID id, TestScheduler scheduler) {
            this.id = id;
            proxy = PunishmentRequestControllerSchedulingIntegrationTest.proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> this.id;
                case "getName" -> "Reviewer";
                case "getScheduler" -> scheduler.proxy;
                case "hasPermission" -> permission(arguments[0].toString());
                case "sendMessage" -> {
                    messages.incrementAndGet();
                    yield null;
                }
                case "openInventory" -> {
                    openInventories.incrementAndGet();
                    yield null;
                }
                case "closeInventory" -> null;
                default -> defaultValue(method);
            });
        }

        private static boolean permission(String permission) {
            return "enthusiastaff.punishment.requests.review".equals(permission)
                    || "enthusiastaff.rank.mod".equals(permission);
        }

        private Player proxy() {
            return proxy;
        }
    }

    private static final class TestLogger extends Logger {
        private final AtomicInteger retired = new AtomicInteger();
        private final AtomicInteger warnings = new AtomicInteger();
        private final AtomicInteger severe = new AtomicInteger();

        private TestLogger() {
            super("punishment-request-controller-test", null);
            setLevel(Level.ALL);
        }

        @Override
        public void log(LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                severe.incrementAndGet();
            } else if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.incrementAndGet();
            }
            if (record.getMessage() != null && record.getMessage().contains("retired player session")) {
                retired.incrementAndGet();
            }
        }
    }

    private static final class TestExecutor extends AbstractExecutorService {
        private boolean reject;
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("test rejection");
            }
            command.run();
        }
    }

    private static final class TestStore implements PunishmentRequestStore {
        private PunishmentApprovalRequest current;
        private boolean rejectAcquire;
        private boolean failPending;
        private int pendingReads;
        private int findReads;
        private int acquireCalls;
        private int approveTransitions;
        private int denyTransitions;
        private long fence;

        private TestStore(PunishmentApprovalRequest current) {
            this.current = current;
        }

        @Override
        public PunishmentRequestResult submit(PunishmentApprovalRequest request) {
            throw new AssertionError("submit is outside this controller test");
        }

        @Override
        public Optional<PunishmentApprovalRequest> find(UUID requestId) {
            findReads++;
            return current != null && current.requestId().equals(requestId)
                    ? Optional.of(current)
                    : Optional.empty();
        }

        @Override
        public List<PunishmentApprovalRequest> pending(Instant now, int limit) {
            pendingReads++;
            if (failPending) {
                throw new IllegalStateException("synthetic storage failure");
            }
            return current != null && current.pendingAt(now) ? List.of(current) : List.of();
        }

        @Override
        public Optional<PunishmentApprovalLease> acquire(
                UUID requestId,
                UUID ownerId,
                Instant now,
                Instant leaseExpiresAt
        ) {
            acquireCalls++;
            if (rejectAcquire || current == null || !current.pendingAt(now)) {
                return Optional.empty();
            }
            fence++;
            current = copy(current, PunishmentRequestStatus.PENDING, current.revision() + 1, null, null, null, null);
            return Optional.of(new PunishmentApprovalLease(current, ownerId, fence, leaseExpiresAt));
        }

        @Override
        public PunishmentRequestResult approve(
                PunishmentApprovalLease lease,
                Actor approver,
                CaseId caseId,
                Instant now
        ) {
            if (current == null || current.status() != PunishmentRequestStatus.PENDING) {
                return new PunishmentRequestResult.Rejected("REQUEST_NOT_PENDING", "Request is not pending");
            }
            approveTransitions++;
            current = copy(
                    current,
                    PunishmentRequestStatus.APPROVED,
                    current.revision() + 1,
                    approver.id(),
                    "Approved",
                    caseId,
                    now
            );
            return new PunishmentRequestResult.Approved(current, caseId, false);
        }

        @Override
        public PunishmentRequestResult deny(
                PunishmentApprovalLease lease,
                Actor approver,
                String note,
                Instant now
        ) {
            if (current == null || current.status() != PunishmentRequestStatus.PENDING) {
                return new PunishmentRequestResult.Rejected("REQUEST_NOT_PENDING", "Request is not pending");
            }
            denyTransitions++;
            current = copy(
                    current,
                    PunishmentRequestStatus.DENIED,
                    current.revision() + 1,
                    approver.id(),
                    note,
                    null,
                    now
            );
            return new PunishmentRequestResult.Denied(current, false);
        }

        @Override
        public int expire(Instant now) {
            return 0;
        }

        private static PunishmentApprovalRequest copy(
                PunishmentApprovalRequest request,
                PunishmentRequestStatus status,
                long revision,
                UUID resolvedBy,
                String note,
                CaseId caseId,
                Instant resolvedAt
        ) {
            return new PunishmentApprovalRequest(
                    request.requestId(),
                    request.submissionKey(),
                    request.proposal(),
                    request.createdAt(),
                    request.expiresAt(),
                    status,
                    revision,
                    resolvedBy,
                    note,
                    caseId,
                    resolvedAt
            );
        }
    }

    private static Object defaultValue(Method method) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "test-proxy";
                case "hashCode" -> System.identityHashCode(method);
                case "equals" -> false;
                default -> null;
            };
        }
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> invocation.invoke(
                        method,
                        arguments == null ? new Object[0] : arguments
                )
        ));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }
}
