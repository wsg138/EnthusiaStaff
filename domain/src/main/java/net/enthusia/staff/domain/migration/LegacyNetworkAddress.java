package net.enthusia.staff.domain.migration;

import java.util.Arrays;
import java.util.HexFormat;

public final class LegacyNetworkAddress {
    private final byte[] addressBytes;

    public LegacyNetworkAddress(byte[] addressBytes) {
        if (addressBytes == null || addressBytes.length != 4 && addressBytes.length != 16) {
            throw new IllegalArgumentException("legacy network address must contain IPv4 or IPv6 bytes");
        }
        this.addressBytes = addressBytes.clone();
    }

    public byte[] addressBytes() {
        return addressBytes.clone();
    }

    public String canonicalHex() {
        return HexFormat.of().formatHex(addressBytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LegacyNetworkAddress address
                && Arrays.equals(addressBytes, address.addressBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(addressBytes);
    }
}
