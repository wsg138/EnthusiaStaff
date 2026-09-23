package net.enthusia.staff.paper.enforcement;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.domain.application.PunishmentService;
import net.enthusia.staff.domain.sanction.ActiveSanction;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper-side authoritative login enforcement for active ban sanctions. */
public final class PaperBanEnforcementListener implements Listener {
    private static final Set<SanctionType> LOGIN_BLOCK_TYPES = Set.of(
            SanctionType.BAN,
            SanctionType.NETWORK_BAN,
            SanctionType.NETWORK_IDENTITY_BAN
    );

    private final JavaPlugin plugin;
    private final Clock clock;
    private final Supplier<OperationalMode> mode;
    private final Supplier<PunishmentService> punishments;

    public PaperBanEnforcementListener(
            JavaPlugin plugin,
            Clock clock,
            Supplier<OperationalMode> mode,
            Supplier<PunishmentService> punishments
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.punishments = Objects.requireNonNull(punishments, "punishments");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (mode.get() != OperationalMode.ACTIVE) {
            return;
        }
        LoginDecision decision = decision(event.getUniqueId(), clock.instant());
        switch (decision.status()) {
            case ALLOW -> {
            }
            case BANNED -> event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    Component.text(decision.message())
            );
            case UNVERIFIED -> event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text(decision.message())
            );
        }
    }

    private LoginDecision decision(UUID playerId, Instant now) {
        PunishmentService service = punishments.get();
        if (service == null) {
            return LoginDecision.unverified();
        }
        try {
            List<ActiveSanction> active = service.activeSanctions(playerId, LOGIN_BLOCK_TYPES, now);
            if (active.isEmpty()) {
                return LoginDecision.allow();
            }
            return LoginDecision.banned(active.getFirst());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Authoritative ban lookup failed; login remains fail-closed", exception);
            return LoginDecision.unverified();
        }
    }

    record LoginDecision(Status status, String message) {
        static LoginDecision allow() {
            return new LoginDecision(Status.ALLOW, "");
        }

        static LoginDecision banned(ActiveSanction sanction) {
            String expiration = sanction.expiresAt().map(Instant::toString).orElse("permanent");
            return new LoginDecision(
                    Status.BANNED,
                    "You are banned (case " + sanction.caseId() + ", expires " + expiration + "). "
                            + sanction.publicReason()
            );
        }

        static LoginDecision unverified() {
            return new LoginDecision(
                    Status.UNVERIFIED,
                    "Moderation status could not be verified. Please retry shortly."
            );
        }
    }

    enum Status {
        ALLOW,
        BANNED,
        UNVERIFIED
    }
}
