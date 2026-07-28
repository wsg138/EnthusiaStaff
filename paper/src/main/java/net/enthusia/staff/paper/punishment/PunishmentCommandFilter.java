package net.enthusia.staff.paper.punishment;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;

public final class PunishmentCommandFilter {
    private static final Map<String, Set<SanctionType>> FILTERS = Map.of(
            "ban", Set.of(SanctionType.BAN, SanctionType.NETWORK_BAN),
            "ipban", Set.of(SanctionType.NETWORK_IDENTITY_BAN),
            "mute", Set.of(SanctionType.MUTE),
            "warn", Set.of(SanctionType.WARNING),
            "kick", Set.of(SanctionType.KICK)
    );

    private PunishmentCommandFilter() {
    }

    public static boolean matches(String commandName, List<SanctionSpec> sanctions) {
        Set<SanctionType> required = types(commandName);
        return required.isEmpty() || sanctions.stream().anyMatch(spec -> required.contains(spec.type()));
    }

    public static boolean includes(String commandName, ReasonPolicy policy) {
        Set<SanctionType> required = types(commandName);
        return required.isEmpty() || policy.steps().stream()
                .flatMap(step -> step.sanctions().stream())
                .anyMatch(spec -> required.contains(spec.type()));
    }

    private static Set<SanctionType> types(String commandName) {
        if (commandName == null) {
            return Set.of();
        }
        return FILTERS.getOrDefault(commandName.toLowerCase(Locale.ROOT), Set.of());
    }
}
