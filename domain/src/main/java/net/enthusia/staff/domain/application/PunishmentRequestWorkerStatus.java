package net.enthusia.staff.domain.application;

import java.time.Instant;
import java.util.Optional;

public record PunishmentRequestWorkerStatus(
        String workerName,
        boolean running,
        long backlog,
        int lastBatchSize,
        Optional<Instant> lastSuccessAt,
        Optional<Instant> lastFailureAt,
        Optional<String> lastErrorCode
) {
    public PunishmentRequestWorkerStatus {
        if (workerName == null || workerName.isBlank() || backlog < 0 || lastBatchSize < 0
                || lastSuccessAt == null || lastFailureAt == null || lastErrorCode == null) {
            throw new IllegalArgumentException("worker status fields must be valid");
        }
        lastErrorCode = lastErrorCode.map(String::trim).filter(value -> !value.isEmpty());
    }
}
