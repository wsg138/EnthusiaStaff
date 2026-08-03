package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.casefile.CaseReview;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.casefile.PunishmentStepReview;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import net.enthusia.staff.persistence.ModerationPersistenceException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentRecommendationSnapshotIntegrationTest extends PunishmentRequestMariaDbSupport {
    @Test
    void storesConfiguredRecommendationSeparatelyFromAppliedOverrideAcrossRestart() {
        CaseId caseId = new CaseId("5300000000000001");
        List<SanctionSpec> recommendation = sevenDayBan();
        List<SanctionSpec> applied = thirtyDayBan();

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(caseId, recommendation, applied));
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            CaseReview review = runtime.caseReviewStore().find(caseId).orElseThrow();
            PunishmentStepReview step = review.punishmentStep().orElseThrow();
            assertEquals("snapshot-v1", review.configurationVersion());
            assertEquals(8, step.rawOrdinal());
            assertEquals(8, step.effectiveOrdinal());
            assertEquals(Optional.of(2), step.selectedOrdinal());
            assertEquals("Seven day recommendation", step.label());
            assertEquals(recommendation, step.recommendedSanctions().orElseThrow());
            assertEquals(
                    NOW.plus(Duration.ofDays(30)),
                    review.sanctions().getFirst().expirationAt().orElseThrow()
            );
        }
    }

    @Test
    void legacyNullSnapshotRemainsExplicitlyUnavailable() throws Exception {
        CaseId caseId = new CaseId("5300000000000002");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(caseId, sevenDayBan(), sevenDayBan()));
            clearSnapshot(caseId);

            PunishmentStepReview step = runtime.caseReviewStore().find(caseId)
                    .orElseThrow()
                    .punishmentStep()
                    .orElseThrow();
            assertFalse(step.selectedOrdinal().isPresent());
            assertFalse(step.recommendedSanctions().isPresent());
        }
    }

    @Test
    void databaseRejectsIncompleteStoredSnapshot() {
        CaseId caseId = new CaseId("5300000000000003");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(caseId, sevenDayBan(), sevenDayBan()));

            assertThrows(SQLException.class, () -> clearSelectedOrdinal(caseId));
        }
    }

    @Test
    void corruptStoredSnapshotFailsClosed() throws Exception {
        CaseId caseId = new CaseId("5300000000000004");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(caseId, sevenDayBan(), sevenDayBan()));
            setRecommendation(caseId, "[]");

            assertThrows(
                    ModerationPersistenceException.class,
                    () -> runtime.caseReviewStore().find(caseId)
            );
        }
    }

    private static PunishmentPlan plan(
            CaseId caseId,
            List<SanctionSpec> recommendation,
            List<SanctionSpec> applied
    ) {
        PunishmentStep selected = new PunishmentStep(2, "Seven day recommendation", recommendation);
        return new PunishmentPlan(
                caseId,
                new IdempotencyKey("recommendation-snapshot:" + caseId.value()),
                identifier("recommendation-snapshot-target-" + caseId.value()),
                MOD,
                "test.snapshot",
                "test",
                "Snapshot test",
                "Applied override remains separate",
                "snapshot-v1",
                CaseVisibility.PUBLIC,
                NOW,
                new EscalationDecision(8, 8, 0, List.of(), DecayEligibility.ELIGIBLE, selected),
                applied
        );
    }

    private static void clearSnapshot(CaseId caseId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE punishment_steps
                     SET selected_ordinal = NULL, recommended_sanctions_json = NULL
                     WHERE case_id = ?
                     """)) {
            statement.setString(1, caseId.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void clearSelectedOrdinal(CaseId caseId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE punishment_steps SET selected_ordinal = NULL WHERE case_id = ?
                     """)) {
            statement.setString(1, caseId.value());
            statement.executeUpdate();
        }
    }

    private static void setRecommendation(CaseId caseId, String value) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE punishment_steps SET recommended_sanctions_json = ? WHERE case_id = ?
                     """)) {
            statement.setString(1, value);
            statement.setString(2, caseId.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                DATABASE.getJdbcUrl(),
                DATABASE.getUsername(),
                DATABASE.getPassword()
        );
    }
}
