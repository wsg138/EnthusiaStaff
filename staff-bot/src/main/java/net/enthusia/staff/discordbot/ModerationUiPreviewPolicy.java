package net.enthusia.staff.discordbot;

import java.util.List;

/** Deterministic fake policy/data source for the staging-only moderation ladder preview. */
final class ModerationUiPreviewPolicy {
    private static final String WARNING_PUNISHMENT = "Warning";
    private static final String STANDARD_LINKED_ACCOUNTS = "RiverAshMC · Java · main\nAshRiverAlt · Bedrock · linked alt";
    record HistoryEntry(
            String when,
            ModerationUiPreviewModel.Offense offense,
            String punishment,
            ModerationUiPreviewModel.Scope scope,
            boolean ladderRelevant
    ) {
        String summary() {
            return punishment + " · " + offense.label() + " · " + when;
        }
    }

    record Profile(
            String activePunishments,
            List<HistoryEntry> history,
            String accounts,
            String notes,
            String cases
    ) {
        int totalHistory() {
            return history.size();
        }

        String recentHistory() {
            return history.stream()
                    .limit(3)
                    .map(HistoryEntry::summary)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("No sample history");
        }
    }

    ModerationUiPreviewModel.Recommendation evaluate(
            ModerationUiPreviewModel.SampleScenario scenario,
            ModerationUiPreviewModel.Offense offense
    ) {
        Profile profile = profile(scenario);
        int relevant = (int) profile.history().stream()
                .filter(entry -> entry.ladderRelevant() && entry.offense() == offense)
                .count();
        int step = Math.min(4, Math.max(baseStep(offense), relevant + 1));
        Consequence consequence = consequence(offense, step);
        String explanation = explanation(offense, profile.totalHistory(), relevant, step);
        String approval = approval(consequence);
        return new ModerationUiPreviewModel.Recommendation(
                consequence.action(),
                consequence.scope(),
                consequence.duration(),
                profile.totalHistory(),
                relevant,
                step,
                explanation,
                approval
        );
    }

    Profile profile(ModerationUiPreviewModel.SampleScenario scenario) {
        return switch (scenario) {
            case FIRST_MINOR -> firstMinor();
            case REPEAT -> repeat();
            case SEVERE -> severe();
            case ADMIN_ESCALATION -> adminEscalation();
            case CUSTOM_OVERRIDE -> customOverride();
            case UNRELATED_HISTORY -> unrelatedHistory();
        };
    }

    private static int baseStep(ModerationUiPreviewModel.Offense offense) {
        return switch (offense) {
            case HATE_SLURS, CHEATING -> 3;
            case ADVERTISING -> 2;
            default -> 1;
        };
    }

    private static Consequence consequence(ModerationUiPreviewModel.Offense offense, int step) {
        if (offense == ModerationUiPreviewModel.Offense.HATE_SLURS) {
            return severeConsequence(step, ModerationUiPreviewModel.Scope.DISCORD);
        }
        if (offense == ModerationUiPreviewModel.Offense.CHEATING) {
            return severeConsequence(step, ModerationUiPreviewModel.Scope.MINECRAFT);
        }
        if (offense == ModerationUiPreviewModel.Offense.ADVERTISING) {
            return advertisingConsequence(step);
        }
        if (offense == ModerationUiPreviewModel.Offense.HARASSMENT) {
            return harassmentConsequence(step);
        }
        return standardConsequence(step);
    }

    private static Consequence severeConsequence(int step, ModerationUiPreviewModel.Scope scope) {
        return step >= 4
                ? new Consequence(ModerationUiPreviewModel.Action.BAN, scope, ModerationUiPreviewModel.PERMANENT)
                : new Consequence(ModerationUiPreviewModel.Action.BAN, scope, "30d");
    }

    private static Consequence advertisingConsequence(int step) {
        return switch (step) {
            case 1, 2 -> new Consequence(
                    ModerationUiPreviewModel.Action.KICK,
                    ModerationUiPreviewModel.Scope.DISCORD,
                    ModerationUiPreviewModel.NOT_APPLICABLE);
            case 3 -> new Consequence(
                    ModerationUiPreviewModel.Action.BAN, ModerationUiPreviewModel.Scope.DISCORD, "7d");
            default -> new Consequence(
                    ModerationUiPreviewModel.Action.BAN,
                    ModerationUiPreviewModel.Scope.DISCORD,
                    ModerationUiPreviewModel.PERMANENT);
        };
    }

    private static Consequence harassmentConsequence(int step) {
        return switch (step) {
            case 1 -> new Consequence(
                    ModerationUiPreviewModel.Action.WARN,
                    ModerationUiPreviewModel.Scope.DISCORD,
                    ModerationUiPreviewModel.NOT_APPLICABLE);
            case 2 -> new Consequence(
                    ModerationUiPreviewModel.Action.MUTE, ModerationUiPreviewModel.Scope.DISCORD, "1d");
            case 3 -> new Consequence(
                    ModerationUiPreviewModel.Action.MUTE, ModerationUiPreviewModel.Scope.DISCORD, "7d");
            default -> new Consequence(
                    ModerationUiPreviewModel.Action.BAN, ModerationUiPreviewModel.Scope.DISCORD, "30d");
        };
    }

    private static Consequence standardConsequence(int step) {
        return switch (step) {
            case 1 -> new Consequence(
                    ModerationUiPreviewModel.Action.WARN,
                    ModerationUiPreviewModel.Scope.DISCORD,
                    ModerationUiPreviewModel.NOT_APPLICABLE);
            case 2 -> new Consequence(
                    ModerationUiPreviewModel.Action.MUTE, ModerationUiPreviewModel.Scope.DISCORD, "2h");
            case 3 -> new Consequence(
                    ModerationUiPreviewModel.Action.MUTE, ModerationUiPreviewModel.Scope.DISCORD, "3d");
            default -> new Consequence(
                    ModerationUiPreviewModel.Action.BAN, ModerationUiPreviewModel.Scope.DISCORD, "30d");
        };
    }

    private static String explanation(
            ModerationUiPreviewModel.Offense offense,
            int total,
            int relevant,
            int step
    ) {
        int unrelated = total - relevant;
        String severity = baseStep(offense) > 1
                ? " This sample offense starts at ladder step " + baseStep(offense) + " because of severity."
                : "";
        return relevant + " prior " + offense.label() + " record(s) are ladder-relevant; "
                + unrelated + " other moderation record(s) stay visible but do not advance this ladder. "
                + "Result: step " + step + "." + severity;
    }

    private static String approval(Consequence consequence) {
        if (ModerationUiPreviewModel.PERMANENT.equals(consequence.duration())) {
            return "Admin+ approval required before a permanent consequence could be committed.";
        }
        return "No higher approval required by this fake recommendation.";
    }

    private static Profile firstMinor() {
        return profile(
                "Discord: none\nMinecraft: none",
                List.of(entry("73d ago", ModerationUiPreviewModel.Offense.HARASSMENT, WARNING_PUNISHMENT,
                        ModerationUiPreviewModel.Scope.DISCORD, true)),
                STANDARD_LINKED_ACCOUNTS,
                "Cooperative during prior staff contact.",
                "CASE-PREVIEW-0911 · closed · old harassment review"
        );
    }

    private static Profile repeat() {
        return profile(
                "Discord: mute · 1h 18m remaining\nMinecraft: none",
                List.of(
                        entry("9d ago", ModerationUiPreviewModel.Offense.SPAM, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("40d ago", ModerationUiPreviewModel.Offense.SPAM, "Mute · 2h",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("24d ago", ModerationUiPreviewModel.Offense.HARASSMENT, "Mute · 2h",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("61d ago", ModerationUiPreviewModel.Offense.CHEATING, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.MINECRAFT, true)
                ),
                STANDARD_LINKED_ACCOUNTS,
                "Pattern note: repeated flood behavior is documented in sample history.",
                "CASE-PREVIEW-1187 · open · spam pattern review"
        );
    }

    private static Profile severe() {
        return profile(
                "Discord: none\nMinecraft: none",
                List.of(
                        entry("12d ago", ModerationUiPreviewModel.Offense.SPAM, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("45d ago", ModerationUiPreviewModel.Offense.ADVERTISING, "Kick",
                                ModerationUiPreviewModel.Scope.DISCORD, true)
                ),
                STANDARD_LINKED_ACCOUNTS,
                "No prior hate/slur record in this sample.",
                "CASE-PREVIEW-1202 · closed · unrelated advertising review"
        );
    }

    private static Profile adminEscalation() {
        return profile(
                "Discord: mute · 6d remaining\nMinecraft: none",
                List.of(
                        entry("8d ago", ModerationUiPreviewModel.Offense.HATE_SLURS, "Ban · 30d",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("50d ago", ModerationUiPreviewModel.Offense.HATE_SLURS, "Mute · 14d",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("122d ago", ModerationUiPreviewModel.Offense.HATE_SLURS, "Mute · 7d",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("31d ago", ModerationUiPreviewModel.Offense.SPAM, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, true)
                ),
                "RiverAshMC · Java · main",
                "Management review required for permanent sample recommendation.",
                "CASE-PREVIEW-1301 · open · severe repeat behavior"
        );
    }

    private static Profile customOverride() {
        return profile(
                "Discord: none\nMinecraft: none",
                List.of(
                        entry("15d ago", ModerationUiPreviewModel.Offense.HARASSMENT, "Mute · 1d",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("52d ago", ModerationUiPreviewModel.Offense.HARASSMENT, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("20d ago", ModerationUiPreviewModel.Offense.SPAM, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, true)
                ),
                STANDARD_LINKED_ACCOUNTS,
                "Owner test: use Harassment, then choose Custom Punishment.",
                "CASE-PREVIEW-1260 · open · harassment pattern review"
        );
    }

    private static Profile unrelatedHistory() {
        return profile(
                "Discord: none\nMinecraft: restrict · 2d remaining",
                List.of(
                        entry("7d ago", ModerationUiPreviewModel.Offense.SPAM, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("13d ago", ModerationUiPreviewModel.Offense.HARASSMENT, "Mute · 1d",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("28d ago", ModerationUiPreviewModel.Offense.CHEATING, "Ban · 7d",
                                ModerationUiPreviewModel.Scope.MINECRAFT, true),
                        entry("39d ago", ModerationUiPreviewModel.Offense.ADVERTISING, "Kick",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("71d ago", ModerationUiPreviewModel.Offense.HATE_SLURS, "Mute · 7d",
                                ModerationUiPreviewModel.Scope.DISCORD, true),
                        entry("95d ago", ModerationUiPreviewModel.Offense.HARASSMENT, WARNING_PUNISHMENT,
                                ModerationUiPreviewModel.Scope.DISCORD, false)
                ),
                STANDARD_LINKED_ACCOUNTS,
                "Several unrelated records are visible; only matching ladder-relevant records count.",
                "CASE-PREVIEW-1294 · open · mixed moderation history"
        );
    }

    private static Profile profile(
            String active,
            List<HistoryEntry> history,
            String accounts,
            String notes,
            String cases
    ) {
        return new Profile(active, List.copyOf(history), accounts, notes, cases);
    }

    private static HistoryEntry entry(
            String when,
            ModerationUiPreviewModel.Offense offense,
            String punishment,
            ModerationUiPreviewModel.Scope scope,
            boolean ladderRelevant
    ) {
        return new HistoryEntry(when, offense, punishment, scope, ladderRelevant);
    }

    private record Consequence(
            ModerationUiPreviewModel.Action action,
            ModerationUiPreviewModel.Scope scope,
            String duration
    ) {
    }
}
