package net.enthusia.staff.paper.economy;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record CurrencyAccountState(
        UUID playerId,
        long bankBalance,
        long bankRevision,
        byte[] inventory,
        byte[] enderChest,
        long inventoryValue,
        long enderChestValue,
        long authoritativeTotal,
        String checksum
) {
    public CurrencyAccountState {
        Objects.requireNonNull(playerId, "playerId");
        inventory = Objects.requireNonNull(inventory, "inventory").clone();
        enderChest = Objects.requireNonNull(enderChest, "enderChest").clone();
        if (bankBalance < 0L || bankRevision < 0L || inventoryValue < 0L
                || enderChestValue < 0L || authoritativeTotal < 0L) {
            throw new IllegalArgumentException("currency account values cannot be negative");
        }
        if (Math.addExact(bankBalance, Math.addExact(inventoryValue, enderChestValue))
                != authoritativeTotal) {
            throw new IllegalArgumentException("authoritative total does not match its components");
        }
        checksum = requireChecksum(checksum);
    }

    @Override
    public byte[] inventory() {
        return inventory.clone();
    }

    @Override
    public byte[] enderChest() {
        return enderChest.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyAccountState that)) {
            return false;
        }
        return bankBalance == that.bankBalance
                && bankRevision == that.bankRevision
                && inventoryValue == that.inventoryValue
                && enderChestValue == that.enderChestValue
                && authoritativeTotal == that.authoritativeTotal
                && playerId.equals(that.playerId)
                && Arrays.equals(inventory, that.inventory)
                && Arrays.equals(enderChest, that.enderChest)
                && checksum.equals(that.checksum);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                playerId,
                bankBalance,
                bankRevision,
                inventoryValue,
                enderChestValue,
                authoritativeTotal,
                checksum
        );
        result = 31 * result + Arrays.hashCode(inventory);
        return 31 * result + Arrays.hashCode(enderChest);
    }

    static String requireChecksum(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksum must be a lowercase SHA-256 value");
        }
        return value;
    }
}
