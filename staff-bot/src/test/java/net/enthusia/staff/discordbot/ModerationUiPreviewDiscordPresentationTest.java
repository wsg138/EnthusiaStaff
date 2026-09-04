package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class ModerationUiPreviewDiscordPresentationTest {
    private final ModerationUiPreviewDiscordPresentation presentation = new ModerationUiPreviewDiscordPresentation();
    private final ModerationUiPreviewPolicy policy = new ModerationUiPreviewPolicy();

    @Test
    void profileFitsDiscordComponentLimitsAndShowsUsefulHistoryBeforePunish() {
        ModerationUiPreviewModel.Snapshot snapshot = snapshot(ModerationUiPreviewModel.State.initial());

        ModerationUiPreviewDiscordPresentation.Rendered rendered = presentation.render(snapshot);

        assertEquals(3, rendered.rows().size());
        assertEquals(5, rendered.rows().getFirst().getComponents().size());
        assertTrue(rendered.embed().getTitle().contains("Moderation profile"));
        assertTrue(fieldValue(rendered.embed(), "Active punishments").contains("Discord"));
        assertTrue(fieldValue(rendered.embed(), "Recent moderation").contains("Spam"));
        assertTrue(fieldValue(rendered.embed(), "History context").contains("total moderation"));
        assertTrue(rendered.embed().getFooter().getText().contains("No moderation action is possible"));
        assertTrue(ModerationUiPreviewController.componentId(snapshot, "punish", "").length() <= 100);
        assertTrue(ModerationUiPreviewController.componentId(snapshot, "nav", "accounts").length() <= 100);
        assertTrue(ModerationUiPreviewController.componentId(snapshot, "sample", "").length() <= 100);
    }

    @Test
    void recommendationScreenMakesPolicyPathPrimaryAndCustomPathSecondary() {
        ModerationUiPreviewModel.State state = stateWithRecommendation(
                ModerationUiPreviewModel.SampleScenario.REPEAT,
                ModerationUiPreviewModel.Offense.SPAM);
        ModerationUiPreviewDiscordPresentation.Rendered rendered = presentation.render(snapshot(state));

        assertEquals("Punishment ladder recommendation", rendered.embed().getTitle());
        assertTrue(fieldValue(rendered.embed(), "History").contains("Relevant previous offenses: 2"));
        assertTrue(fieldValue(rendered.embed(), "Recommended punishment").contains("Mute"));
        List<String> labels = rendered.rows().getFirst().getComponents().stream()
                .map(Button.class::cast)
                .map(Button::getLabel)
                .toList();
        assertEquals(List.of("Apply Recommendation", "Custom Punishment", "Back"), labels);
    }

    @Test
    void confirmationClearlyDistinguishesFollowedRecommendationFromOverride() {
        ModerationUiPreviewModel.State recommended = stateWithRecommendation(
                ModerationUiPreviewModel.SampleScenario.REPEAT,
                ModerationUiPreviewModel.Offense.SPAM).applyRecommendation()
                .withScreen(ModerationUiPreviewModel.Screen.CONFIRM);
        MessageEmbed recommendedEmbed = presentation.render(snapshot(recommended)).embed();
        assertEquals("Followed recommendation", fieldValue(recommendedEmbed, "Recommendation status"));
        assertTrue(fieldValue(recommendedEmbed, "Relevant history / ladder").contains("2 relevant of 4 total"));

        ModerationUiPreviewModel.State custom = stateWithRecommendation(
                ModerationUiPreviewModel.SampleScenario.CUSTOM_OVERRIDE,
                ModerationUiPreviewModel.Offense.HARASSMENT)
                .beginCustom()
                .withCustomAction(ModerationUiPreviewModel.Action.KICK)
                .withCustomScope(ModerationUiPreviewModel.Scope.DISCORD)
                .withScreen(ModerationUiPreviewModel.Screen.CONFIRM);
        MessageEmbed customEmbed = presentation.render(snapshot(custom)).embed();
        assertEquals("Overridden — custom punishment", fieldValue(customEmbed, "Recommendation status"));
        assertTrue(fieldValue(customEmbed, "Recommended punishment").contains("Mute"));
        assertTrue(fieldValue(customEmbed, "Actual selected punishment").contains("Kick"));
    }

    @Test
    void completionUsesRequiredNonDestructiveMessage() {
        ModerationUiPreviewModel.State complete = ModerationUiPreviewModel.State.initial()
                .withScreen(ModerationUiPreviewModel.Screen.COMPLETE);
        MessageEmbed embed = presentation.render(snapshot(complete)).embed();

        assertTrue(embed.getDescription().contains("Preview complete — no moderation action was applied."));
        assertTrue(fieldValue(embed, "Result").contains("No Discord moderation REST action"));
        assertTrue(fieldValue(embed, "Result").contains("LiteBans"));
    }

    @Test
    void commandIsDedicatedPreviewCommand() {
        assertEquals("moderate-preview", JdaModerationUiPreviewListener.command().getName());
    }

    private ModerationUiPreviewModel.State stateWithRecommendation(
            ModerationUiPreviewModel.SampleScenario scenario,
            ModerationUiPreviewModel.Offense offense
    ) {
        ModerationUiPreviewModel.Recommendation recommendation = policy.evaluate(scenario, offense);
        return ModerationUiPreviewModel.State.initial()
                .withSampleScenario(scenario)
                .beginPunish()
                .withRecommendation(offense, offense.label(), recommendation);
    }

    private static ModerationUiPreviewModel.Snapshot snapshot(ModerationUiPreviewModel.State state) {
        return new ModerationUiPreviewModel.Snapshot("abcdefghijklmnop", 0, state);
    }

    private static String fieldValue(MessageEmbed embed, String name) {
        return embed.getFields().stream()
                .filter(field -> name.equals(field.getName()))
                .map(MessageEmbed.Field::getValue)
                .findFirst()
                .orElseThrow();
    }
}
