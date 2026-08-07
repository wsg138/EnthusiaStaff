package net.enthusia.staff.domain.website;

public record WebsiteAppealDecisionPreparation(
        WebsiteAppealView appeal,
        boolean replayed,
        boolean requiresAcceptance,
        String playerAccountId
) {
    public WebsiteAppealDecisionPreparation {
        if (appeal == null || (requiresAcceptance
                && (playerAccountId == null || playerAccountId.isBlank()))) {
            throw new IllegalArgumentException("Website appeal decision preparation is invalid");
        }
    }
}
