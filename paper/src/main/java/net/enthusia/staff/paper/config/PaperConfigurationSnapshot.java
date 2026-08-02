package net.enthusia.staff.paper.config;

import java.util.Objects;
import net.enthusia.staff.paper.alert.PunishmentRequestAlertWorkerSettings;

public record PaperConfigurationSnapshot(
        int version,
        RestartRequiredConfiguration restartRequired,
        PunishmentRequestAlertWorkerSettings punishmentRequestAlerts
) {
    public PaperConfigurationSnapshot {
        if (version != PaperConfigurationLoader.SUPPORTED_VERSION) {
            throw new IllegalArgumentException("unsupported configuration version");
        }
        Objects.requireNonNull(restartRequired, "restartRequired");
        Objects.requireNonNull(punishmentRequestAlerts, "punishmentRequestAlerts");
    }
}
