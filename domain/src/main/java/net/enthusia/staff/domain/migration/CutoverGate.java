package net.enthusia.staff.domain.migration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CutoverGate {
    public static final Duration REQUIRED_SHADOW_DURATION = Duration.ofHours(168);
    public static final Duration MAXIMUM_SHADOW_SUMMARY_GAP = Duration.ofHours(26);
    public static final int REQUIRED_SHADOW_SUMMARIES = 7;
    private static final Set<String> FOUNDER_OVERRIDABLE_BLOCKERS = Set.of(
            "SHADOW_WINDOW_INCOMPLETE",
            "SHADOW_DAILY_COVERAGE_INCOMPLETE"
    );

    public CutoverAssessment assess(CutoverEvidence evidence, Optional<FounderOverride> override) {
        if (evidence == null || override == null) {
            throw new IllegalArgumentException("evidence and override must be present");
        }
        List<String> blockers = new ArrayList<>();
        Duration elapsed = Duration.between(evidence.shadowStartedAt(), evidence.shadowEndedAt());
        if (elapsed.compareTo(REQUIRED_SHADOW_DURATION) < 0) {
            blockers.add("SHADOW_WINDOW_INCOMPLETE");
        }
        require(hasDailyCoverage(evidence), "SHADOW_DAILY_COVERAGE_INCOMPLETE", blockers);
        require(evidence.countsMatch(), "COUNT_MISMATCH", blockers);
        require(evidence.checksumsMatch(), "CHECKSUM_MISMATCH", blockers);
        require(evidence.activeSanctionsMatch(), "ACTIVE_SANCTION_MISMATCH", blockers);
        require(evidence.uuidMappingsMatch(), "UUID_MAPPING_MISMATCH", blockers);
        require(evidence.expirationsMatch(), "EXPIRATION_MISMATCH", blockers);
        require(evidence.loginDecisions().matches(), "LOGIN_DECISION_MISMATCH", blockers);
        require(evidence.muteDecisions().matches(), "MUTE_DECISION_MISMATCH", blockers);
        require(evidence.ipBanDecisions().matches(), "IP_BAN_DECISION_MISMATCH", blockers);
        require(evidence.unresolvedOperations() == 0, "UNRESOLVED_RECOVERY_OPERATIONS", blockers);
        require(evidence.migrationIdle(), "MIGRATION_OPERATION_IN_PROGRESS", blockers);
        require(evidence.writesFrozen(), "PUNISHMENT_WRITES_NOT_FROZEN", blockers);
        require(evidence.finalIncrementalImportComplete(), "FINAL_IMPORT_INCOMPLETE", blockers);

        if (blockers.isEmpty()) {
            return new CutoverAssessment(true, false, List.of());
        }
        if (override.isPresent() && blockers.stream().allMatch(FOUNDER_OVERRIDABLE_BLOCKERS::contains)) {
            return new CutoverAssessment(true, true, blockers);
        }
        return new CutoverAssessment(false, false, blockers);
    }

    private static boolean hasDailyCoverage(CutoverEvidence evidence) {
        List<java.time.Instant> summaries = evidence.successfulShadowSummaries();
        if (summaries.size() < REQUIRED_SHADOW_SUMMARIES) {
            return false;
        }
        java.time.Instant previous = evidence.shadowStartedAt();
        for (java.time.Instant summary : summaries) {
            if (Duration.between(previous, summary).compareTo(MAXIMUM_SHADOW_SUMMARY_GAP) > 0) {
                return false;
            }
            previous = summary;
        }
        return Duration.between(previous, evidence.shadowEndedAt()).compareTo(MAXIMUM_SHADOW_SUMMARY_GAP) <= 0;
    }

    private static void require(boolean condition, String blocker, List<String> blockers) {
        if (!condition) {
            blockers.add(blocker);
        }
    }
}
