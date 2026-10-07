package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import org.junit.jupiter.api.Test;

class PolicyV2PublicDiscordPresentationTest {
    @Test
    void presentationConsumesCanonicalPublicProjectionOnly() {
        PolicyV2PublicDiscordView view = new PolicyV2PublicDiscordView(caseId -> Optional.of(projection()));
        MessageEmbed embed = view.find("ABCDEFGHJKMNPQRS", 0, null).orElseThrow();
        String rendered = embed.toData().toString();

        assertTrue(rendered.contains("Punishment ABCDEFGHJKMNPQRS"));
        assertTrue(rendered.contains("Repeated disruptive chat"));
        assertTrue(rendered.contains("Chat spam"));
        assertFalse(rendered.contains("actorId"));
        assertFalse(rendered.contains("appealReference"));
        assertFalse(rendered.contains("evidence"));
        assertFalse(rendered.contains("detection"));
    }

    private static PolicyV2PublicProjection projection() {
        Instant issued = Instant.parse("2026-10-07T10:00:00Z");
        return new PolicyV2PublicProjection(
                "ABCDEFGHJKMNPQRS",
                Optional.of("PlayerOne"),
                Optional.of("OldName"),
                "Chat & Spam",
                "Chat spam",
                "Repeated disruptive chat",
                Optional.of("Repeat related chat violation"),
                PolicyV2PublicProjection.Status.ACTIVE,
                PolicyV2PublicProjection.AppealStatus.AVAILABLE,
                issued,
                Optional.of(issued.plusSeconds(3600)),
                List.of(new PolicyV2PublicProjection.PublicSanction(
                        PolicyV2PublicProjection.PublicSanctionType.MUTE,
                        "Mute",
                        PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                        Optional.of(issued.plusSeconds(3600))
                )),
                List.of(),
                List.of(new PolicyV2PublicProjection.PublicRevision(
                        PolicyV2PublicProjection.RevisionType.ISSUED,
                        issued,
                        "Case issued"
                )),
                Optional.of("policy-v2-test"),
                0
        );
    }
}
