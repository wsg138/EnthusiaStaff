package net.enthusia.staff.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionStatus;

record ExactSanctionRow(
        UUID sanctionId,
        CaseId caseId,
        UUID subjectId,
        SanctionStatus status,
        Instant issuedAt,
        Optional<Instant> expiration,
        Optional<Instant> endedAt,
        long revision,
        StaffRank issuerRank
) {
}
