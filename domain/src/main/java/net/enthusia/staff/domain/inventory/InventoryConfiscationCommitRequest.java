package net.enthusia.staff.domain.inventory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InventoryConfiscationCommitRequest(
        UUID operationId,
        long fencingToken,
        long expectedRevision,
        String expectedChecksum,
        String replacementChecksum,
        byte[] replacementSnapshot,
        List<Integer> changedSlots,
        String assetsChecksum,
        byte[] assetsSnapshot,
        List<String> selectedPaths
) {
    public InventoryConfiscationCommitRequest {
        Objects.requireNonNull(operationId, "operationId");
        if (fencingToken < 1L || expectedRevision < 0L) {
            throw new IllegalArgumentException("confiscation commit fence or revision is invalid");
        }
        expectedChecksum = InventoryObservation.requireChecksum(expectedChecksum);
        replacementChecksum = InventoryObservation.requireChecksum(replacementChecksum);
        replacementSnapshot = Objects.requireNonNull(replacementSnapshot, "replacementSnapshot").clone();
        if (replacementSnapshot.length == 0) {
            throw new IllegalArgumentException("replacementSnapshot cannot be empty");
        }
        changedSlots = List.copyOf(Objects.requireNonNull(changedSlots, "changedSlots"));
        if (changedSlots.isEmpty()
                || changedSlots.stream().anyMatch(slot -> slot == null || slot < 0 || slot > 127)) {
            throw new IllegalArgumentException("changedSlots must contain valid inventory slots");
        }
        assetsChecksum = InventoryObservation.requireChecksum(assetsChecksum);
        assetsSnapshot = Objects.requireNonNull(assetsSnapshot, "assetsSnapshot").clone();
        if (assetsSnapshot.length == 0) {
            throw new IllegalArgumentException("assetsSnapshot cannot be empty");
        }
        selectedPaths = List.copyOf(Objects.requireNonNull(selectedPaths, "selectedPaths"));
        if (selectedPaths.isEmpty() || selectedPaths.size() > 16_384
                || selectedPaths.stream().anyMatch(path ->
                path == null || !path.matches("[0-9]{1,3}(?:/[0-9]{1,4}){0,16}"))) {
            throw new IllegalArgumentException("selectedPaths contains an invalid nested path");
        }
    }

    @Override
    public byte[] replacementSnapshot() {
        return replacementSnapshot.clone();
    }

    @Override
    public byte[] assetsSnapshot() {
        return assetsSnapshot.clone();
    }
}
