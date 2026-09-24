package net.enthusia.staff.persistence.migration;

import javax.sql.DataSource;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentPlan;
import net.enthusia.staff.domain.application.CrossPlatformPunishmentResult;
import net.enthusia.staff.domain.ports.CrossPlatformPunishmentStore;
import net.enthusia.staff.persistence.ModerationPersistenceException;

/** Applies the normal authoritative-write fence to D08 atomic punishment creation. */
public final class FencedCrossPlatformPunishmentStore implements CrossPlatformPunishmentStore {
    private final CrossPlatformPunishmentStore delegate;
    private final AuthoritativeWriteFence fence;

    public FencedCrossPlatformPunishmentStore(DataSource dataSource, CrossPlatformPunishmentStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("cross-platform store delegate must be present");
        }
        this.delegate = delegate;
        this.fence = new AuthoritativeWriteFence(dataSource);
    }

    @Override
    public CrossPlatformPunishmentResult create(CrossPlatformPunishmentPlan plan) {
        return fence.execute(
                () -> delegate.create(plan),
                () -> {
                    throw new ModerationPersistenceException(
                            "Cross-platform punishment creation is disabled by the operational mode"
                    );
                }
        );
    }
}
