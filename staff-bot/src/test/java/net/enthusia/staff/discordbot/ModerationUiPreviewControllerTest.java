package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModerationUiPreviewControllerTest {
    private static final long OWNER = 41L;

    @Test
    void normalPathCarriesRecommendationIntoConfirmationAndRemainsNonDestructive() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);

        current = button(controller, current, ModerationUiPreviewController.OP_PUNISH, "");
        assertEquals(ModerationUiPreviewModel.Screen.OFFENSE, current.snapshot().state().screen());

        current = select(controller, current, ModerationUiPreviewController.OP_OFFENSE, "spam");
        ModerationUiPreviewModel.Recommendation recommendation = current.snapshot().state().recommendation();
        assertEquals(ModerationUiPreviewModel.Screen.RECOMMENDATION, current.snapshot().state().screen());
        assertEquals(2, recommendation.relevantHistory());
        assertEquals(4, recommendation.totalHistory());

        current = button(controller, current, ModerationUiPreviewController.OP_APPLY, "");
        ModerationUiPreviewModel.State applied = current.snapshot().state();
        assertEquals(ModerationUiPreviewModel.Screen.OPTIONS, applied.screen());
        assertEquals(recommendation.action(), applied.actualAction());
        assertEquals(recommendation.scope(), applied.actualScope());
        assertEquals(recommendation.duration(), applied.actualDuration());
        assertTrue(applied.followedRecommendation());
        assertFalse(applied.overridden());

        current = button(controller, current, ModerationUiPreviewController.OP_REVIEW, "");
        assertEquals(ModerationUiPreviewModel.Screen.CONFIRM, current.snapshot().state().screen());
        assertTrue(current.snapshot().state().followedRecommendation());

        current = button(controller, current, ModerationUiPreviewController.OP_CONFIRM, "");
        assertEquals(ModerationUiPreviewModel.Screen.COMPLETE, current.snapshot().state().screen());
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, id(current, ModerationUiPreviewController.OP_BACK, ""), Optional.empty())
                        .type());
    }

    @Test
    void manualPunishmentControlsRequireExplicitCustomOverride() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = recommendation(controller, "spam");

        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(
                        OWNER,
                        id(current, ModerationUiPreviewController.OP_ACTION, "ban"),
                        Optional.empty()).type());

        current = button(controller, current, ModerationUiPreviewController.OP_CUSTOM, "");
        assertEquals(ModerationUiPreviewModel.Screen.CUSTOM_ACTION, current.snapshot().state().screen());
        assertTrue(current.snapshot().state().overridden());

        current = button(controller, current, ModerationUiPreviewController.OP_ACTION, "ban");
        current = button(controller, current, ModerationUiPreviewController.OP_SCOPE, "discord");
        current = select(controller, current, ModerationUiPreviewController.OP_DURATION, "permanent");

        ModerationUiPreviewModel.State custom = current.snapshot().state();
        assertEquals(ModerationUiPreviewModel.Screen.OPTIONS, custom.screen());
        assertTrue(custom.overridden());
        assertFalse(custom.followedRecommendation());
        assertEquals(ModerationUiPreviewModel.Action.BAN, custom.actualAction());
        assertEquals(ModerationUiPreviewModel.PERMANENT, custom.actualDuration());
        assertTrue(custom.approvalSummary().startsWith("Required"));

        current = button(controller, current, ModerationUiPreviewController.OP_REVIEW, "");
        assertEquals(ModerationUiPreviewModel.Screen.CONFIRM, current.snapshot().state().screen());
        assertFalse(current.snapshot().state().followedRecommendation());
    }

    @Test
    void customOverrideScenarioKeepsRecommendationVisibleWhileStaffChangesAction() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);
        current = select(controller, current, ModerationUiPreviewController.OP_SAMPLE, "custom_override");
        current = button(controller, current, ModerationUiPreviewController.OP_PUNISH, "");
        current = select(controller, current, ModerationUiPreviewController.OP_OFFENSE, "harassment");

        ModerationUiPreviewModel.Recommendation recommendation = current.snapshot().state().recommendation();
        assertEquals(ModerationUiPreviewModel.Action.MUTE, recommendation.action());
        assertEquals("7d", recommendation.duration());

        current = button(controller, current, ModerationUiPreviewController.OP_CUSTOM, "");
        current = button(controller, current, ModerationUiPreviewController.OP_ACTION, "kick");
        current = button(controller, current, ModerationUiPreviewController.OP_SCOPE, "discord");

        ModerationUiPreviewModel.State custom = current.snapshot().state();
        assertEquals(ModerationUiPreviewModel.Screen.OPTIONS, custom.screen());
        assertEquals(ModerationUiPreviewModel.Action.KICK, custom.actualAction());
        assertEquals(ModerationUiPreviewModel.NOT_APPLICABLE, custom.actualDuration());
        assertTrue(custom.overridden());
        assertEquals(recommendation, custom.recommendation());
    }

    @Test
    void supportsCustomOffenseDurationAndExplanationModals() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);
        current = button(controller, current, ModerationUiPreviewController.OP_PUNISH, "");

        ModerationUiPreviewController.Result offenseModal = controller.interact(
                OWNER,
                id(current, ModerationUiPreviewController.OP_OFFENSE, ""),
                Optional.of("other_custom"));
        assertEquals(ModerationUiPreviewController.ResultType.MODAL, offenseModal.type());
        assertNotNull(offenseModal.modal());
        current = controller.submitModal(OWNER, offenseModal.modal().customId(), "Repeated disruptive behavior");
        assertEquals(ModerationUiPreviewModel.Screen.RECOMMENDATION, current.snapshot().state().screen());
        assertTrue(current.snapshot().state().offenseLabel().contains("Repeated disruptive behavior"));

        current = button(controller, current, ModerationUiPreviewController.OP_CUSTOM, "");
        current = button(controller, current, ModerationUiPreviewController.OP_ACTION, "mute");
        current = button(controller, current, ModerationUiPreviewController.OP_SCOPE, "discord");

        ModerationUiPreviewController.Result durationModal = controller.interact(
                OWNER,
                id(current, ModerationUiPreviewController.OP_DURATION, ""),
                Optional.of("custom"));
        current = controller.submitModal(OWNER, durationModal.modal().customId(), "5d 12h");
        assertEquals("Custom — 5d 12h", current.snapshot().state().actualDuration());

        ModerationUiPreviewController.Result explanationModal = controller.interact(
                OWNER,
                id(current, ModerationUiPreviewController.OP_EXPLANATION, ""),
                Optional.empty());
        current = controller.submitModal(
                OWNER, explanationModal.modal().customId(), "Staff-facing preview context");
        assertEquals("Staff-facing preview context", current.snapshot().state().explanation());
    }

    @Test
    void exposesAllDeterministicLadderScenarios() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        for (ModerationUiPreviewModel.SampleScenario scenario : ModerationUiPreviewModel.SampleScenario.values()) {
            ModerationUiPreviewController.Result started = controller.start(OWNER);
            ModerationUiPreviewController.Result selected = controller.interact(
                    OWNER,
                    id(started, ModerationUiPreviewController.OP_SAMPLE, ""),
                    Optional.of(scenario.name().toLowerCase(Locale.ROOT)));
            assertEquals(ModerationUiPreviewModel.Screen.OVERVIEW, selected.snapshot().state().screen());
            assertEquals(scenario, selected.snapshot().state().sampleScenario());
        }
    }

    @Test
    void exposesAllRepresentativeAuthorityAndFailureStates() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        for (ModerationUiPreviewModel.EdgeState edgeState : ModerationUiPreviewModel.EdgeState.values()) {
            ModerationUiPreviewController.Result started = controller.start(OWNER);
            ModerationUiPreviewController.Result selected = controller.interact(
                    OWNER,
                    id(started, ModerationUiPreviewController.OP_EDGE, ""),
                    Optional.of(edgeState.name().toLowerCase(Locale.ROOT)));
            assertEquals(ModerationUiPreviewModel.Screen.EDGE_STATE, selected.snapshot().state().screen());
            assertEquals(edgeState, selected.snapshot().state().edgeState());
        }
    }

    @Test
    void rejectsWrongOwnerReplayMalformedAndExpiredControls() {
        MutableClock clock = new MutableClock();
        ModerationUiPreviewController controller = controller(clock);
        ModerationUiPreviewController.Result started = controller.start(OWNER);
        String punish = id(started, ModerationUiPreviewController.OP_PUNISH, "");

        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(99L, punish, Optional.empty()).type());

        ModerationUiPreviewController.Result advanced = controller.interact(OWNER, punish, Optional.empty());
        assertEquals(ModerationUiPreviewController.ResultType.VIEW, advanced.type());
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, punish, Optional.empty()).type());
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, "pui:not-valid", Optional.empty()).type());
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, "pui:session:-1:punish", Optional.empty()).type());
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, "pui:session:0:unsupported", Optional.empty()).type());

        ModerationUiPreviewController.Result second = controller.start(OWNER);
        clock.advance(Duration.ofMinutes(6));
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(
                        OWNER,
                        id(second, ModerationUiPreviewController.OP_PUNISH, ""),
                        Optional.empty()).type());
    }

    @Test
    void capacityIsBoundedAndExpiredSessionsReleaseCapacity() {
        MutableClock clock = new MutableClock();
        ModerationUiPreviewController controller = new ModerationUiPreviewController(
                new ModerationUiPreviewSessionStore(1, Duration.ofMinutes(5), clock, new SecureRandom()));

        assertEquals(ModerationUiPreviewController.ResultType.VIEW, controller.start(OWNER).type());
        assertEquals(ModerationUiPreviewController.ResultType.ERROR, controller.start(OWNER + 1).type());
        clock.advance(Duration.ofMinutes(6));
        assertEquals(ModerationUiPreviewController.ResultType.VIEW, controller.start(OWNER + 1).type());
    }

    @Test
    void componentIdsRejectDiscordLimitOverflow() {
        ModerationUiPreviewController.Result started = controller(new MutableClock()).start(OWNER);
        assertThrows(IllegalArgumentException.class, () -> ModerationUiPreviewController.componentId(
                started.snapshot(), "nav", "x".repeat(100)));
    }

    private static ModerationUiPreviewController.Result recommendation(
            ModerationUiPreviewController controller,
            String offense
    ) {
        ModerationUiPreviewController.Result current = controller.start(OWNER);
        current = button(controller, current, ModerationUiPreviewController.OP_PUNISH, "");
        return select(controller, current, ModerationUiPreviewController.OP_OFFENSE, offense);
    }

    private static ModerationUiPreviewController controller(MutableClock clock) {
        return new ModerationUiPreviewController(new ModerationUiPreviewSessionStore(
                32,
                Duration.ofMinutes(5),
                clock,
                new SecureRandom()
        ));
    }

    private static ModerationUiPreviewController.Result button(
            ModerationUiPreviewController controller,
            ModerationUiPreviewController.Result current,
            String operation,
            String argument
    ) {
        return controller.interact(OWNER, id(current, operation, argument), Optional.empty());
    }

    private static ModerationUiPreviewController.Result select(
            ModerationUiPreviewController controller,
            ModerationUiPreviewController.Result current,
            String operation,
            String value
    ) {
        return controller.interact(OWNER, id(current, operation, ""), Optional.of(value));
    }

    private static String id(
            ModerationUiPreviewController.Result result,
            String operation,
            String argument
    ) {
        return ModerationUiPreviewController.componentId(result.snapshot(), operation, argument);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-29T04:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
