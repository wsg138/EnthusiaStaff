package net.enthusia.staff.paper.market;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.ports.CaseLookup;
import net.enthusia.staff.domain.ports.MarketComplianceStore;

public record MarketCoordinatorRuntime(
        Clock clock,
        Supplier<OperationalMode> mode,
        AuthorizationPolicy authorization,
        Supplier<MarketComplianceStore> store,
        Supplier<CaseLookup> cases,
        Executor workers
) {
}
