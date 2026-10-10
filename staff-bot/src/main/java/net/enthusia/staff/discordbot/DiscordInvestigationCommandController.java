package net.enthusia.staff.discordbot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.enthusia.staff.domain.investigation.InvestigationNote;

/** D09 Discord input parsing and deliberately bounded private response construction. */
final class DiscordInvestigationCommandController {
    private static final String CASE_CREATE = "case-create";
    private static final String NOTE_ADD = "note-add";
    private static final String NOTE_EDIT = "note-edit";
    private static final String EVASION_RESOLVE = "evasion-resolve";
    private static final String USER = "user";
    private static final String SUMMARY = "summary";
    private static final String SCOPE = "scope";
    private static final String SCOPE_ID = "scope-id";
    private static final String VISIBILITY = "visibility";
    private static final String TEXT = "text";
    private static final String NOTE_ID = "note-id";
    private static final String ALERT_ID = "alert-id";
    private static final String REVISION = "revision";
    private static final String CONTEXT_PREFIX = "d09ctx:";
    private static final int CONTEXT_TARGET_PARTS = 3;
    private static final int MAX_COMPONENT_ID_LENGTH = 100;
    private static final long INVALID_SNOWFLAKE = 0L;

    record Mutation(String content) {
    }

    record MessageCapture(String status, StaffModerationController.Button captureMore) {
    }

    record ContextTarget(long targetId, String channelId, String messageId) {
    }

    private final DiscordInvestigationService service;
    private final JdaDiscordEvidenceCollector evidence;

    DiscordInvestigationCommandController(DiscordInvestigationService service) {
        if (service == null) {
            throw new IllegalArgumentException("investigation service must be present");
        }
        this.service = service;
        this.evidence = new JdaDiscordEvidenceCollector();
    }

    boolean handlesSlash(String commandName) {
        return CASE_CREATE.equals(commandName) || NOTE_ADD.equals(commandName)
                || NOTE_EDIT.equals(commandName) || EVASION_RESOLVE.equals(commandName);
    }

    Mutation executeSlash(SlashCommandInteractionEvent event, long actorId, String actorName) {
        long targetId = event.getOption(USER).getAsUser().getIdLong();
        String token = event.getId();
        return switch (event.getName()) {
            case CASE_CREATE -> createCase(actorId, actorName, targetId, event, token);
            case NOTE_ADD -> addNote(actorId, actorName, targetId, event, token);
            case NOTE_EDIT -> editNote(actorId, actorName, targetId, event, token);
            case EVASION_RESOLVE -> resolveAlert(actorId, actorName, targetId, event);
            default -> throw new IllegalArgumentException("unknown investigation command");
        };
    }

    MessageCapture captureMessage(
            long actorId,
            String actorName,
            long targetId,
            Message focus,
            String operationToken
    ) {
        JdaDiscordEvidenceCollector.Initial initial = evidence.initial(focus);
        DiscordInvestigationService.EvidenceResult result = service.captureMessage(
                actorId, actorName, targetId, initial, operationToken
        );
        String customId = contextId(targetId, focus.getChannel().getId(), focus.getId());
        String status = "Private message evidence captured in investigation case `" + result.caseId() + "`.";
        return new MessageCapture(status, new StaffModerationController.Button("Capture more context", customId));
    }

    Mutation captureMore(
            long actorId,
            String actorName,
            ContextTarget target,
            Message focus,
            String operationToken
    ) {
        if (target == null || focus == null || !focus.getId().equals(target.messageId())) {
            throw new IllegalArgumentException("capture-more target is invalid");
        }
        int captured = service.captureMoreContext(
                actorId, actorName, target.targetId(), evidence.toDomain(focus), evidence.additional(focus), operationToken
        );
        return new Mutation("Captured " + captured + " additional private context message(s).");
    }

    boolean recordEdit(Message message) {
        return service.recordCapturedMessageEdit(evidence.toDomain(message));
    }

    StaffModerationController.Response decorate(
            StaffModerationController.Response response,
            MessageCapture capture
    ) {
        List<StaffModerationController.Button> buttons = new ArrayList<>(response.buttons().stream().limit(4).toList());
        buttons.add(capture.captureMore());
        return StaffModerationController.Response.text(response.content() + "\n\n" + capture.status(), buttons);
    }

    static boolean isCaptureMore(String customId) {
        return customId != null && customId.startsWith(CONTEXT_PREFIX);
    }

    static ContextTarget contextTarget(String customId) {
        if (!isCaptureMore(customId)) {
            throw new IllegalArgumentException("not a capture-more control");
        }
        String[] parts = customId.substring(CONTEXT_PREFIX.length()).split(":", -1);
        if (parts.length != CONTEXT_TARGET_PARTS) {
            throw new IllegalArgumentException("capture-more control is malformed");
        }
        return new ContextTarget(parseUnsigned(parts[0]), snowflake(parts[1]), snowflake(parts[2]));
    }

    static List<CommandData> commands(DefaultMemberPermissions discovery) {
        return List.of(caseCommand(discovery), noteAddCommand(discovery), noteEditCommand(discovery), alertCommand(discovery));
    }

    private Mutation createCase(
            long actorId,
            String actorName,
            long targetId,
            SlashCommandInteractionEvent event,
            String token
    ) {
        DiscordInvestigationService.CaseResult result = service.createCase(
                actorId, actorName, targetId, string(event, SUMMARY), token
        );
        return new Mutation("Private investigation case created: `" + result.caseId() + "`.");
    }

    private Mutation addNote(
            long actorId,
            String actorName,
            long targetId,
            SlashCommandInteractionEvent event,
            String token
    ) {
        DiscordInvestigationService.NoteResult result = service.addNote(
                actorId, actorName, targetId,
                scopeType(string(event, SCOPE)), optionalString(event, SCOPE_ID),
                visibility(string(event, VISIBILITY)), string(event, TEXT), token
        );
        return new Mutation("Private note saved: `" + result.noteId() + "` revision " + result.revision() + ".");
    }

    private Mutation editNote(
            long actorId,
            String actorName,
            long targetId,
            SlashCommandInteractionEvent event,
            String token
    ) {
        DiscordInvestigationService.NoteResult result = service.editNote(
                actorId, actorName, targetId, uuid(string(event, NOTE_ID)), integer(event, REVISION),
                string(event, TEXT), token
        );
        return new Mutation("Private note updated to revision " + result.revision() + ".");
    }

    private Mutation resolveAlert(
            long actorId,
            String actorName,
            long targetId,
            SlashCommandInteractionEvent event
    ) {
        DiscordInvestigationService.AlertResult result = service.resolveAlert(
                actorId, actorName, targetId, uuid(string(event, ALERT_ID)), integer(event, REVISION)
        );
        return resolved(result);
    }

    Mutation resolveAlert(long actorId, String actorName, long targetId, UUID alertId) {
        return resolved(service.resolveAlert(actorId, actorName, targetId, alertId));
    }

    private static Mutation resolved(DiscordInvestigationService.AlertResult result) {
        return new Mutation("Linked-alt investigation alert `" + result.alertId() + "` resolved.");
    }

    private static CommandData caseCommand(DefaultMemberPermissions discovery) {
        return Commands.slash(CASE_CREATE, "Create a private investigation-only case")
                .addOptions(userOption(), stringOption(SUMMARY, "Private case summary", true))
                .setDefaultPermissions(discovery);
    }

    private static CommandData noteAddCommand(DefaultMemberPermissions discovery) {
        return Commands.slash(NOTE_ADD, "Add a versioned private investigation note")
                .addOptions(
                        userOption(),
                        stringOption(SCOPE, "subject, discord, minecraft, or case", true),
                        stringOption(VISIBILITY, "staff or management", true),
                        stringOption(TEXT, "Private note text", true),
                        stringOption(SCOPE_ID, "Minecraft UUID or canonical case ID when required", false)
                )
                .setDefaultPermissions(discovery);
    }

    private static CommandData noteEditCommand(DefaultMemberPermissions discovery) {
        return Commands.slash(NOTE_EDIT, "Edit a private investigation note with revision checking")
                .addOptions(
                        userOption(),
                        stringOption(NOTE_ID, "Exact private note UUID", true),
                        new OptionData(OptionType.INTEGER, REVISION, "Expected note revision", true),
                        stringOption(TEXT, "Replacement private note text", true)
                )
                .setDefaultPermissions(discovery);
    }

    private static CommandData alertCommand(DefaultMemberPermissions discovery) {
        return Commands.slash(EVASION_RESOLVE, "Resolve a reviewed linked-alt investigation alert")
                .addOptions(
                        userOption(),
                        stringOption(ALERT_ID, "Exact linked-alt alert UUID", true),
                        new OptionData(OptionType.INTEGER, REVISION, "Expected alert revision", true)
                )
                .setDefaultPermissions(discovery);
    }

    private static OptionData userOption() {
        return new OptionData(OptionType.USER, USER, "Discord subject", true);
    }

    private static OptionData stringOption(String name, String description, boolean required) {
        return new OptionData(OptionType.STRING, name, description, required);
    }

    private static String string(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        if (option == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return option.getAsString();
    }

    private static String optionalString(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        return option == null ? "" : option.getAsString();
    }

    private static long integer(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        if (option == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        long value = option.getAsLong();
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static InvestigationNote.ScopeType scopeType(String raw) {
        return switch (normalized(raw)) {
            case "subject" -> InvestigationNote.ScopeType.SUBJECT;
            case "discord" -> InvestigationNote.ScopeType.DISCORD_USER;
            case "minecraft" -> InvestigationNote.ScopeType.MINECRAFT_PLAYER;
            case "case" -> InvestigationNote.ScopeType.CASE;
            default -> throw new IllegalArgumentException("unknown private note scope");
        };
    }

    private static InvestigationNote.Visibility visibility(String raw) {
        return switch (normalized(raw)) {
            case "staff" -> InvestigationNote.Visibility.STAFF;
            case "management" -> InvestigationNote.Visibility.MANAGEMENT;
            default -> throw new IllegalArgumentException("unknown private note visibility");
        };
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("UUID input is invalid", exception);
        }
    }

    private static String contextId(long targetId, String channelId, String messageId) {
        String id = CONTEXT_PREFIX + Long.toUnsignedString(targetId) + ':' + snowflake(channelId) + ':' + snowflake(messageId);
        if (id.length() > MAX_COMPONENT_ID_LENGTH) {
            throw new IllegalArgumentException("capture-more control exceeds Discord component limit");
        }
        return id;
    }

    private static long parseUnsigned(String value) {
        try {
            long parsed = Long.parseUnsignedLong(value);
            if (parsed == INVALID_SNOWFLAKE) {
                throw new IllegalArgumentException("Discord user id must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Discord user id is invalid", exception);
        }
    }

    private static String snowflake(String value) {
        try {
            long parsed = Long.parseUnsignedLong(value);
            if (parsed == INVALID_SNOWFLAKE) {
                throw new IllegalArgumentException("Discord snowflake must be positive");
            }
            return Long.toUnsignedString(parsed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Discord snowflake is invalid", exception);
        }
    }
}
