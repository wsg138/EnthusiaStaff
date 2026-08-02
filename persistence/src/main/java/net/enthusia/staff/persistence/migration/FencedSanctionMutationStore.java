package net.enthusia.staff.persistence.migration;

import javax.sql.DataSource;
import net.enthusia.staff.domain.ports.SanctionMutationStore;
import net.enthusia.staff.domain.sanction.SanctionChangeRequest;
import net.enthusia.staff.domain.sanction.SanctionChangeResult;

public final class FencedSanctionMutationStore implements SanctionMutationStore {
    private final SanctionMutationStore delegate;
    private final AuthoritativeWriteFence fence;

    public FencedSanctionMutationStore(DataSource dataSource, SanctionMutationStore delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("sanction mutation store delegate must be present");
        }
        this.delegate = delegate;
        this.fence = new AuthoritativeWriteFence(dataSource);
    }

    @Override
    public SanctionChangeResult apply(SanctionChangeRequest request) {
        return fence.execute(
                () -> delegate.apply(request),
                () -> new SanctionChangeResult.Rejected(
                        "MODE_BLOCKED",
                        "Sanction changes are disabled by the operational mode"
                )
        );
    }
}
