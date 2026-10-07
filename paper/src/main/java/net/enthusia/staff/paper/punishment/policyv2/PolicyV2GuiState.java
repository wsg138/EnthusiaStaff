package net.enthusia.staff.paper.punishment.policyv2;

import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.policyv2.IncidentAttributeDefinition;
import net.enthusia.staff.domain.policyv2.OffensePolicy;

sealed interface PolicyV2GuiState {
    UUID viewerId();

    UUID targetId();

    String targetName();

    record Categories(
            UUID viewerId,
            UUID targetId,
            String targetName,
            List<PolicyV2Category> categories
    ) implements PolicyV2GuiState {
        public Categories {
            validate(viewerId, targetId, targetName);
            categories = List.copyOf(categories);
        }
    }

    record Offenses(
            UUID viewerId,
            UUID targetId,
            String targetName,
            PolicyV2ManualDraft draft,
            List<OffensePolicy> offenses,
            int page
    ) implements PolicyV2GuiState {
        public Offenses {
            validate(viewerId, targetId, targetName);
            if (draft == null || offenses == null || page < 0) {
                throw new IllegalArgumentException("Policy v2 offense screen is invalid");
            }
            offenses = List.copyOf(offenses);
        }
    }

    record Questions(
            UUID viewerId,
            UUID targetId,
            String targetName,
            PolicyV2ManualDraft draft,
            List<IncidentAttributeDefinition> questions
    ) implements PolicyV2GuiState {
        public Questions {
            validate(viewerId, targetId, targetName);
            if (draft == null || questions == null) {
                throw new IllegalArgumentException("Policy v2 question screen is invalid");
            }
            questions = List.copyOf(questions);
        }
    }

    record Review(
            UUID viewerId,
            UUID targetId,
            String targetName,
            PolicyV2ManualDraft draft
    ) implements PolicyV2GuiState {
        public Review {
            validate(viewerId, targetId, targetName);
            if (draft == null) {
                throw new IllegalArgumentException("Policy v2 review screen is invalid");
            }
        }
    }

    record Result(
            UUID viewerId,
            UUID targetId,
            String targetName,
            PolicyV2ManualReview review,
            PolicyV2ReviewPresentation presentation
    ) implements PolicyV2GuiState {
        public Result {
            validate(viewerId, targetId, targetName);
            if (review == null || presentation == null) {
                throw new IllegalArgumentException("Policy v2 result screen is invalid");
            }
        }
    }

    private static void validate(UUID viewerId, UUID targetId, String targetName) {
        if (viewerId == null || targetId == null || targetName == null || targetName.isBlank()) {
            throw new IllegalArgumentException("Policy v2 GUI identity must be present");
        }
    }
}
