package net.enthusia.staff.paper.client;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.autoclicker.server.api.ClientHandshakeSnapshot;
import net.enthusia.autoclicker.server.api.EnthusiaAutoClickerClientApi;
import net.enthusia.staff.domain.evidence.AutoClickerHandshakeEvidence;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import org.bukkit.plugin.ServicesManager;

final class AutoClickerIntegration {
    private final IntegrationAvailability availability;
    private final String issue;
    private final EnthusiaAutoClickerClientApi api;

    private AutoClickerIntegration(
            IntegrationAvailability availability,
            String issue,
            EnthusiaAutoClickerClientApi api
    ) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.issue = Objects.requireNonNull(issue, "issue");
        this.api = api;
    }

    static AutoClickerIntegration discover(ServicesManager services, boolean pluginEnabled) {
        Objects.requireNonNull(services, "services");
        if (!pluginEnabled) {
            return unavailable(
                    IntegrationAvailability.NOT_INSTALLED,
                    "Enthusia AutoClicker server plugin is not installed or enabled"
            );
        }
        try {
            EnthusiaAutoClickerClientApi api = services.load(EnthusiaAutoClickerClientApi.class);
            if (api == null) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "Enthusia AutoClicker did not register its client evidence service"
                );
            }
            if (api.apiVersion() != EnthusiaAutoClickerClientApi.API_VERSION) {
                return unavailable(
                        IntegrationAvailability.INCOMPATIBLE,
                        "Enthusia AutoClicker client API version " + api.apiVersion()
                                + " is incompatible with required version "
                                + EnthusiaAutoClickerClientApi.API_VERSION
                );
            }
            return new AutoClickerIntegration(IntegrationAvailability.AVAILABLE, "", api);
        } catch (LinkageError | RuntimeException exception) {
            return unavailable(
                    IntegrationAvailability.INCOMPATIBLE,
                    "Enthusia AutoClicker API could not be linked: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    HandshakeObservation observe(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (availability != IntegrationAvailability.AVAILABLE) {
            return new HandshakeObservation(availability, Optional.empty(), issue);
        }
        try {
            Optional<ClientHandshakeSnapshot> handshake = api.handshake(playerId);
            return new HandshakeObservation(
                    IntegrationAvailability.AVAILABLE,
                    handshake.map(value -> new AutoClickerHandshakeEvidence(
                            value.modVersion(),
                            value.loader(),
                            value.minecraftVersion(),
                            value.receivedAt()
                    )),
                    ""
            );
        } catch (LinkageError | RuntimeException exception) {
            return new HandshakeObservation(
                    IntegrationAvailability.UNAVAILABLE,
                    Optional.empty(),
                    "Enthusia AutoClicker player query failed: "
                            + exception.getClass().getSimpleName()
            );
        }
    }

    IntegrationAvailability availability() {
        return availability;
    }

    String issue() {
        return issue;
    }

    private static AutoClickerIntegration unavailable(
            IntegrationAvailability availability,
            String issue
    ) {
        return new AutoClickerIntegration(availability, issue, null);
    }

    record HandshakeObservation(
            IntegrationAvailability availability,
            Optional<AutoClickerHandshakeEvidence> handshake,
            String issue
    ) {
        HandshakeObservation {
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(handshake, "handshake");
            Objects.requireNonNull(issue, "issue");
        }
    }
}
