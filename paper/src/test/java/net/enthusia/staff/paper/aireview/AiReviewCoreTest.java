package net.enthusia.staff.paper.aireview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.enthusia.staff.paper.aireview.AiReviewModels.Containment;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionAuthority;
import net.enthusia.staff.paper.aireview.AiReviewModels.CorrectionDecision;
import net.enthusia.staff.paper.aireview.AiReviewModels.Decision;
import net.enthusia.staff.paper.aireview.AiReviewModels.EventDetails;
import net.enthusia.staff.paper.aireview.AiReviewModels.MessageAction;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;
import net.enthusia.staff.paper.aireview.AiReviewModels.StrikeRecommendation;
import net.enthusia.staff.paper.aireview.AiReviewModels.SupportFlow;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

class AiReviewCoreTest {
    @Test
    void disabledConfigurationDoesNotRequireCentralSettings() {
        MemoryConfiguration root = new MemoryConfiguration();
        var loaded = AiReviewConfiguration.load(root.getConfigurationSection("ai-review"), ignored -> null);
        assertFalse(loaded.enabled());
        assertNull(loaded.diagnostic());
    }

    @Test
    void missingBearerTokenDisablesOnlyAiReview() {
        MemoryConfiguration root = configuredRoot();
        var loaded = AiReviewConfiguration.load(root.getConfigurationSection("ai-review"), ignored -> null);
        assertFalse(loaded.enabled());
        assertTrue(loaded.diagnostic().contains("bearer token"));
    }

    @Test
    void malformedBaseUrlFailsSafeWithoutExposingToken() {
        MemoryConfiguration root = configuredRoot();
        root.set("ai-review.base-url", "file:///tmp/nope");
        var loaded = AiReviewConfiguration.load(
                root.getConfigurationSection("ai-review"),
                ignored -> "secret-value"
        );
        assertFalse(loaded.enabled());
        assertFalse(loaded.diagnostic().contains("secret-value"));
    }

    @Test
    void resourceBoundsRejectUnboundedOptionalSettings() {
        MemoryConfiguration root = configuredRoot();
        root.set("ai-review.response-max-bytes", 1_048_577);
        var loaded = AiReviewConfiguration.load(
                root.getConfigurationSection("ai-review"),
                ignored -> "token"
        );
        assertFalse(loaded.enabled());
        assertTrue(loaded.diagnostic().contains("response-max-bytes"));
    }

    @Test
    void validConfigurationUsesEnvironmentTokenWithoutPersistingIt() {
        MemoryConfiguration root = configuredRoot();
        var loaded = AiReviewConfiguration.load(
                root.getConfigurationSection("ai-review"),
                name -> name.equals("ES_TEST_AI") ? "runtime-only-token" : null
        );
        assertTrue(loaded.enabled());
        assertEquals("runtime-only-token", loaded.configuration().orElseThrow().bearerToken());
        assertEquals(AiReviewPermissions.QUEUE, loaded.configuration().orElseThrow().notificationPermission());
        assertEquals(100, loaded.configuration().orElseThrow().reviewLimit());
    }

    @Test
    void initialPollSeedsDedupeWithoutRestartNotificationSpam() {
        AiReviewPollState state = new AiReviewPollState(10);
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        AiReviewPollState.Update update = state.success(
                List.of(review("one", ReviewPriority.NORMAL, now)),
                now
        );
        assertTrue(update.newlyDiscovered().isEmpty());
        assertEquals(1, update.snapshot().items().size());
    }

    @Test
    void laterPollNotifiesOnlyNewEventsAndUrgentItemsSortFirst() {
        AiReviewPollState state = new AiReviewPollState(10);
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        state.success(List.of(review("one", ReviewPriority.NORMAL, now)), now);
        AiReviewPollState.Update update = state.success(
                List.of(
                        review("one", ReviewPriority.NORMAL, now),
                        review("urgent", ReviewPriority.URGENT, now.plusSeconds(1))
                ),
                now.plusSeconds(2)
        );
        assertEquals(List.of("urgent"), update.newlyDiscovered().stream()
                .map(ReviewItem::eventId)
                .toList());
        assertEquals("urgent", update.snapshot().items().getFirst().eventId());
        assertEquals(1, update.snapshot().urgentCount());
    }

    @Test
    void pollFailureKeepsCachedItemsButMarksThemNonAuthoritative() {
        AiReviewPollState state = new AiReviewPollState(10);
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        state.success(List.of(review("one", ReviewPriority.NORMAL, now)), now);
        state.failure("timeout", now.plusSeconds(1));
        assertEquals(1, state.snapshot().items().size());
        assertFalse(state.snapshot().authoritative());
        assertEquals("timeout", state.snapshot().issue());
    }

    @Test
    void queueFreshnessUsesBoundedStalenessWindow() {
        AiReviewPollState state = new AiReviewPollState(10);
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        state.success(List.of(), now);
        assertTrue(state.snapshot().fresh(now.plusSeconds(29), Duration.ofSeconds(30)));
        assertFalse(state.snapshot().fresh(now.plusSeconds(31), Duration.ofSeconds(30)));
    }

    @Test
    void boundedBackoffOpensExponentiallyAndResetsOnSuccess() {
        AiReviewBackoff backoff = new AiReviewBackoff(
                Duration.ofSeconds(2),
                Duration.ofSeconds(8)
        );
        Instant now = Instant.parse("2026-10-03T20:00:00Z");
        assertEquals(Duration.ZERO, backoff.remaining(now));
        assertEquals(Duration.ofSeconds(2), backoff.failure(now));
        assertTrue(backoff.remaining(now.plusSeconds(1)).compareTo(Duration.ZERO) > 0);
        assertEquals(Duration.ofSeconds(4), backoff.failure(now.plusSeconds(2)));
        assertEquals(Duration.ofSeconds(8), backoff.failure(now.plusSeconds(6)));
        assertEquals(Duration.ofSeconds(8), backoff.failure(now.plusSeconds(14)));
        backoff.success();
        assertEquals(Duration.ZERO, backoff.remaining(now.plusSeconds(14)));
        assertEquals(0, backoff.consecutiveFailures());
    }

    @Test
    void reviewExecutorHasBoundedQueueAndCleanShutdown() throws Exception {
        ThreadPoolExecutor executor = AiReviewSubsystem.createExecutor(1, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            executor.execute(() -> {
                // Occupies the one bounded queue slot.
            });
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
                // Third task must be rejected rather than making the queue unbounded.
            }));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void convenienceActionPreservesNonActionDecisionDimensions() {
        Decision original = decision();
        CorrectionDecision corrected = CorrectionDecision.from(original)
                .withAction(MessageAction.BLOCK);
        assertEquals("SAFE", corrected.semanticLabel());
        assertEquals(ReviewPriority.URGENT, corrected.reviewPriority());
        assertEquals(StrikeRecommendation.EVIDENCE, corrected.strikeRecommendation());
        assertEquals(Containment.MUTE, corrected.containment());
        assertEquals(120, corrected.containmentDurationSeconds());
        assertEquals(SupportFlow.TARGET_SAFETY_CHECK, corrected.supportFlow());
        assertEquals(List.of("reason-a"), corrected.reasonCodes());
    }

    @Test
    void semanticCorrectionChangesOnlySemanticLabel() {
        CorrectionDecision original = CorrectionDecision.from(decision());
        CorrectionDecision corrected = original.withSemanticLabel("REAL_WORLD_THREAT");
        assertEquals("REAL_WORLD_THREAT", corrected.semanticLabel());
        assertEquals(original.messageAction(), corrected.messageAction());
        assertEquals(original.reviewPriority(), corrected.reviewPriority());
        assertEquals(original.strikeRecommendation(), corrected.strikeRecommendation());
        assertEquals(original.containment(), corrected.containment());
        assertEquals(original.supportFlow(), corrected.supportFlow());
    }

    @Test
    void regularStaffCanNeverRequestAdminAuthority() {
        CommandSender sender = senderWithPermissions(Set.of(AiReviewPermissions.CORRECT));
        assertThrows(
                SecurityException.class,
                () -> AiReviewPermissions.authority(sender, configuration(true), true)
        );
        assertEquals(
                CorrectionAuthority.STAFF,
                AiReviewPermissions.authority(sender, configuration(true), false)
        );
    }

    @Test
    void adminAuthorityRequiresBothLocalPermissionAndConfiguredEnablement() {
        CommandSender sender = senderWithPermissions(Set.of(AiReviewPermissions.ADMIN));
        assertThrows(
                SecurityException.class,
                () -> AiReviewPermissions.authority(sender, configuration(false), true)
        );
        assertEquals(
                CorrectionAuthority.ADMIN,
                AiReviewPermissions.authority(sender, configuration(true), true)
        );
    }

    @Test
    void presentationBoundsLongMessagesAndDoesNotExposeArbitraryRawJson() {
        EventDetails details = details("x".repeat(5_000));
        List<String> lines = AiReviewPresentation.detailLines(details, configuration(false));
        assertTrue(lines.stream().allMatch(line -> line.length() <= 1_000));
        assertTrue(lines.stream().noneMatch(line -> line.contains("secret_unknown_field")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Message:")));
    }

    @Test
    void presentationBoundsScoresReasonsAndContext() {
        EventDetails details = details("hello");
        List<String> lines = AiReviewPresentation.detailLines(details, configuration(false));
        assertTrue(lines.stream().filter(line -> line.startsWith("Context ")).count() <= 2);
        assertTrue(lines.stream().filter(line -> line.startsWith("Scores:")).count() <= 1);
        assertTrue(lines.stream().filter(line -> line.startsWith("Reasons:")).count() <= 1);
    }

    private static MemoryConfiguration configuredRoot() {
        MemoryConfiguration root = new MemoryConfiguration();
        root.set("ai-review.enabled", true);
        root.set("ai-review.base-url", "http://127.0.0.1:8787");
        root.set("ai-review.client-id", "staff-test");
        root.set("ai-review.token-environment", "ES_TEST_AI");
        return root;
    }

    private static AiReviewConfiguration configuration(boolean admin) {
        return new AiReviewConfiguration(
                URI.create("http://127.0.0.1:8787"),
                "test",
                "token",
                AiReviewPermissions.QUEUE,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                100,
                1,
                8,
                64 * 1024,
                32,
                21,
                120,
                2,
                2,
                2,
                admin
        );
    }

    private static ReviewItem review(String id, ReviewPriority priority, Instant occurredAt) {
        return new ReviewItem(
                id,
                occurredAt,
                "minecraft",
                "minecraft_public",
                "SAFE",
                MessageAction.ALLOW,
                priority,
                List.of("r"),
                null
        );
    }

    private static Decision decision() {
        return new Decision(
                MessageAction.ALLOW,
                "SAFE",
                ReviewPriority.URGENT,
                StrikeRecommendation.EVIDENCE,
                Containment.MUTE,
                120,
                SupportFlow.TARGET_SAFETY_CHECK,
                Map.of("safe", 0.9),
                0.9,
                List.of("rule-a"),
                List.of("reason-a"),
                "model-v1",
                "v1"
        );
    }

    private static EventDetails details(String text) {
        return new EventDetails(
                "event-1",
                "client",
                "minecraft",
                "minecraft_public",
                "SMP",
                "global",
                null,
                "message-1",
                null,
                "sender-1",
                Instant.parse("2026-10-03T19:00:00Z"),
                text,
                null,
                decision(),
                null,
                List.of(),
                null,
                List.of(
                        new AiReviewModels.ContextEvidence(
                                "ctx-1",
                                new AiReviewModels.MessageReference("minecraft", "SMP", "global", "m0"),
                                "sender-2",
                                Instant.parse("2026-10-03T18:59:58Z"),
                                "first context"
                        ),
                        new AiReviewModels.ContextEvidence(
                                "ctx-2",
                                new AiReviewModels.MessageReference("minecraft", "SMP", "global", "m1"),
                                "sender-3",
                                Instant.parse("2026-10-03T18:59:59Z"),
                                "second context"
                        ),
                        new AiReviewModels.ContextEvidence(
                                "ctx-3",
                                new AiReviewModels.MessageReference("minecraft", "SMP", "global", "m2"),
                                "sender-4",
                                Instant.parse("2026-10-03T19:00:00Z"),
                                "third context"
                        )
                )
        );
    }

    private static CommandSender senderWithPermissions(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                AiReviewCoreTest.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("hasPermission")
                            && arguments != null
                            && arguments.length == 1
                            && arguments[0] instanceof String permission) {
                        return permissions.contains(permission);
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                }
        );
    }
}
