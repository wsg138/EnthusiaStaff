package net.enthusia.staff.domain.casefile;

import java.time.Instant;
import java.util.UUID;

public record OverturnRequestReview(
        UUID requestId,
        UUID requestedBy,
        String explanation,
        Instant requestedAt,
        Instant expiresAt
) {
    public OverturnRequestReview {
        if (requestId == null || requestedBy == null || explanation == null || explanation.isBlank()
                || requestedAt == null || expiresAt == null || !expiresAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("overturn request review fields must be present");
        }
        explanation = explanation.trim();
    }
}
