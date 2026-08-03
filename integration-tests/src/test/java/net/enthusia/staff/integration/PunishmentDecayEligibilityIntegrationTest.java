package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PunishmentDecayEligibilityIntegrationTest extends PunishmentRequestMariaDbSupport {
    private static final String FAMILY = "decay-test";
    private static final UUID TARGET = identifier("decay-eligibility-target");

    @Test
    void seriousIneligibleHistorySurvivesRestartAndNeverDecaysUnderLaterEligiblePolicy() {
        CaseId caseId = new CaseId("5400000000000001");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    DecayEligibility.INELIGIBLE,
                    sevenDayBan()
            ));
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            List<PriorOffense> history = runtime.moderationStore().relatedHistory(TARGET, FAMILY);
            assertEquals(1, history.size());
            assertEquals(DecayEligibility.INELIGIBLE, history.getFirst().decayEligibility());

            EscalationDecision decision = new EscalationEngine().decide(
                    policy(true),
                    history,
                    NOW.plus(Duration.ofDays(400))
            );
            assertEquals(2, decision.rawOrdinal());
            assertEquals(2, decision.effectiveOrdinal());
            assertEquals(0, decision.contributions().getFirst().decayedBy());
        }
    }

    @Test
    void eligibleHistorySurvivesRestartAndDecaysUnderLaterIneligiblePolicy() {
        CaseId caseId = new CaseId("5400000000000002");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    DecayEligibility.ELIGIBLE,
                    warning()
            ));
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            List<PriorOffense> history = runtime.moderationStore().relatedHistory(TARGET, FAMILY);
            assertEquals(1, history.size());
            assertEquals(DecayEligibility.ELIGIBLE, history.getFirst().decayEligibility());

            EscalationDecision decision = new EscalationEngine().decide(
                    policy(false),
                    history,
                    NOW.plus(Duration.ofDays(180))
            );
            assertEquals(1, decision.rawOrdinal());
            assertEquals(0, decision.effectiveOrdinal());
            assertEquals(2, decision.contributions().getFirst().decayedBy());
        }
    }

    @Test
    void legacyNullEligibilityLoadsAsUnknownAndDoesNotInventDecay() throws Exception {
        CaseId caseId = new CaseId("5400000000000003");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    DecayEligibility.ELIGIBLE,
                    warning()
            ));
            clearEligibility(caseId);
        }

        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            List<PriorOffense> history = runtime.moderationStore().relatedHistory(TARGET, FAMILY);
            assertEquals(DecayEligibility.UNKNOWN, history.getFirst().decayEligibility());

            EscalationDecision decision = new EscalationEngine().decide(
                    policy(true),
                    history,
                    NOW.plus(Duration.ofDays(365))
            );
            assertEquals(0, decision.contributions().getFirst().decayedBy());
            assertEquals(1, decision.effectiveOrdinal());
        }
    }

    @Test
    void databaseRejectsOutOfRangeDecayEligibility() {
        CaseId caseId = new CaseId("5400000000000004");
        try (MariaDbRuntime runtime = MariaDb.initialize(databaseConfig())) {
            runtime.moderationStore().createPunishment(plan(
                    caseId,
                    DecayEligibility.ELIGIBLE,
                    warning()
            ));

            assertThrows(SQLException.class, () -> setRawEligibility(caseId, 2));
        }
    }

    private static PunishmentPlan plan(
            CaseId caseId,
            DecayEligibility eligibility,
            List<SanctionSpec> sanctions
    ) {
        PunishmentStep selected = new PunishmentStep(0, "Decay snapshot", sanctions);
        return new PunishmentPlan(
                caseId,
                new IdempotencyKey("decay-eligibility:" + caseId.value()),
                TARGET,
                MOD,
                "test.decay",
                FAMILY,
                "Decay test",
                "Persist immutable decay metadata",
                "decay-v1",
                CaseVisibility.PUBLIC,
                NOW,
                new EscalationDecision(0, 0, 0, List.of(), eligibility, selected),
                sanctions
        );
    }

    private static ReasonPolicy policy(boolean decayEligible) {
        return new ReasonPolicy(
                "test.decay-current",
                FAMILY,
                "Current decay test",
                50,
                decayEligible,
                List.of(
                        step(0),
                        step(1),
                        step(2)
                )
        );
    }

    private static PunishmentStep step(int ordinal) {
        return new PunishmentStep(
                ordinal,
                "Step " + ordinal,
                warning()
        );
    }

    private static List<SanctionSpec> warning() {
        return List.of(new SanctionSpec(SanctionType.WARNING, SanctionLength.instant()));
    }

    private static void clearEligibility(CaseId caseId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE punishment_steps SET decay_eligible = NULL WHERE case_id = ?
                     """)) {
            statement.setString(1, caseId.value());
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void setRawEligibility(CaseId caseId, int value) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE punishment_steps SET decay_eligible = ? WHERE case_id = ?
                     """)) {
            statement.setInt(1, value);
            statement.setString(2, caseId.value());
            statement.executeUpdate();
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
