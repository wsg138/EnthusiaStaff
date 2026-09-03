package net.enthusia.staff.domain.auth;

/**
 * Authoritative staff operations exposed by the Discord moderation surface.
 *
 * <p>Command visibility and Discord roles are intentionally not represented here. Entry points must
 * authorize through {@link DiscordModerationAuthorizationService}.</p>
 */
public enum DiscordModerationOperation {
    VIEW_LINKED_ACCOUNTS(false, false),
    VIEW_HISTORY(false, false),
    VIEW_NOTES(false, false),
    VIEW_EVIDENCE(false, false),
    ISSUE_SANCTION(true, true),
    END_SANCTION(true, false),
    REVOKE_SANCTION(true, false),
    APPROVE_SANCTION_REQUEST(true, false),
    REQUEST_OVERTURN(true, false),
    APPROVE_OVERTURN(true, false),
    FULL_OVERTURN(true, false);

    private final boolean mutation;
    private final boolean consequencesRequired;

    DiscordModerationOperation(boolean mutation, boolean consequencesRequired) {
        this.mutation = mutation;
        this.consequencesRequired = consequencesRequired;
    }

    public boolean isMutation() {
        return mutation;
    }

    public boolean consequencesRequired() {
        return consequencesRequired;
    }
}
