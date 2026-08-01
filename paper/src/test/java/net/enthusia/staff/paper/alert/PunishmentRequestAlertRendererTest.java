package net.enthusia.staff.paper.alert;

import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentApprovalRequest;
import net.enthusia.staff.domain.application.PunishmentRequestAlertAudience;
import net.enthusia.staff.domain.application.PunishmentRequestAlertClaim;
import net.enthusia.staff.domain.application.PunishmentRequestLifecycleEventType;
import net.enthusia.staff.domain.auth.StaffRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentRequestAlertRendererTest {
    private final PunishmentRequestAlertRenderer renderer = new PunishmentRequestAlertRenderer();

    @Test
    void rendersEveryLifecycleEventForEveryAudience() {
        for (PunishmentRequestLifecycleEventType eventType : PunishmentRequestLifecycleEventType.values()) {
            PunishmentApprovalRequest request = PunishmentRequestAlertTestFixtures.request(eventType);
            for (PunishmentRequestAlertAudience audience : PunishmentRequestAlertAudience.values()) {
                UUID recipient = switch (audience) {
                    case DIRECT_RECIPIENT -> PunishmentRequestAlertTestFixtures.REQUESTER_ID;
                    case ELIGIBLE_REVIEWERS -> PunishmentRequestAlertTestFixtures.REVIEWER_ID;
                    case OPERATIONAL_ADMINISTRATORS -> PunishmentRequestAlertTestFixtures.ADMIN_ID;
                };
                PunishmentRequestAlertClaim claim = PunishmentRequestAlertTestFixtures.claim(
                        eventType,
                        audience,
                        recipient,
                        StaffRank.MOD
                );

                Component message = renderer.render(claim, request, "TargetName", "LifecycleActor").message();
                String text = plain(message);

                assertTrue(text.contains("TargetName"), eventType + " " + audience);
                assertTrue(text.contains(request.requestId().toString()), eventType + " " + audience);
                assertTrue(text.contains(request.proposal().publicReason()), eventType + " " + audience);
                assertFalse(text.contains(request.proposal().internalExplanation()), eventType + " " + audience);
                assertFalse(text.contains(request.resolutionNote() == null ? "private-never" : request.resolutionNote()),
                        eventType + " " + audience);
            }
        }
    }

    @Test
    void reviewerSubmissionUsesExactClickableReviewCommand() {
        PunishmentApprovalRequest request = PunishmentRequestAlertTestFixtures.request(
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED
        );
        PunishmentRequestAlertClaim claim = PunishmentRequestAlertTestFixtures.claim(
                PunishmentRequestLifecycleEventType.REQUEST_SUBMITTED,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                PunishmentRequestAlertTestFixtures.REVIEWER_ID,
                StaffRank.MOD
        );

        Component message = renderer.render(claim, request, null, null).message();
        ClickEvent event = findClick(message);

        assertNotNull(event);
        assertEquals(ClickEvent.Action.RUN_COMMAND, event.action());
        assertEquals("/punish review " + request.requestId(), event.value());
        assertTrue(plain(message).contains(PunishmentRequestAlertTestFixtures.TARGET_ID.toString()));
    }

    @Test
    void requesterOutputNeverLeaksPrivateExplanationAndUsesCorrectCaseRules() {
        for (PunishmentRequestLifecycleEventType eventType : PunishmentRequestLifecycleEventType.values()) {
            PunishmentApprovalRequest request = PunishmentRequestAlertTestFixtures.request(eventType);
            PunishmentRequestAlertClaim claim = PunishmentRequestAlertTestFixtures.claim(
                    eventType,
                    PunishmentRequestAlertAudience.DIRECT_RECIPIENT,
                    PunishmentRequestAlertTestFixtures.REQUESTER_ID,
                    null
            );

            String text = plain(renderer.render(claim, request, "Target", "Reviewer").message());

            assertFalse(text.contains("Private evidence"));
            if (eventType == PunishmentRequestLifecycleEventType.REQUEST_APPROVED
                    || eventType == PunishmentRequestLifecycleEventType.REQUEST_EXTERNALLY_FULFILLED) {
                assertTrue(text.contains(PunishmentRequestAlertTestFixtures.CASE_ID.toString()));
            } else {
                assertFalse(text.contains(PunishmentRequestAlertTestFixtures.CASE_ID.toString()));
            }
        }
    }

    @Test
    void nonPendingReviewerLifecycleDoesNotOfferStaleReviewCommand() {
        PunishmentApprovalRequest request = PunishmentRequestAlertTestFixtures.request(
                PunishmentRequestLifecycleEventType.REQUEST_DENIED
        );
        PunishmentRequestAlertClaim claim = PunishmentRequestAlertTestFixtures.claim(
                PunishmentRequestLifecycleEventType.REQUEST_DENIED,
                PunishmentRequestAlertAudience.ELIGIBLE_REVIEWERS,
                PunishmentRequestAlertTestFixtures.REVIEWER_ID,
                StaffRank.MOD
        );

        Component message = renderer.render(claim, request, "Target", "Reviewer").message();

        assertNull(findClick(message));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static ClickEvent findClick(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent nested = findClick(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}
