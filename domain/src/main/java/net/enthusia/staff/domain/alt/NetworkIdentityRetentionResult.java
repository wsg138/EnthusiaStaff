package net.enthusia.staff.domain.alt;

public record NetworkIdentityRetentionResult(
        int identityTokensDeleted,
        int evidenceRowsDeleted
) {
    public NetworkIdentityRetentionResult {
        if (identityTokensDeleted < 0 || evidenceRowsDeleted < 0) {
            throw new IllegalArgumentException("network identity retention counts cannot be negative");
        }
    }

    public int totalDeleted() {
        return identityTokensDeleted + evidenceRowsDeleted;
    }
}
