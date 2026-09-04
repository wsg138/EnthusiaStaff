package net.enthusia.staff.paper.market;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.common.IdempotencyKey;
import net.enthusia.staff.domain.market.MarketComplianceKind;
import net.enthusia.staff.domain.market.MarketComplianceRequest;

final class MarketComplianceRequests {
    private static final Duration REVIEW_DELAY = Duration.ofDays(7);
    private static final Duration RECOVERY_WINDOW = Duration.ofDays(30);

    private final Supplier<UUID> identifiers;

    MarketComplianceRequests(Supplier<UUID> identifiers) {
        this.identifiers = java.util.Objects.requireNonNull(identifiers, "identifiers");
    }

    MarketComplianceRequest stall(
            UUID actorId,
            UUID targetId,
            CaseId caseId,
            String stallId,
            Optional<Instant> blacklistExpiresAt,
            Instant now
    ) {
        return request(
                actorId,
                targetId,
                caseId,
                MarketComplianceKind.STALL,
                Optional.of(stallId),
                blacklistExpiresAt,
                OptionalLong.empty(),
                now
        );
    }

    MarketComplianceRequest blacklistApply(
            UUID actorId,
            UUID targetId,
            CaseId caseId,
            Optional<Instant> expiresAt,
            Instant now
    ) {
        return request(
                actorId,
                targetId,
                caseId,
                MarketComplianceKind.BLACKLIST_APPLY,
                Optional.empty(),
                expiresAt,
                OptionalLong.empty(),
                now
        );
    }

    MarketComplianceRequest blacklistRemove(
            UUID actorId,
            UUID targetId,
            CaseId caseId,
            long expectedRevision,
            Instant now
    ) {
        return request(
                actorId,
                targetId,
                caseId,
                MarketComplianceKind.BLACKLIST_REMOVE,
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(expectedRevision),
                now
        );
    }

    private MarketComplianceRequest request(
            UUID actorId,
            UUID targetId,
            CaseId caseId,
            MarketComplianceKind kind,
            Optional<String> stallId,
            Optional<Instant> blacklistExpiresAt,
            OptionalLong expectedRevision,
            Instant now
    ) {
        String resource = stallId.orElse(targetId.toString());
        String keyMaterial = kind + "|" + caseId.value() + "|" + targetId + "|" + resource
                + "|" + blacklistExpiresAt.map(Instant::toString).orElse("")
                + "|" + (expectedRevision.isPresent() ? expectedRevision.orElseThrow() : "");
        return new MarketComplianceRequest(
                identifiers.get(),
                new IdempotencyKey("market:" + kind.name().toLowerCase(java.util.Locale.ROOT)
                        + ':' + checksum(keyMaterial)),
                caseId,
                targetId,
                kind,
                stallId,
                actorId,
                blacklistExpiresAt,
                expectedRevision,
                now.plus(REVIEW_DELAY),
                now.plus(RECOVERY_WINDOW),
                now
        );
    }

    private static String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
