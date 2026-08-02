package net.enthusia.staff.persistence.migration;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.PunishmentPlan;
import net.enthusia.staff.domain.application.PunishmentResult;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.ports.ModerationStore;
import net.enthusia.staff.persistence.ModerationPersistenceException;

public final class FencedModerationStore implements ModerationStore {
    private final ModerationStore delegate;
    private final AuthoritativeWriteFence fence;

    public FencedModerationStore(DataSource dataSource, ModerationStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("moderation store delegate must be present");
        }
        this.delegate = delegate;
        this.fence = new AuthoritativeWriteFence(dataSource);
    }

    @Override
    public List<PriorOffense> relatedHistory(UUID targetId, String family) {
        return delegate.relatedHistory(targetId, family);
    }

    @Override
    public PunishmentResult.Accepted createPunishment(PunishmentPlan plan) {
        return fence.execute(
                () -> delegate.createPunishment(plan),
                () -> {
                    throw new ModerationPersistenceException(
                            "Authoritative punishment creation is disabled by the operational mode"
                    );
                }
        );
    }
}
