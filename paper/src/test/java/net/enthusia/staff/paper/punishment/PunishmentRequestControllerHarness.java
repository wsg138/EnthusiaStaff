package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

final class PunishmentRequestControllerHarness {
    static final Instant NOW = Instant.parse("2026-09-20T20:00:00Z");
    static final UUID REQUEST_ID = UUID.fromString("62000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("62000000-0000-0000-0000-000000000002");
    private static final UUID REQUESTER_ID = UUID.fromString("62000000-0000-0000-0000-000000000003");
    static final UUID REVIEWER_ID = UUID.fromString("62000000-0000-0000-0000-000000000004");

    static Fixture fixture(PunishmentApprovalRequest request, SchedulerMode mode) {
        RequestStoreHarness store = new RequestStoreHarness(request);
        DefaultAuthorizationPolicy authorization = new DefaultAuthorizationPolicy();
        PunishmentRequestService service = service(store, authorization);
        SchedulerHarness scheduler = new SchedulerHarness(mode);
        PlayerHarness player = new PlayerHarness(REVIEWER_ID, scheduler);
        RecordingLogger logger = new RecordingLogger();
        Plugin plugin = plugin(logger);
        WorkerHarness executor = new WorkerHarness();
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

    static PunishmentApprovalLease acquire(Fixture fixture) {
        return assertInstanceOf(
                PunishmentRequestResult.Leased.class,
                fixture.service().acquire(REQUEST_ID, reviewer())
        ).lease();
    }

    private static Actor reviewer() {
        return new Actor(REVIEWER_ID, "Reviewer", StaffRank.MOD);
    }

    static PunishmentApprovalRequest pending(StaffRank requiredRank) {
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

    static PunishmentApprovalRequest deniedRequest() {
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

    static PunishmentRequestGuiState.Review review(PunishmentApprovalLease lease) {
        return new PunishmentRequestGuiState.Review(lease, "Target", 0);
    }

    static InventoryClickEvent click(Player player, PunishmentRequestGuiState state, int slot) {
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

    private static Plugin plugin(RecordingLogger logger) {
        return proxy(Plugin.class, (method, arguments) -> switch (method.getName()) {
            case "getLogger" -> logger;
            case "getName" -> "PunishmentRequestControllerSchedulingTest";
            case "getServer" -> throw new AssertionError("legacy Bukkit scheduler path must not be used");
            default -> defaultValue(method);
        });
    }

    record Fixture(
            PunishmentRequestGuiController controller,
            RequestStoreHarness store,
            SchedulerHarness scheduler,
            PlayerHarness player,
            RecordingLogger logger,
            WorkerHarness executor,
            PunishmentRequestService service
    ) {
    }

    enum SchedulerMode {
        RUN,
        HOLD,
        RETIRE,
        RETIRE_FALSE
    }

    static final class SchedulerHarness {
        final AtomicInteger scheduled = new AtomicInteger();
        final java.util.ArrayList<Runnable> heldActions = new java.util.ArrayList<>();
        SchedulerMode mode;
        private final EntityScheduler proxy;

        SchedulerHarness(SchedulerMode mode) {
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

    static final class PlayerHarness {
        final AtomicInteger messages = new AtomicInteger();
        final AtomicInteger openInventories = new AtomicInteger();
        private final UUID id;
        private final Player proxy;

        PlayerHarness(UUID id, SchedulerHarness scheduler) {
            this.id = id;
            proxy = PunishmentRequestControllerHarness.proxy(Player.class, (method, arguments) -> switch (method.getName()) {
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

        Player proxy() {
            return proxy;
        }
    }

    static final class RecordingLogger extends Logger {
        final AtomicInteger retired = new AtomicInteger();
        final AtomicInteger warnings = new AtomicInteger();
        final AtomicInteger severe = new AtomicInteger();

        RecordingLogger() {
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

    static final class WorkerHarness extends AbstractExecutorService {
        boolean reject;
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

    static final class RequestStoreHarness implements PunishmentRequestStore {
        PunishmentApprovalRequest current;
        boolean rejectAcquire;
        boolean failPending;
        int pendingReads;
        int findReads;
        int acquireCalls;
        int approveTransitions;
        int denyTransitions;
        private long fence;

        RequestStoreHarness(PunishmentApprovalRequest current) {
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
