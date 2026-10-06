package net.enthusia.staff.paper.auth;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import org.bukkit.entity.Player;

/** Supplies the LuckPerms context used for Staff Mode active-duty inheritance. */
public final class StaffDutyContextCalculator implements ContextCalculator<Player> {
    public static final String CONTEXT_KEY = "enthusiastaff-duty";
    public static final String ACTIVE_VALUE = "active";

    private final Predicate<UUID> activeDuty;
    private final Predicate<UUID> alwaysActive;

    public StaffDutyContextCalculator(Predicate<UUID> activeDuty) {
        this(activeDuty, ignored -> false);
    }

    public StaffDutyContextCalculator(Predicate<UUID> activeDuty, Predicate<UUID> alwaysActive) {
        this.activeDuty = Objects.requireNonNull(activeDuty, "activeDuty");
        this.alwaysActive = Objects.requireNonNull(alwaysActive, "alwaysActive");
    }

    @Override
    public void calculate(Player player, ContextConsumer consumer) {
        UUID playerId = player.getUniqueId();
        if (alwaysActive.test(playerId) || activeDuty.test(playerId)) {
            consumer.accept(CONTEXT_KEY, ACTIVE_VALUE);
        }
    }
}
