package net.enthusia.staff.persistence.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.migration.CutoverAssessment;
import net.enthusia.staff.domain.migration.CutoverEvidence;
import net.enthusia.staff.domain.migration.CutoverGate;
import net.enthusia.staff.domain.migration.FounderOverride;
import net.enthusia.staff.domain.runtime.OperationalStateSnapshot;
import net.enthusia.staff.persistence.ModerationPersistenceException;

public final class CutoverCoordinator {
    private static final String ACTIVATION_REASON = "Validated LiteBans cutover";

    private final DataSource dataSource;
    private final Clock clock;
    private final CutoverGate gate = new CutoverGate();
    private final CutoverPersistence persistence = new CutoverPersistence();
    private final CutoverEvidenceReader evidenceReader;
    private final CutoverAuditWriter auditWriter;

    public CutoverCoordinator(DataSource dataSource, ObjectMapper json, Clock clock) {
        if (dataSource == null || json == null || clock == null) {
            throw new IllegalArgumentException("cutover coordinator dependencies must be present");
        }
        this.dataSource = dataSource;
        this.clock = clock;
        this.evidenceReader = new CutoverEvidenceReader(json);
        this.auditWriter = new CutoverAuditWriter(json);
    }

    public boolean enterMaintenance(UUID actorId, String reason) {
        validateTransition(actorId, reason);
        return transitionMode(
                OperationalMode.SHADOW_MIGRATION,
                OperationalMode.MAINTENANCE,
                actorId,
                reason.trim(),
                "LITEBANS_CUTOVER_MAINTENANCE_ENTERED"
        );
    }

    public boolean abortMaintenance(UUID actorId, String reason) {
        validateTransition(actorId, reason);
        return transitionMode(
                OperationalMode.MAINTENANCE,
                OperationalMode.SHADOW_MIGRATION,
                actorId,
                reason.trim(),
                "LITEBANS_CUTOVER_MAINTENANCE_ABORTED"
        );
    }

    public boolean freezeActiveAuthority(UUID actorId, String reason) {
        validateTransition(actorId, reason);
        return transitionMode(
                OperationalMode.ACTIVE,
                OperationalMode.READ_ONLY_FAILURE,
                actorId,
                reason.trim(),
                "LITEBANS_CUTOVER_EMERGENCY_FREEZE"
        );
    }

    public Optional<CutoverEvidence> latestEvidence() {
        try (Connection connection = dataSource.getConnection()) {
            OperationalStateSnapshot state = persistence.readOperationalState(connection, false);
            return evidenceReader.read(connection, state, clock.instant())
                    .map(CutoverEvidenceBundle::evidence);
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ModerationPersistenceException("Unable to assemble cutover evidence", exception);
        }
    }

    public CutoverAssessment assess(Optional<FounderOverride> override) {
        if (override == null) {
            throw new IllegalArgumentException("cutover override container must be present");
        }
        return latestEvidence()
                .map(evidence -> gate.assess(evidence, override))
                .orElseGet(() -> blockedAssessment("NO_COMPLETED_SHADOW_EVIDENCE"));
    }

    public CutoverOutcome activate(UUID actorId, Optional<FounderOverride> override) {
        validateActivation(actorId, override);
        return withMigrationLock(() -> MigrationTransactionSupport.execute(
                dataSource,
                "Cutover activation transaction failed",
                connection -> activate(connection, actorId, override)
        ));
    }

    private CutoverOutcome activate(
            Connection connection,
            UUID actorId,
            Optional<FounderOverride> override
    ) throws SQLException {
        OperationalStateSnapshot current = persistence.readOperationalState(connection, true);
        if (current.mode() != OperationalMode.MAINTENANCE) {
            return inactiveOutcome("MAINTENANCE_REQUIRED");
        }
        Instant activatedAt = clock.instant();
        Optional<CutoverEvidenceBundle> assembled = evidenceReader.read(
                connection,
                current,
                activatedAt
        );
        if (assembled.isEmpty()) {
            return inactiveOutcome("NO_COMPLETED_SHADOW_EVIDENCE");
        }
        CutoverEvidenceBundle bundle = assembled.orElseThrow();
        CutoverAssessment assessment = gate.assess(bundle.evidence(), override);
        if (!assessment.allowed()) {
            return new CutoverOutcome(assessment, false, Optional.empty());
        }
        return completeActivation(
                connection,
                current,
                bundle,
                assessment,
                actorId,
                override,
                activatedAt
        );
    }

    private CutoverOutcome completeActivation(
            Connection connection,
            OperationalStateSnapshot current,
            CutoverEvidenceBundle bundle,
            CutoverAssessment assessment,
            UUID actorId,
            Optional<FounderOverride> override,
            Instant activatedAt
    ) throws SQLException {
        UUID cutoverId = UUID.randomUUID();
        persistence.insertCutoverRecord(
                connection,
                cutoverId,
                bundle,
                assessment,
                actorId,
                activatedAt,
                auditWriter.assessmentJson(bundle.evidence(), assessment, override),
                auditWriter.blockersJson(assessment.blockers())
        );
        persistence.transitionOperationalState(
                connection,
                current,
                OperationalMode.MAINTENANCE,
                OperationalMode.ACTIVE,
                actorId,
                ACTIVATION_REASON,
                activatedAt
        );
        auditWriter.appendActivation(
                connection,
                cutoverId,
                actorId,
                assessment,
                override,
                activatedAt
        );
        return new CutoverOutcome(assessment, true, Optional.of(cutoverId));
    }

    private boolean transitionMode(
            OperationalMode expected,
            OperationalMode next,
            UUID actorId,
            String reason,
            String auditType
    ) {
        return withMigrationLock(() -> MigrationTransactionSupport.execute(
                dataSource,
                "Migration operational transition failed",
                connection -> transitionMode(
                        connection,
                        expected,
                        next,
                        actorId,
                        reason,
                        auditType
                )
        ));
    }

    private boolean transitionMode(
            Connection connection,
            OperationalMode expected,
            OperationalMode next,
            UUID actorId,
            String reason,
            String auditType
    ) throws SQLException {
        OperationalStateSnapshot current = persistence.readOperationalState(connection, true);
        if (current.mode() != expected) {
            return false;
        }
        Instant changedAt = clock.instant();
        persistence.transitionOperationalState(
                connection,
                current,
                expected,
                next,
                actorId,
                reason,
                changedAt
        );
        auditWriter.appendTransition(
                connection,
                actorId,
                expected,
                next,
                reason,
                auditType,
                changedAt
        );
        return true;
    }

    private <T> T withMigrationLock(Supplier<T> operation) {
        MigrationDatabaseLock migrationLock = MigrationDatabaseLock.acquire(dataSource);
        Throwable operationFailure = null;
        try {
            return operation.get();
        } catch (RuntimeException | Error exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            migrationLock.closeAfter(operationFailure);
        }
    }

    private static void validateActivation(UUID actorId, Optional<FounderOverride> override) {
        if (actorId == null || override == null) {
            throw new IllegalArgumentException("cutover actor and override container are required");
        }
        if (override.filter(value -> !value.actorId().equals(actorId)).isPresent()) {
            throw new IllegalArgumentException("Founder override actor must match the cutover actor");
        }
    }

    private static void validateTransition(UUID actorId, String reason) {
        if (actorId == null || reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("migration transition actor and bounded reason are required");
        }
    }

    private static CutoverOutcome inactiveOutcome(String blocker) {
        return new CutoverOutcome(blockedAssessment(blocker), false, Optional.empty());
    }

    private static CutoverAssessment blockedAssessment(String blocker) {
        return new CutoverAssessment(false, false, List.of(blocker));
    }
}
