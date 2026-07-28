package net.enthusia.staff.paper.punishment;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.player.PlayerIdentity;

sealed interface PunishmentGuiState {
    UUID viewerId();

    PlayerIdentity target();

    String commandName();

    record Categories(UUID viewerId, PlayerIdentity target, String commandName, int page)
            implements PunishmentGuiState {
        public Categories {
            validate(viewerId, target, commandName, page);
        }
    }

    record Reasons(UUID viewerId, PlayerIdentity target, String commandName, String family, int page)
            implements PunishmentGuiState {
        public Reasons {
            validate(viewerId, target, commandName, page);
            if (family == null || family.isBlank()) {
                throw new IllegalArgumentException("punishment family must be present");
            }
        }
    }

    record Review(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentDraft draft,
            Optional<PunishmentAssessment> assessment
    ) implements PunishmentGuiState {
        public Review {
            validate(viewerId, target, commandName, 0);
            if (draft == null || assessment == null
                    || !draft.actorId().equals(viewerId)
                    || !draft.targetId().equals(target.playerId())) {
                throw new IllegalArgumentException("punishment review fields must be consistent");
            }
        }
    }

    private static void validate(UUID viewerId, PlayerIdentity target, String commandName, int page) {
        if (viewerId == null || target == null || commandName == null || commandName.isBlank() || page < 0) {
            throw new IllegalArgumentException("punishment GUI state fields must be present");
        }
    }
}
