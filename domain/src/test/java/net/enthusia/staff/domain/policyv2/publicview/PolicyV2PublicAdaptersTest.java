package net.enthusia.staff.domain.policyv2.publicview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.website.PublicPunishmentState;
import org.junit.jupiter.api.Test;

final class PolicyV2PublicAdaptersTest {
    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");

    @Test
    void apiAdapterUsesExactPublicAllowlist() {
        Map<String, Object> response = PolicyV2PublicApiAdapter.toPublicMap(projection());

        assertEquals(Set.of(
                "caseId", "player", "historicalName", "category", "offense", "reason",
                "relatedHistory", "status", "appealStatus", "issuedAt", "expiresAt",
                "sanctions", "remedies", "timeline", "policyVersion", "revision"
        ), response.keySet());
        String rendered = response.toString().toLowerCase(java.util.Locale.ROOT);
        assertFalse(rendered.contains("actor"));
        assertFalse(rendered.contains("evidence"));
        assertFalse(rendered.contains("appealreference"));
        assertFalse(rendered.contains("network_identity"));
    }

    @Test
    void discordAdapterConsumesOnlyCanonicalProjection() {
        PolicyV2PublicDiscordAdapter.Message message =
                PolicyV2PublicDiscordAdapter.toMessage(projection());

        assertEquals("Punishment ABCDEFGHJKMNPQRS", message.title());
        assertTrue(message.description().contains("disruptive chat"));
        assertTrue(message.fields().stream().anyMatch(field -> field.name().equals("Offense")));
    }

    @Test
    void legacyWebsiteAdapterPreservesRepresentablePublicPunishment() {
        var legacy = PolicyV2LegacyPublicPunishmentAdapter.toLegacy(projection(), NOW);

        assertEquals(1, legacy.size());
        assertEquals("MUTE", legacy.getFirst().punishmentType());
        assertEquals(PublicPunishmentState.ACTIVE, legacy.getFirst().state());
        assertTrue(legacy.getFirst().appealAvailable());
    }

    private static PolicyV2PublicProjection projection() {
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
                NOW,
                Optional.of(NOW.plusSeconds(3600)),
                List.of(new PolicyV2PublicProjection.PublicSanction(
                        PolicyV2PublicProjection.PublicSanctionType.MUTE,
                        "Mute",
                        PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                        Optional.of(NOW.plusSeconds(3600))
                )),
                List.of(new PolicyV2PublicProjection.PublicRemedy(
                        PolicyV2PublicProjection.PublicRemedyType.CONTENT_REMOVAL,
                        "Content removal",
                        PolicyV2PublicProjection.PublicRemedy.RemedyStatus.SATISFIED
                )),
                List.of(
                        new PolicyV2PublicProjection.PublicRevision(
                                PolicyV2PublicProjection.RevisionType.ISSUED,
                                NOW,
                                "Case issued"
                        ),
                        new PolicyV2PublicProjection.PublicRevision(
                                PolicyV2PublicProjection.RevisionType.REMEDY_UPDATED,
                                NOW.plusSeconds(30),
                                "Content removal satisfied"
                        )
                ),
                Optional.of("policy-v2-test"),
                2
        );
    }
}
