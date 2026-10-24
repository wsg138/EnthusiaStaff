package net.enthusia.staff.discordbot.modcase;

import java.util.Objects;
import java.util.function.Predicate;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.enthusia.staff.domain.modcase.CaseDelivery;
import net.enthusia.staff.domain.modcase.CaseLedger;
import net.enthusia.staff.domain.modcase.CaseNotice;
import net.enthusia.staff.domain.modcase.CaseStaffActions;

/** Not registered by default; requires accepted rank resolver and guild/channel binding. */
public final class JdaDiscordCaseButtonListener extends ListenerAdapter {
    private final long guildId;
    private final long channelId;
    private final long roleId;
    private final String trustedScope;
    private final DiscordCaseControls controls;
    private final CaseStaffActions actions;
    private final Predicate<Member> freshStaffAuthorization;

    public JdaDiscordCaseButtonListener(long guildId, long channelId, long roleId,
                                        String trustedScope, DiscordCaseControls controls,
                                        CaseStaffActions actions, Predicate<Member> freshStaffAuthorization) {
        if (guildId <= 0 || channelId <= 0 || roleId <= 0 || trustedScope == null || trustedScope.isBlank()) {
            throw new IllegalArgumentException("trusted Discord case interaction destination required");
        }
        this.guildId = guildId;
        this.channelId = channelId;
        this.roleId = roleId;
        this.trustedScope = trustedScope;
        this.controls = Objects.requireNonNull(controls);
        this.actions = Objects.requireNonNull(actions);
        this.freshStaffAuthorization = Objects.requireNonNull(freshStaffAuthorization);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("amc:")) {
            return;
        }
        if (!event.isFromGuild() || event.getGuild().getIdLong() != guildId
                || event.getChannel().getIdLong() != channelId) {
            return;
        }
        if (!authorized(event.getMember())) {
            event.reply("Not authorized for this private review action.").setEphemeral(true).queue();
            return;
        }
        try {
            DiscordCaseControls.Parsed request = controls.parse(event.getComponentId(), trustedScope);
            CaseDelivery.Key key = new CaseDelivery.Key(
                    request.origin(), trustedScope, request.caseId(), request.revision(),
                    CaseDelivery.Destination.DISCORD);
            String actor = "discord:" + event.getUser().getId();
            CaseLedger.Change changed = request.action() == CaseNotice.Action.CLAIM
                    ? actions.claim(key, request.revision(), actor)
                    : actions.resolve(key, request.revision(), actor);
            event.reply("Review action: " + changed.name()).setEphemeral(true).queue();
        } catch (IllegalArgumentException error) {
            event.reply("Invalid or expired review control.").setEphemeral(true).queue();
        }
    }

    private boolean authorized(Member member) {
        return member != null
                && member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId)
                && freshStaffAuthorization.test(member);
    }
}
