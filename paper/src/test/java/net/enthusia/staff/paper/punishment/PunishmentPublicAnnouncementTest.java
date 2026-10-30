package net.enthusia.staff.paper.punishment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.application.PunishmentExpectation;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class PunishmentPublicAnnouncementTest {
    @Test
    void permanentIdentityBanIsAnnouncedSimplyAsABan() {
        String visible = plain(List.of(new SanctionSpec(
                SanctionType.NETWORK_IDENTITY_BAN, SanctionLength.permanent()
        )), "Ban evasion");
        assertTrue(visible.contains("PUBLIC PUNISHMENT"));
        assertTrue(visible.contains("TestPlayer received a ban."));
        assertTrue(visible.contains("Reason: Ban evasion"));
        assertFalse(visible.toLowerCase().contains("ip ban"));
        assertFalse(visible.contains("NETWORK_IDENTITY_BAN"));
    }

    @Test
    void warningsRemainLegibleAndReasonCannotInjectMultipleLines() {
        String visible = plain(List.of(new SanctionSpec(SanctionType.WARNING, SanctionLength.instant())),
                "Cheating\nFAKE ANNOUNCEMENT");
        assertTrue(visible.contains("received a warning."));
        assertTrue(visible.contains("Reason: Cheating FAKE ANNOUNCEMENT"));
        assertFalse(visible.contains("Reason: Cheating\\n"));
    }

    @Test
    void resumedPublicDraftUsesExactPolicyVersionAndSavedSanction() {
        SanctionSpec warning = new SanctionSpec(
                SanctionType.WARNING, SanctionLength.instant());
        ReasonPolicy policy = new ReasonPolicy(
                "chat.spam", "chat", "Chat spam", 15, true,
                List.of(new PunishmentStep(0, "Warning", List.of(warning))));
        var original = new AtomicReasonPolicyRepository("policy-1", List.of(policy));
        PunishmentDraft publicDraft = draft(
                CaseVisibility.PUBLIC, "policy-1", List.of(warning));

        assertEquals("Chat spam", PunishmentPublicAnnouncement
                .publicReasonForConfirmedDraft(publicDraft, original).orElseThrow());
        assertTrue(PunishmentPublicAnnouncement
                .publicReasonForConfirmedDraft(publicDraft,
                        new AtomicReasonPolicyRepository("policy-2", List.of(policy)))
                .isEmpty(), "Never announce an old draft using a changed policy version");
        assertTrue(PunishmentPublicAnnouncement
                .publicReasonForConfirmedDraft(
                        draft(CaseVisibility.PRIVATE, "policy-1", List.of(warning)), original)
                .isEmpty(), "Private punishments never need public reason resolution");
    }

    private static PunishmentDraft draft(
            CaseVisibility visibility, String policyVersion, List<SanctionSpec> sanctions) {
        Instant now = Instant.parse("2026-10-24T06:00:00Z");
        return new PunishmentDraft(
                UUID.fromString("92000000-0000-0000-0000-000000000001"),
                UUID.fromString("92000000-0000-0000-0000-000000000002"),
                UUID.fromString("92000000-0000-0000-0000-000000000003"),
                "chat.spam", "A public chat incident", visibility, "punish",
                new PunishmentExpectation(policyVersion, 0, "Warning", sanctions),
                now, now.plus(Duration.ofMinutes(30)));
    }

    private static String plain(List<SanctionSpec> sanctions, String reason) {
        return PlainTextComponentSerializer.plainText().serialize(
                PunishmentPublicAnnouncement.message("TestPlayer", sanctions, reason)
        );
    }
}
