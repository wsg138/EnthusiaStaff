package net.enthusia.staff.protocol;

public enum VerificationStatus {
    ACCEPTED,
    UNKNOWN_SERVER,
    UNSUPPORTED_VERSION,
    EXPIRED,
    FUTURE_TIMESTAMP,
    INVALID_MAC,
    REPLAYED
}
