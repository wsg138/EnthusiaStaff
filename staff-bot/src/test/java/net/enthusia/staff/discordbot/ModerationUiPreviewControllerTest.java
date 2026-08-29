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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModerationUiPreviewControllerTest {
    private static final long OWNER = 41L;
    private static final String OP_PUNISH = "punish";
    private static final String OP_ACTION = "action";
    private static final String OP_SCOPE = "scope";
    private static final String OP_REASON = "reason";
    private static final String OP_DURATION = "duration";

    @Test
    void completesPreviewWithoutAnyExternalAdapter() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);

        current = button(controller, current, OP_PUNISH, "");
        current = button(controller, current, OP_ACTION, "ban");
        current = button(controller, current, OP_SCOPE, "both");
        current = select(controller, current, OP_REASON, "harassment");
        current = select(controller, current, OP_DURATION, "permanent");
        current = button(controller, current, "toggle", "delete");
        current = button(controller, current, "review", "");

        assertEquals(ModerationUiPreviewModel.Screen.CONFIRM, current.snapshot().state().screen());
        assertTrue(current.snapshot().state().approvalSummary().startsWith("Required"));

        current = button(controller, current, "confirm", "");
        assertEquals(ModerationUiPreviewModel.Screen.COMPLETE, current.snapshot().state().screen());
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, id(current, "back", ""), Optional.empty()).type());
    }

    @Test
    void permanentApprovalCopyDoesNotMislabelMinecraftAsDiscord() {
        ModerationUiPreviewModel.State state = ModerationUiPreviewModel.State.initial()
                .withAction(ModerationUiPreviewModel.Action.BAN)
                .withScope(ModerationUiPreviewModel.Scope.MINECRAFT)
                .withReason("Harassment")
                .withDuration("Permanent");

        assertTrue(state.approvalSummary().contains("selected platform scope"));
        assertFalse(state.approvalSummary().contains("Discord"));
    }

    @Test
    void rejectsWrongOwnerReplayMalformedAndExpiredControls() {
        MutableClock clock = new MutableClock();
        ModerationUiPreviewController controller = controller(clock);
        ModerationUiPreviewController.Result started = controller.start(OWNER);
        String punish = id(started, OP_PUNISH, "");

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
                controller.interact(OWNER, id(second, OP_PUNISH, ""), Optional.empty()).type());
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

    @Test
    void supportsCustomReasonAndDurationModals() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);
        current = button(controller, current, OP_PUNISH, "");
        current = button(controller, current, OP_ACTION, "mute");
        current = button(controller, current, OP_SCOPE, "discord");

        String reasonId = id(current, OP_REASON, "");
        ModerationUiPreviewController.Result reasonModal = controller.interact(
                OWNER, reasonId, Optional.of("custom"));
        assertEquals(ModerationUiPreviewController.ResultType.MODAL, reasonModal.type());
        assertNotNull(reasonModal.modal());
        current = controller.submitModal(OWNER, reasonModal.modal().customId(), "Repeated targeted harassment");

        String durationId = id(current, OP_DURATION, "");
        ModerationUiPreviewController.Result durationModal = controller.interact(
                OWNER, durationId, Optional.of("custom"));
        current = controller.submitModal(OWNER, durationModal.modal().customId(), "5d 12h");

        assertEquals(ModerationUiPreviewModel.Screen.OPTIONS, current.snapshot().state().screen());
        assertEquals("Custom — Repeated targeted harassment", current.snapshot().state().reason());
        assertEquals("Custom — 5d 12h", current.snapshot().state().duration());
    }

    @Test
    void exposesAllRepresentativeFailureStates() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        for (ModerationUiPreviewModel.Scenario scenario : ModerationUiPreviewModel.Scenario.values()) {
            ModerationUiPreviewController.Result started = controller.start(OWNER);
            ModerationUiPreviewController.Result selected = controller.interact(
                    OWNER,
                    id(started, "scenario", ""),
                    Optional.of(scenario.name().toLowerCase(java.util.Locale.ROOT)));
            assertEquals(ModerationUiPreviewModel.Screen.SCENARIO, selected.snapshot().state().screen());
            assertEquals(scenario, selected.snapshot().state().scenario());
        }
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
