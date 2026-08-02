package net.enthusia.staff.paper.alert;

import java.util.Optional;
import java.util.UUID;

public interface PunishmentRequestAlertPresenter {
    Optional<PunishmentRequestAlertRecipient> current(UUID playerId);

    /** Returns false when the current Bukkit presentation boundary is temporarily unavailable. */
    boolean present(PunishmentRequestAlertRecipient recipient, PunishmentRequestAlertPresentation presentation);
}
