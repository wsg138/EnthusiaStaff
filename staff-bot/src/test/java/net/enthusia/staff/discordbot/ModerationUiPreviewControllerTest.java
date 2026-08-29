package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void completesPreviewWithoutAnyExternalAdapter() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);

        current = button(controller, current, "punish", "");
        current = button(controller, current, "action", "ban");
        current = button(controller, current, "scope", "both");
        current = select(controller, current, "reason", "harassment");
        current = select(controller, current, "duration", "permanent");
        current = button(controller, current, "toggle", "delete");
        current = button(controller, current, "review", "");

        assertEquals(ModerationUiPreviewModel.Screen.CONFIRM, current.snapshot().state().screen());
        assertTrue(current.snapshot().state().approvalSummary().startsWith("Required"));

        current = button(controller, current, "confirm", "");
        assertEquals(ModerationUiPreviewModel.Screen.COMPLETE, current.snapshot().state().screen());
    }

    @Test
    void rejectsWrongOwnerReplayMalformedAndExpiredControls() {
        MutableClock clock = new MutableClock();
        ModerationUiPreviewController controller = controller(clock);
        ModerationUiPreviewController.Result started = controller.start(OWNER);
        String punish = id(started, "punish", "");

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

        ModerationUiPreviewController.Result second = controller.start(OWNER);
        clock.advance(Duration.ofMinutes(6));
        assertEquals(
                ModerationUiPreviewController.ResultType.ERROR,
                controller.interact(OWNER, id(second, "punish", ""), Optional.empty()).type());
    }

    @Test
    void supportsCustomReasonAndDurationModals() {
        ModerationUiPreviewController controller = controller(new MutableClock());
        ModerationUiPreviewController.Result current = controller.start(OWNER);
        current = button(controller, current, "punish", "");
        current = button(controller, current, "action", "mute");
        current = button(controller, current, "scope", "discord");

        String reasonId = id(current, "reason", "");
        ModerationUiPreviewController.Result reasonModal = controller.interact(
                OWNER, reasonId, Optional.of("custom"));
        assertEquals(ModerationUiPreviewController.ResultType.MODAL, reasonModal.type());
        assertNotNull(reasonModal.modal());
        current = controller.submitModal(OWNER, reasonModal.modal().customId(), "Repeated targeted harassment");

        String durationId = id(current, "duration", "");
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
