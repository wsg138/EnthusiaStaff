package net.enthusia.staff.discordbot;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.auth.DiscordConsequenceType;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.discord.DiscordPunishmentTermination;
import net.enthusia.staff.domain.discord.DiscordRestrictionTarget;
import net.enthusia.staff.domain.sanction.SanctionLength;

/** Validation and response projection shared by D07 quick commands and moderation-panel actions. */
final class DiscordPunishmentCommandController {
    private static final String CONFIRM_ISSUE_PREFIX = "d07ci:";
    private static final String CONFIRM_REMOVE_PREFIX = "d07cr:";
    private static final String PANEL_ROOT_PREFIX = "d07p:r:";
    private static final String PANEL_ACTION_PREFIX = "d07p:";
    private static final String PANEL_MODAL_PREFIX = "d07m:";

    enum PanelAction {
        WARNING("w", "Warn"),
        KICK("k", "Kick"),
        MUTE_ONE_HOUR("m1", "Mute 1h"),
        BAN_ONE_DAY("b1", "Ban 1d");

        private final String code;
        private final String label;

        PanelAction(String code, String label) {
            this.code = code;
            this.label = label;
        }

        String label() {
            return label;
        }

        static PanelAction parse(String code) {
            for (PanelAction action : values()) {
                if (action.code.equals(code)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("unknown punishment panel action");
        }
    }

    record PanelButton(String label, String customId) {
    }

    record PanelDraft(PanelAction action, long targetId) {
    }

    record Prepared(String content, String confirmationCustomId) {
    }

    record Committed(String content) {
    }

    private final DiscordPunishmentService service;
    private final DiscordDurationParser durations;

    DiscordPunishmentCommandController(DiscordPunishmentService service) {
        if (service == null) {
            throw new IllegalArgumentException("punishment service must be present");
        }
        this.service = service;
        this.durations = new DiscordDurationParser();
    }

    Prepared warn(long actorId, String actorName, long targetId, String reason, String explanation) {
        return prepareIssue(actorId, actorName, targetId, new DiscordPunishmentIntent(
                DiscordConsequenceType.WARNING,
                SanctionLength.instant(),
                false,
                false,
                Optional.empty(),
                reason,
                explanation,
                0,
                true
        ));
    }

    Prepared mute(
            long actorId,
            String actorName,
            long targetId,
            String duration,
            String reason,
            String explanation
    ) {
        DiscordDurationParser.Parsed parsed = durations.parse(duration, true);
        return prepareIssue(actorId, actorName, targetId, new DiscordPunishmentIntent(
                DiscordConsequenceType.MUTE,
                parsed.length(),
                parsed.custom(),
                false,
                Optional.empty(),
                reason,
                explanation,
                0,
                true
        ));
    }

    Prepared kick(long actorId, String actorName, long targetId, String reason, String explanation) {
        return prepareIssue(actorId, actorName, targetId, new DiscordPunishmentIntent(
                DiscordConsequenceType.KICK,
                SanctionLength.instant(),
                false,
                false,
                Optional.empty(),
                reason,
                explanation,
                0,
                true
        ));
    }

    Prepared ban(
            long actorId,
            String actorName,
            long targetId,
            String duration,
            String reason,
            String explanation,
            int messageDeleteSeconds
    ) {
        DiscordDurationParser.Parsed parsed = durations.parse(duration, true);
        return prepareIssue(actorId, actorName, targetId, new DiscordPunishmentIntent(
                DiscordConsequenceType.BAN,
                parsed.length(),
                parsed.custom(),
                false,
                Optional.empty(),
                reason,
                explanation,
                messageDeleteSeconds,
                true
        ));
    }

    Prepared restrict(
            long actorId,
            String actorName,
            long targetId,
            String duration,
            String reason,
            String explanation,
            String scopeKind,
            String scopeId,
            String mode
    ) {
        DiscordDurationParser.Parsed parsed = durations.parse(duration, true);
        DiscordRestrictionTarget restriction = new DiscordRestrictionTarget(
                restrictionKind(scopeKind),
                scopeId,
                restrictionMode(mode)
        );
        return prepareIssue(actorId, actorName, targetId, new DiscordPunishmentIntent(
                DiscordConsequenceType.CHANNEL_RESTRICTION,
                parsed.length(),
                parsed.custom(),
                false,
                Optional.of(restriction),
                reason,
                explanation,
                0,
                true
        ));
    }

    Prepared remove(long actorId, String actorName, long targetId, DiscordConsequenceType type) {
        DiscordPunishmentService.Confirmation confirmation = service.prepareRemoval(
                actorId,
                actorName,
                targetId,
                type,
                DiscordPunishmentTermination.END
        );
        return new Prepared(
                "Confirm ending the active Discord " + display(type) + " for <@" + confirmation.targetUserId() + ">.",
                CONFIRM_REMOVE_PREFIX + confirmation.token()
        );
    }

    Prepared preparePanel(PanelDraft draft, long actorId, String actorName, String reason) {
        if (draft == null) {
            throw new IllegalArgumentException("panel draft must be present");
        }
        return switch (draft.action()) {
            case WARNING -> warn(actorId, actorName, draft.targetId(), reason, "");
            case KICK -> kick(actorId, actorName, draft.targetId(), reason, "");
            case MUTE_ONE_HOUR -> mute(actorId, actorName, draft.targetId(), "1h", reason, "");
            case BAN_ONE_DAY -> ban(actorId, actorName, draft.targetId(), "1d", reason, "", 0);
        };
    }

    Committed confirm(long actorId, String actorName, String customId) {
        if (customId == null) {
            throw new IllegalArgumentException("confirmation id must be present");
        }
        DiscordPunishmentService.MutationResult result;
        if (customId.startsWith(CONFIRM_ISSUE_PREFIX)) {
            result = service.confirmIssue(actorId, actorName, token(customId, CONFIRM_ISSUE_PREFIX));
        } else if (customId.startsWith(CONFIRM_REMOVE_PREFIX)) {
            result = service.confirmRemoval(actorId, actorName, token(customId, CONFIRM_REMOVE_PREFIX));
        } else {
            throw new IllegalArgumentException("unknown punishment confirmation");
        }
        return new Committed("Discord moderation intent accepted. Enforcement state: " + result.state() + ".");
    }

    static String panelRootId(long targetId) {
        requirePositive(targetId);
        return PANEL_ROOT_PREFIX + Long.toUnsignedString(targetId);
    }

    static boolean isPanelRoot(String customId) {
        return customId != null && customId.startsWith(PANEL_ROOT_PREFIX);
    }

    static boolean isPanelAction(String customId) {
        return customId != null && customId.startsWith(PANEL_ACTION_PREFIX) && !isPanelRoot(customId);
    }

    static boolean isPanelModal(String customId) {
        return customId != null && customId.startsWith(PANEL_MODAL_PREFIX);
    }

    static boolean isConfirmation(String customId) {
        return customId != null
                && (customId.startsWith(CONFIRM_ISSUE_PREFIX) || customId.startsWith(CONFIRM_REMOVE_PREFIX));
    }

    static long panelRootTarget(String customId) {
        if (!isPanelRoot(customId)) {
            throw new IllegalArgumentException("not a punishment panel root");
        }
        return parseUnsigned(customId.substring(PANEL_ROOT_PREFIX.length()));
    }

    static List<PanelButton> panelButtons(long targetId) {
        requirePositive(targetId);
        String target = Long.toUnsignedString(targetId);
        return java.util.Arrays.stream(PanelAction.values())
                .map(action -> new PanelButton(
                        action.label(), PANEL_ACTION_PREFIX + action.code + ":" + target
                ))
                .toList();
    }

    static PanelDraft panelDraft(String customId) {
        return parsePanel(customId, PANEL_ACTION_PREFIX);
    }

    static String modalId(PanelDraft draft) {
        return PANEL_MODAL_PREFIX + draft.action().code + ":" + Long.toUnsignedString(draft.targetId());
    }

    static PanelDraft modalDraft(String customId) {
        return parsePanel(customId, PANEL_MODAL_PREFIX);
    }

    private Prepared prepareIssue(
            long actorId,
            String actorName,
            long targetId,
            DiscordPunishmentIntent intent
    ) {
        DiscordPunishmentService.Confirmation confirmation = service.prepareIssue(
                actorId, actorName, targetId, intent
        );
        return new Prepared(
                "Confirm Discord " + display(confirmation.type()) + " for <@" + confirmation.targetUserId()
                        + "> (" + confirmation.duration() + ").",
                CONFIRM_ISSUE_PREFIX + confirmation.token()
        );
    }

    private static PanelDraft parsePanel(String customId, String prefix) {
        if (customId == null || !customId.startsWith(prefix)) {
            throw new IllegalArgumentException("invalid punishment panel control");
        }
        String[] parts = customId.substring(prefix.length()).split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("invalid punishment panel control");
        }
        return new PanelDraft(PanelAction.parse(parts[0]), parseUnsigned(parts[1]));
    }

    private static UUID token(String customId, String prefix) {
        try {
            return UUID.fromString(customId.substring(prefix.length()));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid punishment confirmation token", exception);
        }
    }

    private static DiscordRestrictionTarget.Kind restrictionKind(String value) {
        return switch (normalized(value)) {
            case "channel" -> DiscordRestrictionTarget.Kind.CHANNEL;
            case "category" -> DiscordRestrictionTarget.Kind.CATEGORY;
            default -> throw new IllegalArgumentException("restriction scope kind must be channel or category");
        };
    }

    private static DiscordRestrictionTarget.Mode restrictionMode(String value) {
        return switch (normalized(value)) {
            case "read-only", "readonly" -> DiscordRestrictionTarget.Mode.READ_ONLY;
            case "no-access", "noaccess" -> DiscordRestrictionTarget.Mode.NO_ACCESS;
            default -> throw new IllegalArgumentException("restriction mode must be read-only or no-access");
        };
    }

    private static String normalized(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String display(DiscordConsequenceType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static long parseUnsigned(String value) {
        try {
            long parsed = Long.parseUnsignedLong(value);
            requirePositive(parsed);
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid Discord user id", exception);
        }
    }

    private static void requirePositive(long value) {
        if (value == 0L) {
            throw new IllegalArgumentException("Discord user id must be positive");
        }
    }
}
