package net.enthusia.staff.domain.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentDraft;

public interface PunishmentDraftStore {
    void save(PunishmentDraft draft);

    Optional<PunishmentDraft> find(UUID draftId, UUID actorId, Instant now);

    Optional<PunishmentDraft> findLatest(UUID actorId, UUID targetId, Instant now);

    boolean delete(UUID draftId, UUID actorId);

    int deleteExpired(Instant now);
}
