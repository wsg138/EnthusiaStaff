package net.enthusia.staff.domain.evidence;

import java.time.Instant;

public record AutoClickerHandshakeEvidence(
        String modVersion,
        String loader,
        String minecraftVersion,
        Instant receivedAt
) {
    public AutoClickerHandshakeEvidence {
        if (modVersion == null || modVersion.isBlank() || modVersion.length() > 64
                || loader == null || loader.isBlank() || loader.length() > 32
                || minecraftVersion == null || minecraftVersion.isBlank()
                || minecraftVersion.length() > 32 || receivedAt == null) {
            throw new IllegalArgumentException("AutoClicker handshake fields are invalid");
        }
    }
}
