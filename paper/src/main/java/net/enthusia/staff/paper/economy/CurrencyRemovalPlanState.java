package net.enthusia.staff.paper.economy;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CurrencyRemovalPlanState(
        UUID operationId,
        UUID playerId,
        long amount,
        CurrencyAccountState before,
        long replacementBankBalance,
        byte[] replacementInventory,
        byte[] replacementEnderChest,
        long expectedFinalTotal,
        String replacementChecksum,
        List<CurrencyAssetSource> sourceOrder
) {
    public CurrencyRemovalPlanState {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(before, "before");
        if (!playerId.equals(before.playerId()) || amount <= 0L
                || amount > before.authoritativeTotal()) {
            throw new IllegalArgumentException("currency removal plan identity or amount is invalid");
        }
        replacementInventory = Objects.requireNonNull(
                replacementInventory,
                "replacementInventory"
        ).clone();
        replacementEnderChest = Objects.requireNonNull(
                replacementEnderChest,
                "replacementEnderChest"
        ).clone();
        if (replacementBankBalance < 0L
                || expectedFinalTotal != before.authoritativeTotal() - amount) {
            throw new IllegalArgumentException("currency removal replacement totals are invalid");
        }
        replacementChecksum = CurrencyAccountState.requireChecksum(replacementChecksum);
        sourceOrder = List.copyOf(Objects.requireNonNull(sourceOrder, "sourceOrder"));
        if (sourceOrder.size() != CurrencyAssetSource.values().length
                || !EnumSet.copyOf(sourceOrder).equals(EnumSet.allOf(CurrencyAssetSource.class))) {
            throw new IllegalArgumentException("sourceOrder must contain each source exactly once");
        }
    }

    @Override
    public byte[] replacementInventory() {
        return replacementInventory.clone();
    }

    @Override
    public byte[] replacementEnderChest() {
        return replacementEnderChest.clone();
    }
}
