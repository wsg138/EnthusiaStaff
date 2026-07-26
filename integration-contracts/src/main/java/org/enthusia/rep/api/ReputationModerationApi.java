package org.enthusia.rep.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReputationModerationApi {
    int API_VERSION = 1;

    int apiVersion();

    boolean isReputationBlacklisted(UUID playerId);

    ReputationBlacklist blacklist(UUID playerId, Instant expirationAt, String caseId);

    ReputationBlacklist blacklistPermanently(UUID playerId, String caseId);

    boolean removeBlacklist(UUID playerId, String caseId);

    boolean canGiveReputation(UUID playerId);

    Optional<ReputationBlacklist> getBlacklist(UUID playerId);
}
