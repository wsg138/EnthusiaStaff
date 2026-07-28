package net.enthusia.staff.integration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.inventory.InventoryObservation;
import net.enthusia.staff.domain.inventory.InventoryPrepareRequest;

final class InventoryRestorationTestSupport {
    private InventoryRestorationTestSupport() {
    }

    static InventoryPrepareRequest request(
            UUID operationId,
            InventoryObservation observation,
            CaseId caseId,
            UUID actorId,
            String operationType,
            byte[] replacement
    ) {
        return new InventoryPrepareRequest(
                operationId,
                "inventory:restoration-test:" + operationId,
                observation.playerId(),
                observation.scopeId(),
                observation.owningServerId(),
                actorId,
                Optional.of(caseId.value()),
                operationType,
                observation.revision(),
                observation.checksum(),
                observation.snapshot(),
                checksum(replacement),
                replacement,
                List.of(1),
                false
        );
    }

    static String checksum(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
