package net.enthusia.staff.domain.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CutoverGate {
    public static final Duration REQUIRED_SHADOW_DURATION = Duration.ofHours(168);

    public CutoverAssessment assess(CutoverEvidence evidence, Optional<FounderOverride> override) {
        if (evidence == null || override == null) {
            throw new IllegalArgumentException("evidence and override must be present");
        }
        List<String> blockers = new ArrayList<>();
        Duration elapsed = Duration.between(evidence.shadowStartedAt(), evidence.assessedAt());
        if (elapsed.compareTo(REQUIRED_SHADOW_DURATION) < 0) {
            blockers.add("SHADOW_WINDOW_INCOMPLETE");
        }
        require(evidence.countsMatch(), "COUNT_MISMATCH", blockers);
        require(evidence.checksumsMatch(), "CHECKSUM_MISMATCH", blockers);
        require(evidence.activeSanctionsMatch(), "ACTIVE_SANCTION_MISMATCH", blockers);
        require(evidence.uuidMappingsMatch(), "UUID_MAPPING_MISMATCH", blockers);
        require(evidence.expirationsMatch(), "EXPIRATION_MISMATCH", blockers);
        require(evidence.loginDecisions().matches(), "LOGIN_DECISION_MISMATCH", blockers);
        require(evidence.muteDecisions().matches(), "MUTE_DECISION_MISMATCH", blockers);
        require(evidence.ipBanDecisions().matches(), "IP_BAN_DECISION_MISMATCH", blockers);
        require(evidence.unresolvedOperations() == 0, "UNRESOLVED_RECOVERY_OPERATIONS", blockers);
        require(evidence.writesFrozen(), "PUNISHMENT_WRITES_NOT_FROZEN", blockers);
        require(evidence.finalIncrementalImportComplete(), "FINAL_IMPORT_INCOMPLETE", blockers);

        if (blockers.isEmpty()) {
            return new CutoverAssessment(true, false, List.of());
        }
        if (override.isPresent()) {
            return new CutoverAssessment(true, true, blockers);
        }
        return new CutoverAssessment(false, false, blockers);
    }

    private static void require(boolean condition, String blocker, List<String> blockers) {
        if (!condition) {
            blockers.add(blocker);
        }
    }
}
