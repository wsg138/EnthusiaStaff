package net.enthusia.staff.domain.policyv2.publicview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

final class PolicyV2PublicProjectionTest {
    private static final Instant NOW = Instant.parse("2026-10-07T01:00:00Z");

    @Test
    void canonicalProjectionHasOnlyReviewedPublicFields() {
        Set<String> fields = Arrays.stream(PolicyV2PublicProjection.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(Set.of(
                "caseId", "currentPlayerName", "incidentPlayerName", "category",
                "publicOffense", "publicReason", "relatedHistorySummary", "status",
                "appealStatus", "issuedAt", "expiresAt", "sanctions", "remedies",
                "timeline", "policyVersion", "revision"
        ), fields);
        assertNoPrivateFieldNames(PolicyV2PublicProjection.class);
        assertNoPrivateFieldNames(PolicyV2PublicProjection.PublicSanction.class);
        assertNoPrivateFieldNames(PolicyV2PublicProjection.PublicRemedy.class);
        assertNoPrivateFieldNames(PolicyV2PublicProjection.PublicRevision.class);
    }

    @Test
    void sensitiveSanctionVariantsCollapseToPublicBan() {
        var sanction = new PolicyV2PublicProjection.PublicSanction(
                SanctionType.NETWORK_IDENTITY_BAN,
                PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                Optional.empty()
        );

        assertEquals(PolicyV2PublicProjection.PublicSanctionType.BAN, sanction.type());
        assertEquals("Ban", sanction.summary());
        assertTrue(!sanction.toString().contains("IDENTITY"));
    }

    @Test
    void remedyLikeSanctionsCannotCrossThePublicSanctionBoundary() {
        assertThrows(IllegalArgumentException.class, () ->
                new PolicyV2PublicProjection.PublicSanction(
                        SanctionType.CONTENT_REMOVAL,
                        PolicyV2PublicProjection.PublicSanction.SanctionStatus.COMPLETED,
                        Optional.empty()
                ));
    }

    @Test
    void compatibilityConstructorKeepsPermanentActiveCasesOpenEnded() {
        PolicyV2PublicProjection projection = new PolicyV2PublicProjection(
                "ABCDEFGHJKMNPQRS",
                "Cheating",
                "Confirmed cheating violation",
                PolicyV2PublicProjection.Status.ACTIVE,
                NOW,
                List.of(
                        new PolicyV2PublicProjection.PublicSanction(
                                SanctionType.BAN,
                                PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                                Optional.empty()
                        ),
                        new PolicyV2PublicProjection.PublicSanction(
                                SanctionType.MUTE,
                                PolicyV2PublicProjection.PublicSanction.SanctionStatus.ACTIVE,
                                Optional.of(NOW.plusSeconds(3600))
                        )
                ),
                0
        );

        assertTrue(projection.expiresAt().isEmpty());
    }

    @Test
    void publicCollectionsAreImmutable() {
        PolicyV2PublicProjection projection = sample();

        assertThrows(UnsupportedOperationException.class, () -> projection.timeline().clear());
        assertThrows(UnsupportedOperationException.class, () -> projection.sanctions().clear());
    }

    private static PolicyV2PublicProjection sample() {
        return new PolicyV2PublicProjection(
                "ABCDEFGHJKMNPQRS",
                Optional.of("PlayerOne"),
                Optional.empty(),
                "Chat & Spam",
                "Chat spam",
                "Repeated disruptive chat",
                Optional.empty(),
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
                List.of(),
                List.of(new PolicyV2PublicProjection.PublicRevision(
                        PolicyV2PublicProjection.RevisionType.ISSUED,
                        NOW,
                        "Case issued"
                )),
                Optional.of("policy-v2-test"),
                0
        );
    }

    private static void assertNoPrivateFieldNames(Class<?> recordType) {
        Set<String> forbidden = Set.of(
                "ip", "alt", "evidence", "actor", "note", "internal", "detection",
                "attribute", "historyinput", "operation", "appealreference", "uuid"
        );
        for (RecordComponent component : recordType.getRecordComponents()) {
            String normalized = component.getName().toLowerCase(java.util.Locale.ROOT);
            assertTrue(forbidden.stream().noneMatch(normalized::contains), component.getName());
        }
    }
}
