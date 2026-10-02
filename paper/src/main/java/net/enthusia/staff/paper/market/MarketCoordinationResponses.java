package net.enthusia.staff.paper.market;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import net.enthusia.staff.domain.market.MarketComplianceOperation;

/** Constructs bounded public results without mixing response concerns into orchestration. */
final class MarketCoordinationResponses {
    private static final int MAXIMUM_DETAIL_LENGTH = 512;

    private MarketCoordinationResponses() {
    }

    static MarketCoordinationResult result(
            MarketCoordinationResult.Status status,
            MarketComplianceOperation operation,
            String detail
    ) {
        return new MarketCoordinationResult(
                status,
                Optional.ofNullable(operation),
                bounded(detail)
        );
    }

    static MarketCoordinationResult rejected(String detail) {
        return new MarketCoordinationResult(
                MarketCoordinationResult.Status.REJECTED,
                Optional.empty(),
                bounded(detail)
        );
    }

    static MarketCoordinationResult unavailable(String detail) {
        return new MarketCoordinationResult(
                MarketCoordinationResult.Status.UNAVAILABLE,
                Optional.empty(),
                bounded(detail)
        );
    }

    static String safeMessage(Throwable failure) {
        Throwable current = failure;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(current);
        while (current.getCause() != null && visited.add(current.getCause())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : bounded(message);
    }

    private static String bounded(String detail) {
        if (detail == null || detail.isBlank()) {
            return "Market operation failed without detail";
        }
        return detail.length() <= MAXIMUM_DETAIL_LENGTH
                ? detail
                : detail.substring(0, MAXIMUM_DETAIL_LENGTH);
    }
}
