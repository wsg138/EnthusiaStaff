package net.enthusia.staff.domain.guided;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GuidedIncidentWizardTest {
    private static final String CHAT = "chat";
    private static final String MEDIUM = "medium";
    private static final String CONDUCT = "conduct";
    private static final String EXTENT = "extent";
    private static final String CONTINUED = "continued";
    private static final String FEW = "few";
    private static final String SPAM = "spam";
    private static final String NO_WARNING = "not-applicable";
    private static GuidedIncidentWizard spam(String extent, String warning) {
        return GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat")
                .answer(CONDUCT, SPAM)
                .answer(EXTENT, extent)
                .answer(CONTINUED, warning);
    }

    @Test
    void normalChatFlowStartsWithPlaceNotSpecificOffense() {
        var flow = GuidedIncidentWizard.start(CHAT);
        assertEquals(MEDIUM, flow.nextQuestion().orElseThrow().id());
        flow = flow.answer(MEDIUM, "public-chat");
        assertEquals(CONDUCT, flow.nextQuestion().orElseThrow().id());
        flow = flow.answer(CONDUCT, SPAM);
        assertEquals(EXTENT, flow.nextQuestion().orElseThrow().id());
        flow = flow.answer(EXTENT, FEW);
        assertEquals(CONTINUED, flow.nextQuestion().orElseThrow().id());
        assertTrue(flow.nextQuestion().orElseThrow().choices().stream()
                .anyMatch(x -> x.id().equals(NO_WARNING)));
    }

    @Test
    void unknownWarningDoesNotInventDefianceOrRequireReviewAlone() {
        var unknownWarning = spam(FEW, "unknown").finding();
        var noWarning = spam(FEW, NO_WARNING).finding();
        assertEquals(noWarning.preliminaryImpact(), unknownWarning.preliminaryImpact());
        assertFalse(unknownWarning.needsReview());
    }

    @Test
    void noWarningDoesNotInflateImpact() {
        var notWarned = spam(FEW, NO_WARNING).finding();
        var stopped = spam(FEW, "no").finding();
        var ignored = spam(FEW, "yes").finding();
        assertEquals(notWarned.preliminaryImpact(), stopped.preliminaryImpact());
        assertTrue(ignored.preliminaryImpact() > notWarned.preliminaryImpact());
        assertEquals("spam.low-level", ignored.candidateId());
    }

    @Test
    void answerChangeClearsAllDependentAnswers() {
        var flow = spam("sustained", "yes");
        flow = flow.answer(CONDUCT, "slur");
        assertFalse(flow.answers().containsKey(EXTENT));
        assertFalse(flow.answers().containsKey(CONTINUED));
        assertEquals("detail", flow.nextQuestion().orElseThrow().id());
        flow = flow.answer("detail", "full");
        assertEquals("targeting", flow.nextQuestion().orElseThrow().id());
        assertThrows(IllegalStateException.class, flow::finding);
    }

    @Test
    void otherCategoriesSelectConfiguredReasonOrRequireHighRiskReview() {
        for (String category : new String[]{"gameplay","safety","accounts","community","economy"}) {
            var flow = GuidedIncidentWizard.start(category).answer(MEDIUM, "in-game");
            var conduct = flow.nextQuestion().orElseThrow();
            assertEquals(CONDUCT, conduct.id());
            String value = conduct.choices().get(0).id();
            flow = flow.answer(CONDUCT, value);
            while (flow.nextQuestion().isPresent()) {
                var q = flow.nextQuestion().orElseThrow();
                String answer = q.id().equals(CONTINUED) ? NO_WARNING : q.choices().get(0).id();
                flow = flow.answer(q.id(), answer);
            }
            if ("safety".equals(category)) {
                assertTrue(flow.finding().needsReview());
            } else {
                assertFalse(flow.finding().needsReview(), category);
            }
            assertTrue(flow.finding().candidateId() != null, category);
        }
    }

    @Test
    void placedTextCannotSilentlyUsePublicChatOnlyRules() {
        var placed = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "sign").answer(CONDUCT, "communication")
                .answer("detail", "language").answer(EXTENT, FEW)
                .answer(CONTINUED, NO_WARNING).finding();
        assertTrue(placed.needsReview());
        assertEquals("language.non-english-public-chat", placed.candidateId());
    }

    @Test
    void signSpamCannotBecomeAChatMuteWithoutReview() {
        var flow = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "sign").answer(CONDUCT, SPAM)
                .answer(EXTENT, "sustained").answer(CONTINUED, NO_WARNING)
                .answer("persistence", "persistent");
        assertTrue(flow.finding().needsReview());
        assertNull(flow.finding().candidateId());
    }

    @Test
    void harassmentOutsideOrdinaryChatOrDirectMessagesRequiresReview() {
        for (String medium : new String[]{"sign", "book", "voice", "profile"}) {
            var finding = GuidedIncidentWizard.start(CHAT)
                    .answer(MEDIUM, medium).answer(CONDUCT, "harassment")
                    .answer("detail", "verbal").answer("targeting", "targeted")
                    .answer(EXTENT, FEW).answer(CONTINUED, NO_WARNING).finding();
            assertEquals("hate.targeted-harassment", finding.candidateId());
            assertTrue(finding.needsReview(), medium);
        }
        var directMessage = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "direct-message").answer(CONDUCT, "harassment")
                .answer("detail", "verbal").answer("targeting", "targeted")
                .answer(EXTENT, FEW).answer(CONTINUED, NO_WARNING).finding();
        assertFalse(directMessage.needsReview());
    }

    @Test
    void backNavigationAndChoiceValidationAreFailClosed() {
        var flow = GuidedIncidentWizard.start(CHAT);
        assertThrows(IllegalArgumentException.class, () -> flow.answer(CONDUCT, SPAM));
        assertThrows(IllegalArgumentException.class, () -> flow.answer(MEDIUM, "fake"));
        assertEquals(flow.answers(), flow.back().answers());
        var first = flow.answer(MEDIUM, "public-chat");
        assertTrue(first.back().answers().isEmpty());
    }

    @Test
    void voiceNoiseRequiresReviewUntilVoiceEnforcementIsVerified() {
        var voice = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "voice").answer(CONDUCT, SPAM)
                .answer(EXTENT, "sustained").answer(CONTINUED, NO_WARNING)
                .finding();
        assertEquals("spam.noise-pollution", voice.candidateId(),
                "Keep the configured reason for audited manual handling");
        assertTrue(voice.needsReview(),
                "Do not issue a Minecraft chat mute as an unverified voice sanction");
        var normalChat = spam("sustained", NO_WARNING).finding();
        assertFalse(normalChat.needsReview(),
                "Ordinary public chat spam remains eligible for the guided workflow");
    }

    @Test
    void unknownLocationStopsAtConductForManualReview() {
        var incident = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "unknown").answer(CONDUCT, SPAM);
        assertTrue(incident.nextQuestion().isEmpty());
        assertTrue(incident.finding().needsReview());
        assertFalse(incident.answers().containsKey(EXTENT));
        assertFalse(incident.answers().containsKey(CONTINUED));
    }

    @Test
    void unknownDetailSkipsUnneededSeverityQuestions() {
        var incident = GuidedIncidentWizard.start("gameplay")
                .answer(MEDIUM, "in-game").answer(CONDUCT, "cheating")
                .answer("detail", "other");
        assertTrue(incident.nextQuestion().isEmpty());
        assertTrue(incident.finding().needsReview());
        assertFalse(incident.answers().containsKey(EXTENT));
    }

    @Test
    void unknownTargetStopsBeforeExtentButValidTargetStillAsksExtent() {
        var base = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, "harassment")
                .answer("detail", "verbal");
        var unknown = base.answer("targeting", "unknown");
        assertTrue(unknown.nextQuestion().isEmpty());
        assertTrue(unknown.finding().needsReview());
        assertFalse(unknown.answers().containsKey(EXTENT));
        assertEquals("targeting", unknown.back().nextQuestion().orElseThrow().id());
        assertEquals(EXTENT, unknown.back().answer("targeting", "targeted")
                .nextQuestion().orElseThrow().id());
    }

    @Test
    void unknownExtentSkipsWarningQuestionWithoutFabricatingAggravation() {
        var incident = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, SPAM)
                .answer(EXTENT, "unknown");
        assertTrue(incident.nextQuestion().isEmpty());
        assertTrue(incident.finding().needsReview());
        assertEquals("spam.low-level", incident.finding().candidateId());
        assertEquals(10, incident.finding().preliminaryImpact());
        assertFalse(incident.answers().containsKey(CONTINUED));
        assertEquals(EXTENT, incident.back().nextQuestion().orElseThrow().id());
        assertEquals(CONTINUED, incident.back().answer(EXTENT, "sustained")
                .nextQuestion().orElseThrow().id());
    }

    @Test
    void protectedIncidentPathsStopAfterRelevantDetails() {
        var fullSlur = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, "slur")
                .answer("detail", "full").answer("targeting", "targeted");
        assertTrue(fullSlur.nextQuestion().isEmpty());
        assertEquals(4, fullSlur.answers().size());
        assertEquals("hate.full-slur-targeted", fullSlur.finding().candidateId());
        assertTrue(fullSlur.finding().needsReview());
        assertFalse(fullSlur.answers().containsKey(EXTENT));

        var hate = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, "hate")
                .answer("detail", "advocacy");
        assertTrue(hate.nextQuestion().isEmpty());
        assertEquals(3, hate.answers().size());
        assertEquals("hate.advocating-hate-violence", hate.finding().candidateId());
        assertTrue(hate.finding().needsReview());

        var sexual = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, "harassment")
                .answer("detail", "sexual");
        assertTrue(sexual.nextQuestion().isEmpty());
        assertEquals("harassment.sexual", sexual.finding().candidateId());
        assertTrue(sexual.finding().needsReview());

        var malicious = GuidedIncidentWizard.start("community")
                .answer(MEDIUM, "discord").answer(CONDUCT, "advertising")
                .answer("detail", "malicious");
        assertTrue(malicious.nextQuestion().isEmpty());
        assertEquals("advertising.malicious-link", malicious.finding().candidateId());
        assertTrue(malicious.finding().needsReview());

        var crash = GuidedIncidentWizard.start("gameplay")
                .answer(MEDIUM, "in-game").answer(CONDUCT, "exploit")
                .answer("detail", "crash");
        assertTrue(crash.nextQuestion().isEmpty());
        assertEquals("exploit.server-crash-attempt", crash.finding().candidateId());
        assertTrue(crash.finding().needsReview());
    }

    @Test
    void ordinaryHarassmentRetainsIntensityAndWarningQuestions() {
        var normal = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, "harassment")
                .answer("detail", "verbal").answer("targeting", "targeted");
        assertEquals(EXTENT, normal.nextQuestion().orElseThrow().id());
        normal = normal.answer(EXTENT, "sustained");
        assertEquals(CONTINUED, normal.nextQuestion().orElseThrow().id());
        assertTrue(normal.answer(CONTINUED, NO_WARNING).finding().candidateId()
                .equals("hate.targeted-harassment"));
    }

    @Test
    void targetedSlurCanBeReclassifiedFromManualReviewByEditingEarlierAnswer() {
        var slur = GuidedIncidentWizard.start(CHAT)
                .answer(MEDIUM, "public-chat").answer(CONDUCT, "slur")
                .answer("detail", "full").answer("targeting", "targeted");
        assertTrue(slur.nextQuestion().isEmpty());
        var amended = slur.back().answer("targeting", "general");
        assertEquals("hate.full-slur-untargeted", amended.finding().candidateId());
        assertTrue(amended.finding().needsReview());
        assertFalse(amended.answers().containsKey(EXTENT));
    }
}
