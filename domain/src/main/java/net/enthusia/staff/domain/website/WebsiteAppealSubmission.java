package net.enthusia.staff.domain.website;

public record WebsiteAppealSubmission(
        WebsiteAppealView appeal,
        boolean replayed
) {
    public WebsiteAppealSubmission {
        if (appeal == null) {
            throw new IllegalArgumentException("Website appeal submission is required");
        }
    }
}
