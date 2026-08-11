package net.enthusia.staff.paper.economy;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.auth.AuthorizationPolicy;
import net.enthusia.staff.domain.ports.EconomyJournalStore;
import org.bukkit.plugin.java.JavaPlugin;

public record EconomyCoordinatorRuntime(
        JavaPlugin plugin,
        Clock clock,
        String serverId,
        Supplier<OperationalMode> mode,
        AuthorizationPolicy authorization,
        Supplier<EconomyJournalStore> store,
        ExecutorService workers
) {
}
