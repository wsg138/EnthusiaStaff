package net.enthusia.staff.paper.punishment;

import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.application.PunishmentAssessment;
import net.enthusia.staff.domain.application.PunishmentDraft;
import net.enthusia.staff.domain.discord.DiscordPunishmentIntent;
import net.enthusia.staff.domain.history.ModerationHistoryPage;
import net.enthusia.staff.domain.player.PlayerIdentity;

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

    record Review(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            PunishmentDraft draft,
            Optional<PunishmentAssessment> assessment,
            PaperPunishmentScope scope,
            Optional<DiscordPunishmentIntent> discordIntent
    ) implements PunishmentGuiState {
        public Review {
            validate(viewerId, target, commandName, overview, 0);
            if (draft == null || assessment == null || scope == null || discordIntent == null
                    || !draft.actorId().equals(viewerId)
                    || !draft.targetId().equals(target.playerId())) {
                throw new IllegalArgumentException("punishment review fields must be consistent");
            }
            if (scope == PaperPunishmentScope.MINECRAFT && discordIntent.isPresent()) {
                throw new IllegalArgumentException("Minecraft-only review cannot carry a Discord intent");
            }
        }

        Review withScope(PaperPunishmentScope next, Optional<DiscordPunishmentIntent> nextIntent) {
            return new Review(
                    viewerId, target, commandName, overview, draft, assessment, next, nextIntent);
        }
    }

    record CrossPlatformStatus(
            UUID viewerId,
            PlayerIdentity target,
            String commandName,
            PunishmentGuiOverview overview,
            PaperCrossPlatformPunishmentService.Status status
    ) implements PunishmentGuiState {
        public CrossPlatformStatus {
            validate(viewerId, target, commandName, overview, 0);
            if (status == null) {
                throw new IllegalArgumentException("cross-platform status must be present");
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
