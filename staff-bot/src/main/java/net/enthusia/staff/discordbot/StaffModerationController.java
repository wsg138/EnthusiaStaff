package net.enthusia.staff.discordbot;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    record Button(String label, String customId) {
    }

    record Choice(String label, String value) {
    }

    record Response(String content, List<Button> buttons, List<Choice> choices, Optional<String> selectCustomId) {
        Response {
            if (content == null || buttons == null || choices == null || selectCustomId == null) {
                throw new IllegalArgumentException("response fields must be present");
            }
            buttons = List.copyOf(buttons);
            choices = List.copyOf(choices);
            if (choices.isEmpty() != selectCustomId.isEmpty()) {
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
        return guarded(() -> profile(
                invokerId,
                invokerName,
                reads.discordTarget(discord(targetId)),
                SignedComponentCodec.TargetRef.discord(targetId)
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
            StaffModerationReadService.MinecraftResolution resolution = reads.resolveMinecraft(input);
            if (resolution instanceof StaffModerationReadService.MinecraftResolution.Missing) {
                return Response.text("No Minecraft player matched that UUID or current/historical username.", List.of());
            }
            if (resolution instanceof StaffModerationReadService.MinecraftResolution.Ambiguous ambiguous) {
                List<Choice> choices = ambiguous.matches().stream()
                        .map(identity -> new Choice(
                                shorten(identity.currentUsername().orElse(identity.playerId().toString()), 90),
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
                        "Multiple Minecraft identities matched. Choose one; no guess was made." + suffix,
                        choices,
                        selector
                );
            }
            StaffModerationReadService.Target target =
                    ((StaffModerationReadService.MinecraftResolution.Resolved) resolution).target();
            return profile(
                    invokerId,
                    invokerName,
                    target,
                    SignedComponentCodec.TargetRef.minecraft(target.minecraftId().orElseThrow())
            );
        });
    }

    Response linkedDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> linked(
                invokerId,
                invokerName,
                reads.discordTarget(discord(targetId)),
                SignedComponentCodec.TargetRef.discord(targetId)
        ));
    }

    Response historyDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> historyAll(
                invokerId,
                invokerName,
                reads.discordTarget(discord(targetId)),
                SignedComponentCodec.TargetRef.discord(targetId)
        ));
    }

    Response notesDiscord(long invokerId, String invokerName, long targetId) {
        return guarded(() -> notes(
                invokerId,
                invokerName,
                reads.discordTarget(discord(targetId)),
                SignedComponentCodec.TargetRef.discord(targetId)
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
        return guarded(() -> {
            SignedComponentCodec.Decoded decoded = components.decodeAndClaim(customId, invokerId);
            if (decoded.action() == SignedComponentCodec.Action.SELECT_MINECRAFT) {
                String selected = selectedValue.orElseThrow(() -> new IllegalArgumentException("selection is required"));
                return moderateMinecraft(invokerId, invokerName, selected);
            }
            if (decoded.action() == SignedComponentCodec.Action.CASE) {
                return caseView(invokerId, invokerName, decoded.target().caseId());
            }
            StaffModerationReadService.Target target = target(decoded.target());
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
        });
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
        StringBuilder content = new StringBuilder("**Moderation profile**\n");
        appendIdentity(content, snapshot);
        content.append("Active Discord sanctions: not available before Discord enforcement (D07)\n");
        content.append("Active Minecraft sanctions: ").append(snapshot.activeMinecraftSanctions().size()).append('\n');
        content.append("Recent history entries: ").append(snapshot.recentHistory().size()).append('\n');
        content.append("Recent cases: ").append(snapshot.recentCases().size()).append('\n');
        content.append("Recent notes: ").append(snapshot.recentNotes().size());
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
        StringBuilder content = new StringBuilder("**Linked accounts**\n");
        if (snapshot.linkedMinecraft().isEmpty()) {
            content.append("No current Minecraft accounts are linked. This is a Discord-only or unlinked target.\n");
        } else {
            for (StaffModerationReadService.LinkedMinecraft linked : snapshot.linkedMinecraft()) {
                content.append(linked.main() ? "• **Main:** " : "• ")
                        .append(escape(linked.username().orElse(linked.playerId().toString())))
                        .append(" — ").append(linked.platform())
                        .append(" (`").append(linked.playerId()).append("`)\n");
            }
        }
        content.append("Historical link records: ").append(snapshot.historicalLinkCount());
        return Response.text(limit(content), navigation(invokerId, targetRef));
    }

    private Response historyAll(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder("**History — All**\n");
        appendMinecraftHistory(content, snapshot);
        content.append("Discord: no Discord punishment history exists before D07; D06 does not invent entries.\n");
        content.append("Cases: ").append(snapshot.recentCases().size())
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
        String content = "**History — Discord**\n"
                + "No Discord punishment history exists before D07. D06 does not create or invent Discord sanctions.";
        return Response.text(content, historyNavigation(invokerId, targetRef));
    }

    private Response historyMinecraft(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder("**History — Minecraft**\n");
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
        StringBuilder content = new StringBuilder("**Recent staff notes**\n");
        if (snapshot.recentNotes().isEmpty()) {
            content.append("No staff notes are available for the current linked Minecraft identities.");
        } else {
            for (StaffNote note : snapshot.recentNotes()) {
                content.append("• ").append(TIME.format(note.createdAt())).append(" — ")
                        .append(shorten(escape(note.noteText()), ITEM_TEXT)).append('\n');
            }
        }
        return Response.text(limit(content), historyNavigation(invokerId, targetRef));
    }

    private Response cases(
            long invokerId,
            String invokerName,
            StaffModerationReadService.Target target,
            SignedComponentCodec.TargetRef targetRef
    ) {
        authorize(invokerId, invokerName, target, DiscordModerationOperation.VIEW_HISTORY);
        StaffModerationReadService.Snapshot snapshot = reads.snapshot(target);
        StringBuilder content = new StringBuilder("**Recent cases**\n");
        if (snapshot.recentCases().isEmpty()) {
            content.append("No cases are available for the current linked Minecraft identities.");
        } else {
            for (CaseReview review : snapshot.recentCases()) {
                content.append("• `").append(review.caseId()).append("` — ")
                        .append(review.state()).append(" — ")
                        .append(shorten(escape(review.publicReason()), ITEM_TEXT)).append('\n');
            }
        }
        return Response.text(limit(content), historyNavigation(invokerId, targetRef));
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
        String content = "**Case `" + caseId + "`**\n"
                + "State: " + review.state() + "\n"
                + "Issued: " + TIME.format(review.issuedAt()) + "\n"
                + "Reason: " + shorten(escape(review.publicReason()), 300) + "\n"
                + "Sanctions: " + review.sanctions().size();
        return Response.text(limit(new StringBuilder(content)), List.of());
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
        authorization.require(
                actor,
                targetStaff,
                operation,
                target.kind() == StaffModerationReadService.TargetKind.DISCORD
                        ? ModerationPlatform.DISCORD
                        : ModerationPlatform.MINECRAFT
        );
    }

    private StaffModerationReadService.Target target(SignedComponentCodec.TargetRef targetRef) {
        return switch (targetRef.type()) {
            case DISCORD -> reads.discordTarget(discord(targetRef.discordId()));
            case MINECRAFT -> reads.minecraftTarget(targetRef.minecraftId());
            case CASE, NONE -> throw new IllegalArgumentException("component target cannot open a moderation profile");
        };
    }

    private List<Button> navigation(long invokerId, SignedComponentCodec.TargetRef target) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(button("Refresh", SignedComponentCodec.Action.PROFILE, target, invokerId));
        buttons.add(button("History", SignedComponentCodec.Action.HISTORY, target, invokerId));
        buttons.add(button("Linked", SignedComponentCodec.Action.LINKED, target, invokerId));
        buttons.add(button("Notes", SignedComponentCodec.Action.NOTES, target, invokerId));
        buttons.add(button("Cases", SignedComponentCodec.Action.CASES, target, invokerId));
        return List.copyOf(buttons);
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
            content.append("• ").append(TIME.format(entry.occurredAt())).append(" — ")
                    .append(entry.eventType()).append(" / ").append(escape(entry.status()))
                    .append(" — ").append(shorten(escape(entry.publicReason()), ITEM_TEXT)).append('\n');
        }
    }

    private Response guarded(Supplier<Response> action) {
        try {
            return action.get();
        } catch (LinkedStaffActorResolver.MissingStaffLinkException exception) {
            return Response.text("This Discord account is not linked to a current Enthusia staff identity.", List.of());
        } catch (StaffReadAuthorization.DeniedException exception) {
            return Response.text("You are not authorized to view this moderation data.", List.of());
        } catch (SignedComponentCodec.InvalidComponentException exception) {
            return Response.text("That moderation control is expired, already used, or invalid. Open a fresh panel.", List.of());
        } catch (StaffAuthorityClient.UnavailableException exception) {
            return Response.text("Current staff authority could not be verified. Try again after the authority service recovers.", List.of());
        } catch (StaffModerationReadService.TooManyLinksException exception) {
            return Response.text("This identity has too many linked accounts for the bounded Discord view. Use Minecraft staff tools.", List.of());
        } catch (RuntimeException exception) {
            return Response.text("The read-only moderation view is temporarily unavailable.", List.of());
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
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String escape(String value) {
        return value.replace("`", "'").replace("@", "＠");
    }
}
