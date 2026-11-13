package net.enthusia.staff.discordbot.modcase;

import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.enthusia.staff.domain.modcase.CaseNotice;

/** Safe staff-only view: metadata, not raw messages, crisis disclosures, or punishments. */
public final class DiscordCaseCard {
    private DiscordCaseCard() {
    }

    public static String content(CaseNotice notice) {
        return "**" + notice.safeTitle() + "**\n"
                + "Case: `" + notice.caseId() + "` · Revision: " + notice.revision()
                + "\nStatus: **" + notice.state() + "** · Source: " + notice.origin()
                + "\nEvidence references: " + notice.evidenceRefs().size()
                + "\nReview privately. No automatic sanctions.";
    }

    public static List<ActionRow> controls(CaseNotice notice, DiscordCaseControls signer) {
        return List.of(ActionRow.of(
                Button.secondary(signer.encode(notice, CaseNotice.Action.CLAIM), "Claim"),
                Button.success(signer.encode(notice, CaseNotice.Action.RESOLVE), "Resolve")
        ));
    }
}
