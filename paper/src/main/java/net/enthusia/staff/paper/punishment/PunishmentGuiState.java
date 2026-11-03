package net.enthusia.staff.paper.punishment;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.enthusia.staff.domain.guided.GuidedIncidentWizard;

sealed interface PunishmentGuiState {
    UUID viewerId();

    PlayerIdentity target();

    String commandName();

    PunishmentGuiOverview overview();

    record Categories(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            int page
    ) implements PunishmentGuiState {
        public Categories {
            validate(viewerId, target, commandName, overview, page);
        }
    }

    /**
     * A non-enforcing guided interview and recommendation preview.
     * The live punishment draft workflow remains authoritative.
     */
    record Guided(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            String categoryId,
            GuidedIncidentWizard interview,
            int durationChoice
    ) implements PunishmentGuiState {
        public Guided {
            validate(viewerId, target, commandName, overview, 0);
            if (categoryId == null || interview == null
                    || !categoryId.equals(interview.category())
                    || durationChoice < -1 || durationChoice > 1) {
                throw new IllegalArgumentException("Guided state must match its category and adjustment");
            }
        }
    }

    record Reasons(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            String categoryId,
            int page
    ) implements PunishmentGuiState {
        public Reasons {
            validate(viewerId, target, commandName, overview, page);
            if (categoryId == null || categoryId.isBlank()) {
                throw new IllegalArgumentException("punishment category must be present");
            }
        }
    }

    record Search(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            String query,
            int page
    ) implements PunishmentGuiState {
        public Search {
            validate(viewerId, target, commandName, overview, page);
            if (query == null || query.codePointCount(0, query.length()) < 2
                    || query.codePointCount(0, query.length()) > 64) {
                throw new IllegalArgumentException("punishment search query must be 2–64 characters");
            }
        }
    }

    record Review(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            PunishmentDraft draft,
            Optional<PunishmentAssessment> assessment
    ) implements PunishmentGuiState {
        public Review {
            validate(viewerId, target, commandName, overview, 0);
            if (draft == null || assessment == null
                    || !draft.actorId().equals(viewerId)
                    || !draft.targetId().equals(target.playerId())) {
                throw new IllegalArgumentException("punishment review fields must be consistent");
            }
        }
    }

    record History(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            ModerationHistoryPage history,
            boolean sensitiveHistory,
            boolean available,
            PunishmentGuiState returnState
    ) implements PunishmentGuiState {
        public History {
            validate(viewerId, target, commandName, overview, 0);
            if (history == null || !history.subjectId().equals(target.playerId())
                    || returnState == null || returnState instanceof History
                    || !returnState.viewerId().equals(viewerId)
                    || !returnState.target().playerId().equals(target.playerId())) {
                throw new IllegalArgumentException("punishment history state must match the selected target and return view");
            }
        }
    }

    private static void validate(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            int page
    ) {
        if (viewerId == null || target == null || commandName == null || commandName.isBlank()
                || overview == null || page < 0) {
            throw new IllegalArgumentException("punishment GUI state fields must be present");
        }
    }
}
