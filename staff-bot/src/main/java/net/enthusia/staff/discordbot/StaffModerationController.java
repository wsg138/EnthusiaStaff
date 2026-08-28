package net.enthusia.staff.discordbot;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.Actor;
import net.enthusia.staff.domain.auth.DiscordModerationOperation;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.moderation.DiscordUserId;
import net.enthusia.staff.domain.moderation.ModerationPlatform;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;

/** Pure D06 interaction/controller layer. It never invokes a destructive moderation port. */
final class StaffModerationController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_CONTENT = 1_900;
    private static final int ITEM_TEXT = 180;
    private static final int AMBIGUOUS_LABEL_LIMIT = 90;
    private static final int CASE_REASON_LIMIT = 300;
    private static final String GENERIC_UNAVAILABLE = "The read-only moderation view is temporarily unavailable.";
    private static final String ITEM_SEPARATOR = " — ";
    private static final Map<Class<?>, String> DENIAL_MESSAGES = Map.of(
            LinkedStaffActorResolver.MissingStaffLinkException.class,
            "This Discord account is not linked to a current Enthusia staff identity.",
            StaffReadAuthorization.DeniedException.class,
            "You are not authorized to view this moderation data.",
            SignedComponentCodec.InvalidComponentException.class,
            "That moderation control is expired, already used, or invalid. Open a fresh panel.",
            StaffAuthorityClient.UnavailableException.class,
            "Current staff authority could not be verified. Try again after the authority service recovers.",
            StaffModerationReadService.TooManyLinksException.class,
            "This identity has too many linked accounts for the bounded Discord view. Use Minecraft staff tools."
    );

    record Button(String label, String customId) {
    }

    record Choice(String label, String value) {
    }

    record Response(String content, List<Button> buttons, List<Choice> choices, Optional<String> selectCustomId) {
        Response(String content, List<Button> buttons, List<Choice> choices, Optional<String> selectCustomId) {
            if (content == null || buttons == null || choices == null || selectCustomId == null) {
                throw new IllegalArgumentException("response fields must be present");
            }
            this.content = content;
            this.buttons = List.copyOf(buttons);
            this.choices = List.copyOf(choices);
            this.selectCustomId = selectCustomId;
            if (this.choices.isEmpty() != this.selectCustomId.isEmpty()) {
                throw new IllegalArgumentException("selector choices and component ID must appear together");
            }
        }

        static Response text(String content, List<Button> buttons) {
            return new Response(content, buttons, List.of(), Optional.empty());
        }

        static Response selector(String content, List<Choice> choices, String customId) {
            return new Response(content, List.of(), choices, Optional.of(customId));
        }
    }

    @FunctionalInterface
    private interface TargetRenderer {
        Response render(
                long invokerId,
                String invokerName,
                StaffModerationReadService.Target target,
                SignedComponentCodec.TargetRef targetRef
        );
    }

    private final StaffModerationReadService reads;
    private final LinkedStaffActorResolver actors;
    private final StaffReadAuthorization authorization;
    private final SignedComponentCodec components;

    StaffModerationController(
            StaffModerationReadService reads,
            LinkedStaffActorResolver actors,
            StaffReadAuthorization authorization,
            SignedComponentCodec components
    ) {
        if (reads == null || actors == null || authorization == null || components == null) {
            throw new IllegalArgumentException("controller dependencies must be present");
        }
        this.reads = reads;
        this.actors = actors;
        this.authorization = authorization;
        this.components = components;
    }

    Response moderateDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> discordView(
                invokerId,
                invokerName,
                targetId,
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                this::profile
        ));
    }

    Response moderateMinecraft(long invokerId, String invokerName, String input) {
        return guarded(() -> {
            requireInvoker(
                    invokerId,
                    invokerName,
                    DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                    ModerationPlatform.MINECRAFT
            );
            return resolvedMinecraft(invokerId, invokerName, reads.resolveMinecraft(input));
        });
    }

    Response linkedDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> discordView(
                invokerId,
                invokerName,
                targetId,
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                this::linked
        ));
    }

    Response historyDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> discordView(
                invokerId,
                invokerName,
                targetId,
                DiscordModerationOperation.VIEW_HISTORY,
                this::historyAll
        ));
    }

    Response notesDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> discordView(
                invokerId,
                invokerName,
                targetId,
                DiscordModerationOperation.VIEW_NOTES,
                this::notes
        ));
    }

    Response caseView(long invokerId, String invokerName, String rawCaseId) {
        return guarded(() -> caseView(invokerId, invokerName, new CaseId(rawCaseId)));
    }

    Response component(
            long invokerId,
            String invokerName,
            String customId,
            Optional<String> selectedValue
    ) {
        return guarded(() -> componentRead(invokerId, invokerName, customId, selectedValue));
    }

    private Response discordView(
            long invokerId,
            String invokerName,
            long targetId,
            DiscordModerationOperation operation,
            TargetRenderer renderer
    ) {
        requireInvoker(invokerId, invokerName, operation, ModerationPlatform.DISCORD);
        StaffModerationReadService.Target target = reads.discordTarget(discord(targetId));
        return renderer.render(
                invokerId,
                invokerName,
                target,
                SignedComponentCodec.TargetRef.discord(targetId)
        );
    }

    private Response componentRead(
            long invokerId,
            String invokerName,
            String customId,
            Optional<String> selectedValue
    ) {
        SignedComponentCodec.Decoded decoded = components.decodeAndClaim(customId, invokerId);
        if (decoded.action() == SignedComponentCodec.Action.SELECT_MINECRAFT) {
            return selectedMinecraft(invokerId, invokerName, selectedValue);
        }
        if (decoded.action() == SignedComponentCodec.Action.CASE) {
            return caseView(invokerId, invokerName, decoded.target().caseId());
        }
        preauthorizeComponent(invokerId, invokerName, decoded);
        StaffModerationReadService.Target target = target(decoded.target());
        return renderComponent(invokerId, invokerName, decoded, target);
    }

    private Response selectedMinecraft(long invokerId, String invokerName, Optional<String> selectedValue) {
        requireInvoker(
                invokerId,
                invokerName,
                DiscordModerationOperation.VIEW_LINKED_ACCOUNTS,
                ModerationPlatform.MINECRAFT
        );
        String selected = selectedValue.orElseThrow(() -> new IllegalArgumentException("selection is required"));
        return resolvedMinecraft(invokerId, invokerName, reads.resolveMinecraft(selected));
    }

    private Response resolvedMinecraft(
            long invokerId,
            String invokerName,
            StaffModerationReadService.MinecraftResolution resolution
    ) {
        if (resolution instanceof StaffModerationReadService.MinecraftResolution.Missing) {
            return Response.text("No Minecraft player matched that UUID or current/historical username.", List.of());
        }
        if (resolution instanceof StaffModerationReadService.MinecraftResolution.Ambiguous ambiguous) {
            return ambiguitySelector(invokerId, ambiguous);
        }
        StaffModerationReadService.Target target =
                ((StaffModerationReadService.MinecraftResolution.Resolved) resolution).target();
        return profile(
                invokerId,
                invokerName,
                target,
                SignedComponentCodec.TargetRef.minecraft(target.minecraftId().orElseThrow())
        );
    }

    private Response ambiguitySelector(
            long invokerId,
            StaffModerationReadService.MinecraftResolution.Ambiguous ambiguous
    ) {
        List<Choice> choices = ambiguous.matches().stream()
                .map(identity -> new Choice(
                        shorten(
                                identity.currentUsername().orElse(identity.playerId().toString()),
                                AMBIGUOUS_LABEL_LIMIT
                        ),
                        identity.playerId().toString()
                ))
                .toList();
        String selector = components.encode(
                SignedComponentCodec.Action.SELECT_MINECRAFT,
                SignedComponentCodec.TargetRef.none(),
                invokerId
        );
        String suffix = ambiguous.truncated() ? " More matches exist; narrow the username." : "";
        return Response.selector(
                "Multiple Minecraft identities matched. Choose one; no guess was made.".concat(suffix),
                choices,
                selector
        );
    }

    private Response renderComponent(
            long invokerId,
            String invokerName,
            SignedComponentCodec.Decoded decoded,
            StaffModerationReadService.Target target
    ) {
        return switch (decoded.action()) {
            case PROFILE -> profile(invokerId, invokerName, target, decoded.target());
            case HISTORY -> historyAll(invokerId, invokerName, target, decoded.target());
            case HISTORY_DISCORD -> historyDiscordOnly(invokerId, invokerName, target, decoded.target());
            case HISTORY_MINECRAFT -> historyMinecraft(invokerId, invokerName, target, decoded.target());
            case LINKED -> linked(invokerId, invokerName, target, decoded.target());
            case NOTES -> notes(invokerId, invokerName, target, decoded.target());
            case CASES -> cases(invokerId, invokerName, target, decoded.target());
            case SELECT_MINECRAFT, CASE -> throw new IllegalStateException("component action already handled");
        };
    }

    private void preauthorizeComponent(
            long invokerId,
            String invokerName,
            SignedComponentCodec.Decoded decoded
    ) {
        requireInvoker(
                invokerId,
                invokerName,
                componentOperation(decoded.action()),
                componentPlatform(decoded.target().type())
        );
    }

    private static DiscordModerationOperation componentOperation(SignedComponentCodec.Action action) {
        return switch (action) {
            case PROFILE, LINKED -> DiscordModerationOperation.VIEW_LINKED_ACCOUNTS;
            case NOTES -> DiscordModerationOperation.VIEW_NOTES;
            case HISTORY, HISTORY_DISCORD, HISTORY_MINECRAFT, CASES -> DiscordModerationOperation.VIEW_HISTORY;
            case SELECT_MINECRAFT, CASE -> throw new IllegalArgumentException("component action must be handled first");
        };
    }

    private static ModerationPlatform componentPlatform(SignedComponentCodec.TargetType type) {
        return switch (type) {
            case DISCORD -> ModerationPlatform.DISCORD;
            case MINECRAFT -> ModerationPlatform.MINECRAFT;
            case CASE, NONE -> throw new IllegalArgumentException("component target cannot open a moderation profile");
        };
    }

    private Response profile(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        Actor actor = authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_LINKED_ACCOUNTS);
        Optional<Actor> targetStaff = actors.targetStaff(target);
        require(actor, targetStaff, DiscordModerationOperation.VIEW_HISTORY, target);
        require(actor, targetStaff, DiscordModerationOperation.VIEW_NOTES, target);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Moderation profile**\n");
        appendIdentity(content, snapshot);
        content.append("Active Discord sanctions: not available before Discord enforcement (D07)\nActive Minecraft sanctions: ")
                .append(snapshot.activeMinecraftSanctions().size())
                .append("\nRecent history entries: ").append(snapshot.recentHistory().size())
                .append("\nRecent cases: ").append(snapshot.recentCases().size())
                .append("\nRecent notes: ").append(snapshot.recentNotes().size());
        return Response.text(limit(content), navigation(invokerId, targetRef));
    }

    private Response linked(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_LINKED_ACCOUNTS);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Linked accounts**\n");
        appendLinkedAccounts(content, snapshot);
        content.append("Historical link records: ").append(snapshot.historicalLinkCount());
        return Response.text(limit(content), navigation(invokerId, targetRef));
    }

    private static void appendLinkedAccounts(
            StringBuilder content,
            StaffModerationReadService.Snapshot snapshot
    ) {
        if (snapshot.linkedMinecraft().isEmpty()) {
            content.append("No current Minecraft accounts are linked. This is a Discord-only or unlinked target.\n");
            return;
        }
        for (StaffModerationReadService.LinkedMinecraft linked : snapshot.linkedMinecraft()) {
            content.append(linked.main() ? "• **Main:** " : "• ")
                    .append(escape(linked.username().orElse(linked.playerId().toString())))
                    .append(ITEM_SEPARATOR).append(linked.platform())
                    .append(" (`").append(linked.playerId()).append("`)\n");
        }
    }

    private Response historyAll(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**History — All**\n");
        appendMinecraftHistory(content, snapshot);
        content.append("Discord: no Discord punishment history exists before D07; D06 does not invent entries.\nCases: ")
                .append(snapshot.recentCases().size())
                .append(" | Notes: ").append(snapshot.recentNotes().size());
        return Response.text(limit(content), historyNavigation(invokerId, targetRef));
    }

    private Response historyDiscordOnly(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        return Response.text(
                "**History — Discord**\nNo Discord punishment history exists before D07. "
                        + "D06 does not create or invent Discord sanctions.",
                historyNavigation(invokerId, targetRef)
        );
    }

    private Response historyMinecraft(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**History — Minecraft**\n");
        appendMinecraftHistory(content, snapshot);
        return Response.text(limit(content), historyNavigation(invokerId, targetRef));
    }

    private Response notes(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_NOTES);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Recent staff notes**\n");
        appendNotes(content, snapshot);
        return Response.text(limit(content), historyNavigation(invokerId, targetRef));
    }

    private static void appendNotes(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        if (snapshot.recentNotes().isEmpty()) {
            content.append("No staff notes are available for the current linked Minecraft identities.");
            return;
        }
        for (StaffNote note : snapshot.recentNotes()) {
            content.append("• ").append(TIME.format(note.createdAt())).append(ITEM_SEPARATOR)
                    .append(shorten(escape(note.noteText()), ITEM_TEXT)).append('\n');
        }
    }

    private Response cases(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Recent cases**\n");
        appendCases(content, snapshot);
        return Response.text(limit(content), historyNavigation(invokerId, targetRef));
    }

    private static void appendCases(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        if (snapshot.recentCases().isEmpty()) {
            content.append("No cases are available for the current linked Minecraft identities.");
            return;
        }
        for (CaseReview review : snapshot.recentCases()) {
            content.append("• `").append(review.caseId()).append("` — ")
                    .append(review.state()).append(ITEM_SEPARATOR)
                    .append(shorten(escape(review.publicReason()), ITEM_TEXT)).append('\n');
        }
    }

    private Response caseView(long invokerId, String invokerName, CaseId caseId) {
        Actor actor = requireInvoker(
                invokerId,
                invokerName,
                DiscordModerationOperation.VIEW_HISTORY,
                ModerationPlatform.MINECRAFT
        );
        CaseReview review = reads.caseReview(caseId).orElse(null);
        if (review == null) {
            return Response.text("No case exists with that ID.", List.of());
        }
        StaffModerationReadService.Target target = reads.minecraftTarget(review.targetId());
        require(actor, actors.targetStaff(target), DiscordModerationOperation.VIEW_HISTORY, target);
        StringBuilder content = new StringBuilder(MAX_CONTENT).append("**Case `").append(caseId).append("`**\n")
                .append("State: ").append(review.state()).append('\n')
                .append("Issued: ").append(TIME.format(review.issuedAt())).append('\n')
                .append("Reason: ").append(shorten(escape(review.publicReason()), CASE_REASON_LIMIT)).append('\n')
                .append("Sanctions: ").append(review.sanctions().size());
        return Response.text(limit(content), List.of());
    }

    private Actor authorize(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            DiscordModerationOperation operation
    ) {
        Actor actor = actors.invoker(discord(invokerId), invokerName);
        require(actor, actors.targetStaff(target), operation, target);
        return actor;
    }

    private Actor requireInvoker(
            long invokerId,
            String invokerName,
            DiscordModerationOperation operation,
            ModerationPlatform platform
    ) {
        Actor actor = actors.invoker(discord(invokerId), invokerName);
        authorization.require(actor, Optional.empty(), operation, platform);
        return actor;
    }

    private void require(
            Actor actor,
            Optional<Actor> targetStaff,
            DiscordModerationOperation operation,
            StaffModerationReadService.Target target
    ) {
        authorization.require(actor, targetStaff, operation, platform(target));
    }

    private static ModerationPlatform platform(StaffModerationReadService.Target target) {
        return target.kind() == StaffModerationReadService.TargetKind.DISCORD
                ? ModerationPlatform.DISCORD
                : ModerationPlatform.MINECRAFT;
    }

    private StaffModerationReadService.Target target(SignedComponentCodec.TargetRef targetRef) {
        return switch (targetRef.type()) {
            case DISCORD -> reads.discordTarget(discord(targetRef.discordId()));
            case MINECRAFT -> reads.minecraftTarget(targetRef.minecraftId());
            case CASE, NONE -> throw new IllegalArgumentException("component target cannot open a moderation profile");
        };
    }

    private List<Button> navigation(long invokerId, SignedComponentCodec.TargetRef target) {
        return List.of(
                button("Refresh", SignedComponentCodec.Action.PROFILE, target, invokerId),
                button("History", SignedComponentCodec.Action.HISTORY, target, invokerId),
                button("Linked", SignedComponentCodec.Action.LINKED, target, invokerId),
                button("Notes", SignedComponentCodec.Action.NOTES, target, invokerId),
                button("Cases", SignedComponentCodec.Action.CASES, target, invokerId)
        );
    }

    private List<Button> historyNavigation(long invokerId, SignedComponentCodec.TargetRef target) {
        return List.of(
                button("All", SignedComponentCodec.Action.HISTORY, target, invokerId),
                button("Discord", SignedComponentCodec.Action.HISTORY_DISCORD, target, invokerId),
                button("Minecraft", SignedComponentCodec.Action.HISTORY_MINECRAFT, target, invokerId),
                button("Cases", SignedComponentCodec.Action.CASES, target, invokerId),
                button("Notes", SignedComponentCodec.Action.NOTES, target, invokerId)
        );
    }

    private Button button(
            String label,
            SignedComponentCodec.Action action,
            SignedComponentCodec.TargetRef target,
            long invokerId
    ) {
        return new Button(label, components.encode(action, target, invokerId));
    }

    private static void appendIdentity(StringBuilder content, StaffModerationReadService.Snapshot snapshot) {
        snapshot.target().discordId().ifPresentOrElse(
                id -> content.append("Discord ID: `").append(id.value()).append("`\n"),
                () -> content.append("Discord: not currently linked\n")
        );
        Optional<StaffModerationReadService.LinkedMinecraft> main = snapshot.linkedMinecraft().stream()
                .filter(StaffModerationReadService.LinkedMinecraft::main)
                .findFirst();
        main.ifPresentOrElse(
                linked -> content.append("Main Minecraft: ")
                        .append(escape(linked.username().orElse(linked.playerId().toString())))
                        .append(" (").append(linked.platform()).append(")\n"),
                () -> content.append("Main Minecraft: none\n")
        );
        content.append("Linked Minecraft accounts: ").append(snapshot.linkedMinecraft().size()).append('\n');
    }

    private static void appendMinecraftHistory(
            StringBuilder content,
            StaffModerationReadService.Snapshot snapshot
    ) {
        if (snapshot.recentHistory().isEmpty()) {
            content.append("Minecraft: no moderation history is available for the current linked identities.\n");
            return;
        }
        for (ModerationHistoryEntry entry : snapshot.recentHistory()) {
            content.append("• ").append(TIME.format(entry.occurredAt())).append(ITEM_SEPARATOR)
                    .append(entry.eventType()).append(" / ").append(escape(entry.status()))
                    .append(ITEM_SEPARATOR).append(shorten(escape(entry.publicReason()), ITEM_TEXT)).append('\n');
        }
    }

    private Response guarded(Supplier<Response> action) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            String message = DENIAL_MESSAGES.getOrDefault(exception.getClass(), GENERIC_UNAVAILABLE);
            return Response.text(message, List.of());
        }
    }

    private static DiscordUserId discord(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Discord id must be positive");
        }
        return new DiscordUserId(Long.toString(id));
    }

    private static String limit(StringBuilder content) {
        return shorten(content.toString(), MAX_CONTENT);
    }

    private static String shorten(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)).concat("…");
    }

    private static String escape(String value) {
        return value.replace("`", "'").replace("@", "＠");
    }
}
