package net.enthusia.staff.domain.website;

public record WebsiteAppealMutation(
        WebsiteAppealView appeal,
        boolean replayed,
        boolean claimed
) {
    public WebsiteAppealMutation {
        if (appeal == null) {
            throw new IllegalArgumentException("Website appeal mutation requires an appeal");
        }
    }
}
