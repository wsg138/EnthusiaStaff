package net.enthusia.staff.protocol;

public record VerificationResult(VerificationStatus status) {
    public boolean accepted() {
        return status == VerificationStatus.ACCEPTED;
    }
}
