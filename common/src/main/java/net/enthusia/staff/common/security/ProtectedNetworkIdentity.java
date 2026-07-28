package net.enthusia.staff.common.security;

import java.util.Arrays;

public final class ProtectedNetworkIdentity {
    private final int equalityKeyVersion;
    private final byte[] equalityToken;
    private final int encryptionKeyVersion;
    private final byte[] encryptedValue;

    public ProtectedNetworkIdentity(
            int equalityKeyVersion,
            byte[] equalityToken,
            int encryptionKeyVersion,
            byte[] encryptedValue
    ) {
        if (equalityKeyVersion < 1 || equalityToken == null || equalityToken.length != 32
                || encryptionKeyVersion < 1 || encryptedValue == null || encryptedValue.length < 29) {
            throw new IllegalArgumentException("protected network identity fields are invalid");
        }
        this.equalityKeyVersion = equalityKeyVersion;
        this.equalityToken = equalityToken.clone();
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.encryptedValue = encryptedValue.clone();
    }

    public int equalityKeyVersion() {
        return equalityKeyVersion;
    }

    public byte[] equalityToken() {
        return equalityToken.clone();
    }

    public int encryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    public byte[] encryptedValue() {
        return encryptedValue.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProtectedNetworkIdentity identity)) {
            return false;
        }
        return equalityKeyVersion == identity.equalityKeyVersion
                && encryptionKeyVersion == identity.encryptionKeyVersion
                && Arrays.equals(equalityToken, identity.equalityToken)
                && Arrays.equals(encryptedValue, identity.encryptedValue);
    }

    @Override
    public int hashCode() {
        int result = 31 * equalityKeyVersion + encryptionKeyVersion;
        result = 31 * result + Arrays.hashCode(equalityToken);
        return 31 * result + Arrays.hashCode(encryptedValue);
    }
}
