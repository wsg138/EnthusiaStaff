package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.evidence.ClientEvidenceSnapshot;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.player.PlayerPlatform;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import net.enthusia.staff.domain.ports.ReasonPolicyRepository;
import net.enthusia.staff.domain.ports.ReportStore;
import net.enthusia.staff.domain.ports.SanctionLookup;
import net.enthusia.staff.domain.report.CreateReportRequest;
import net.enthusia.staff.domain.report.ReportSubmissionResult;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.paper.report.ChatContextBuffer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ReportCommandFoliaSchedulingTest {
    private static final UUID REPORTER_ID = new UUID(0L, 1L);
    private static final UUID TARGET_ID = new UUID(0L, 2L);
    private static final UUID REPORT_ID = new UUID(0L, 3L);
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void capturesOnlineTargetEvidenceOnTheTargetsEntityScheduler() {
        Fixture fixture = new Fixture((current, action, retired) -> {
            current.runOnTarget(action);
            return true;
        });

        fixture.command().onCommand(fixture.reporter, null, "report", arguments());

        assertEquals(1, fixture.globalExecutions.get());
        assertEquals(1, fixture.targetSchedules.get());
        assertEquals(1, fixture.evidenceCaptures.get());
        assertEquals(1, fixture.submissions.size());
        CreateReportRequest request = fixture.submissions.getFirst();
        assertEquals(TARGET_ID, request.targetId());
        assertEquals(Optional.of("12,70,-4"), request.targetCoordinates());
        assertEquals(Optional.of(fixture.evidence()), request.targetClientEvidence());
        assertEquals(fixture.evidence().capturedAt(), request.createdAt());
        assertEquals(1, fixture.reporterSchedules.get());
        assertEquals(1, fixture.reporterMessages.get());
    }

    @Test
    void retiredTargetFallsBackToAReportWithoutTargetEvidenceOnlyOnce() {
        Fixture fixture = new Fixture((current, action, retired) -> {
            current.runOnTarget(retired);
            return false;
        });

        fixture.command().onCommand(fixture.reporter, null, "report", arguments());

        assertEquals(1, fixture.globalExecutions.get());
        assertEquals(1, fixture.targetSchedules.get());
        assertEquals(0, fixture.evidenceCaptures.get());
        assertEquals(1, fixture.submissions.size());
        CreateReportRequest request = fixture.submissions.getFirst();
        assertTrue(request.targetCoordinates().isEmpty());
        assertTrue(request.targetClientEvidence().isEmpty());
        assertEquals(NOW, request.createdAt());
        assertEquals(1, fixture.reporterSchedules.get());
        assertEquals(1, fixture.reporterMessages.get());
    }

    private static String[] arguments() {
        return new String[]{"Target", "test.reason", "Observed behavior"};
    }

    private static final class Fixture {
        private final AtomicReference<String> context = new AtomicReference<>("command");
        private final AtomicInteger globalExecutions = new AtomicInteger();
        private final AtomicInteger targetSchedules = new AtomicInteger();
        private final AtomicInteger reporterSchedules = new AtomicInteger();
        private final AtomicInteger evidenceCaptures = new AtomicInteger();
        private final AtomicInteger reporterMessages = new AtomicInteger();
        private final List<CreateReportRequest> submissions = new ArrayList<>();
        private Plugin plugin;
        private final Player reporter;
        private final Player target;

        private Fixture(TargetScheduling targetScheduling) {
            World world = proxy(World.class, (method, arguments) -> {
                if (method.getName().equals("getKey")) {
                    return NamespacedKey.minecraft("world");
                }
                return unexpected(method);
            });
            EntityScheduler reporterScheduler = scheduler((seenPlugin, action, retired, delay) -> {
                assertSame(plugin, seenPlugin);
                assertEquals(1L, delay);
                reporterSchedules.incrementAndGet();
                runOnReporter(action);
                return true;
            });
            EntityScheduler targetScheduler = scheduler((seenPlugin, action, retired, delay) -> {
                assertSame(plugin, seenPlugin);
                assertEquals(1L, delay);
                targetSchedules.incrementAndGet();
                return targetScheduling.execute(this, action, retired);
            });
            reporter = player((method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> REPORTER_ID;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 4, 65, 8);
                case "getScheduler" -> reporterScheduler;
                case "sendMessage" -> {
                    assertEquals("reporter", context.get());
                    reporterMessages.incrementAndGet();
                    yield null;
                }
                default -> unexpected(method);
            });
            target = player((method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> TARGET_ID;
                case "getLocation" -> {
                    assertEquals("target", context.get());
                    yield new Location(world, 12, 70, -4);
                }
                case "getScheduler" -> targetScheduler;
                default -> unexpected(method);
            });
            GlobalRegionScheduler global = proxy(GlobalRegionScheduler.class, (method, arguments) -> {
                if (method.getName().equals("execute")) {
                    assertSame(plugin, arguments[0]);
                    globalExecutions.incrementAndGet();
                    runOnGlobal((Runnable) arguments[1]);
                    return null;
                }
                return unexpected(method);
            });
            Server server = proxy(Server.class, (method, arguments) -> switch (method.getName()) {
                case "getGlobalRegionScheduler" -> global;
                case "getPlayerExact" -> target;
                default -> unexpected(method);
            });
            plugin = proxy(Plugin.class, (method, arguments) -> switch (method.getName()) {
                case "getServer" -> server;
                case "getLogger" -> Logger.getLogger("ReportCommandFoliaSchedulingTest");
                default -> unexpected(method);
            });
        }

        private ReportCommand command() {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            return new ReportCommand(
                    new ReportCommand.Dependencies(
                            plugin,
                            clock,
                            "test-server",
                            () -> OperationalMode.ACTIVE,
                            () -> players(),
                            () -> reports(),
                            () -> noRestrictions(),
                            reasons(),
                            new ChatContextBuffer(clock),
                            player -> captureEvidence(player)
                    ),
                    new DirectExecutor()
            );
        }

        private PlayerDirectory players() {
            return proxy(PlayerDirectory.class, (method, arguments) -> {
                if (method.getName().equals("find")) {
                    return Optional.of(new PlayerIdentity(
                            TARGET_ID,
                            Optional.of("Target"),
                            PlayerPlatform.UNKNOWN,
                            NOW.minusSeconds(60),
                            NOW
                    ));
                }
                return unexpected(method);
            });
        }

        private ReportStore reports() {
            return proxy(ReportStore.class, (method, arguments) -> {
                if (method.getName().equals("submit")) {
                    submissions.add((CreateReportRequest) arguments[0]);
                    return new ReportSubmissionResult.Accepted(REPORT_ID, false, false);
                }
                return unexpected(method);
            });
        }

        private SanctionLookup noRestrictions() {
            return (playerId, types, instant) -> List.of();
        }

        private ReasonPolicyRepository reasons() {
            ReasonPolicy policy = new ReasonPolicy(
                    "test.reason",
                    "test",
                    "Test reason",
                    1,
                    false,
                    List.of(new PunishmentStep(0, "Warning", List.of(
                            new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())
                    )))
            );
            return new ReasonPolicyRepository() {
                @Override
                public Optional<ReasonPolicy> find(String reasonId) {
                    return Optional.of(policy);
                }

                @Override
                public Collection<ReasonPolicy> all() {
                    return List.of(policy);
                }

                @Override
                public String activeVersion() {
                    return "test";
                }
            };
        }

        private ClientEvidenceSnapshot captureEvidence(Player player) {
            assertSame(target, player);
            assertEquals("target", context.get());
            evidenceCaptures.incrementAndGet();
            return evidence();
        }

        private ClientEvidenceSnapshot evidence() {
            return new ClientEvidenceSnapshot(
                    TARGET_ID,
                    NOW.plusSeconds(1),
                    PlayerPlatform.JAVA,
                    Optional.of(763),
                    Optional.of("1.21.11"),
                    Optional.empty(),
                    IntegrationAvailability.NOT_INSTALLED,
                    Optional.empty(),
                    IntegrationAvailability.NOT_INSTALLED,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    IntegrationAvailability.NOT_INSTALLED,
                    IntegrationAvailability.NOT_INSTALLED,
                    Optional.empty(),
                    IntegrationAvailability.NOT_INSTALLED,
                    Optional.empty()
            );
        }

        private void runOnGlobal(Runnable action) {
            runInContext("global", action);
        }

        private void runOnTarget(Runnable action) {
            runInContext("target", action);
        }

        private void runOnReporter(Runnable action) {
            runInContext("reporter", action);
        }

        private void runInContext(String next, Runnable action) {
            String previous = context.getAndSet(next);
            try {
                action.run();
            } finally {
                context.set(previous);
            }
        }
    }

    private static EntityScheduler scheduler(SchedulerExecution execution) {
        return proxy(EntityScheduler.class, (method, arguments) -> {
            if (method.getName().equals("execute")) {
                return execution.execute(
                        (Plugin) arguments[0],
                        (Runnable) arguments[1],
                        (Runnable) arguments[2],
                        (Long) arguments[3]
                );
            }
            return unexpected(method);
        });
    }

    private static Player player(Invocation invocation) {
        return proxy(Player.class, invocation);
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return objectMethod(instance, method, arguments);
                    }
                    return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
                }
        ));
    }

    private static Object objectMethod(Object instance, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "toString" -> instance.getClass().getSimpleName();
            case "hashCode" -> System.identityHashCode(instance);
            case "equals" -> instance == arguments[0];
            default -> throw new AssertionError("Unexpected object method: " + method.getName());
        };
    }

    private static Object unexpected(Method method) {
        throw new AssertionError("Unexpected call: " + method.getName());
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Method method, Object[] arguments) throws Throwable;
    }

    @FunctionalInterface
    private interface SchedulerExecution {
        boolean execute(Plugin plugin, Runnable action, Runnable retired, long delay);
    }

    @FunctionalInterface
    private interface TargetScheduling {
        boolean execute(Fixture fixture, Runnable action, Runnable retired);
    }

    private static final class DirectExecutor extends AbstractExecutorService {
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
            assertFalse(shutdown);
            command.run();
        }
    }
}
