package net.enthusia.staff.discordbot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.history.ModerationHistoryEntry;
import net.enthusia.staff.domain.ports.StaffNoteStore.StaffNote;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

/** Maps already-authorized D06/JDA read state into explicit browser DTO allowlists. */
final class ModerationReadSnapshotMapper {
    private final JDA jda;
    private final MinecraftProfileLookup minecraftProfiles;

    ModerationReadSnapshotMapper(JDA jda) {
        this(jda, MinecraftProfileLookup.mojang());
    }

    ModerationReadSnapshotMapper(JDA jda, MinecraftProfileLookup minecraftProfiles) {
        if (jda == null || minecraftProfiles == null) {
            throw new IllegalArgumentException("snapshot mapper dependencies must be present");
        }
        this.jda = jda;
        this.minecraftProfiles = minecraftProfiles;
    }

    ModerationReadApiModel.ActorDto actor(ModerationReadContext context) {
        return new ModerationReadApiModel.ActorDto(
                Long.toUnsignedString(context.actorId()), context.actorMember().getEffectiveName());
    }

    ModerationReadApiModel.IdentityDto identity(
            ModerationReadContext context,
            StaffModerationReadService.Snapshot snapshot,
            List<ModerationReadApiModel.LinkedAccountDto> linkedAccounts
    ) {
        long userId = context.readTarget().userId().orElseThrow();
        User user = jda.retrieveUserById(userId).complete();
        Member member = ModerationDiscordMessageMapper.memberIfPresent(context.guild(), userId);
        Optional<String> main = linkedAccounts.stream()
                .filter(ModerationReadApiModel.LinkedAccountDto::main)
                .map(account -> account.username().orElse(account.playerId()))
                .findFirst();
        String status = snapshot.activeMinecraftSanctions().isEmpty() ? "No active sanctions" : "Active moderation";
        return new ModerationReadApiModel.IdentityDto(
                user.getId(), user.getName(), Optional.ofNullable(user.getGlobalName()),
                Optional.ofNullable(member == null ? null : member.getEffectiveName()),
                member == null ? ModerationDiscordMessageMapper.displayName(user) : member.getEffectiveName(),
                user.getEffectiveAvatarUrl(), snapshot.target().subject().isPresent() ? "Linked" : "Unlinked",
                main, status);
    }

    List<ModerationReadApiModel.LinkedAccountDto> linked(StaffModerationReadService.Snapshot snapshot) {
        List<StaffModerationReadService.LinkedMinecraft> linked = snapshot.linkedMinecraft();
        Map<UUID, MinecraftProfileLookup.Profile> profiles = minecraftProfiles.profiles(
                linked.stream().map(StaffModerationReadService.LinkedMinecraft::playerId).toList());
        return linked.stream().map(account -> linked(account, profiles.get(account.playerId()))).toList();
    }

    List<ModerationReadApiModel.SanctionDto> sanctions(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.activeMinecraftSanctions().stream().map(this::sanction).toList();
    }

    List<ModerationReadApiModel.HistoryDto> history(StaffModerationReadService.Snapshot snapshot) {
        Map<String, CaseReview> cases = new HashMap<>();
        snapshot.recentCases().forEach(review -> cases.put(review.caseId().toString(), review));
        return snapshot.recentHistory().stream().map(entry -> history(entry, cases)).toList();
    }

    List<ModerationReadApiModel.FamilyCountDto> relevantHistoryCounts(
            StaffModerationReadService.Snapshot snapshot
    ) {
        return snapshot.relevantHistoryCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ModerationReadApiModel.FamilyCountDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    List<ModerationReadApiModel.CaseDto> cases(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.recentCases().stream().map(review -> new ModerationReadApiModel.CaseDto(
                review.caseId().toString(), review.publicReason(), review.exactReasonId(), review.sanctionFamily(),
                review.state().name(), review.issuedAt(), review.actorName())).toList();
    }

    List<ModerationReadApiModel.NoteDto> notes(StaffModerationReadService.Snapshot snapshot) {
        return snapshot.recentNotes().stream().map(this::note).toList();
    }

    private ModerationReadApiModel.LinkedAccountDto linked(
            StaffModerationReadService.LinkedMinecraft account,
            MinecraftProfileLookup.Profile profile
    ) {
        Optional<String> username = profile == null ? account.username() : Optional.of(profile.username());
        Optional<String> skinTextureUrl = profile == null ? Optional.empty() : profile.skinTextureUrl();
        return new ModerationReadApiModel.LinkedAccountDto(
                account.playerId().toString(), username, skinTextureUrl, account.platform().name(), account.main());
    }

    private ModerationReadApiModel.SanctionDto sanction(ActiveSanction sanction) {
        return new ModerationReadApiModel.SanctionDto(
                sanction.sanctionId().toString(), sanction.caseId().toString(), sanction.type().name(),
                sanction.publicReason(), sanction.issuedAt(), sanction.expiresAt());
    }

    private ModerationReadApiModel.HistoryDto history(
            ModerationHistoryEntry entry,
            Map<String, CaseReview> cases
    ) {
        CaseReview review = entry.caseId().map(id -> cases.get(id.toString())).orElse(null);
        return new ModerationReadApiModel.HistoryDto(
                entry.stableKey(), entry.eventType().name(), entry.occurredAt(), entry.caseId().map(Object::toString),
                entry.punishmentType(), entry.status(), entry.publicReason(), entry.actorName(),
                Optional.ofNullable(review).map(CaseReview::exactReasonId),
                Optional.ofNullable(review).map(CaseReview::sanctionFamily));
    }

    private ModerationReadApiModel.NoteDto note(StaffNote note) {
        return new ModerationReadApiModel.NoteDto(
                note.noteId().toString(), note.noteText(), note.createdAt(), note.actorId().toString());
    }
}
