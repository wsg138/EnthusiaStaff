package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.Test;

final class StaffStateCodecTest {
    @Test
    void corruptedSnapshotIsRejectedBeforeReadingPlayerState() {
        assertThrows(IllegalStateException.class,
                () -> new StaffStateCodec().verifiedRestorationChecksum(null, "SMP", new byte[]{1}, "incorrect"));
    }
    @Test
    void infiniteStrengthSnapshotCanBeRestoredWithoutChangingItsAmplifier() {
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(PotionEffect.INFINITE_DURATION, 255));
    }

    @Test
    void finiteEffectsAndExpiredEffectsRemainSupported() {
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(1200, 0));
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(0, 0));
        assertDoesNotThrow(() -> StaffStateCodec.validatePotionEffectValues(Integer.MAX_VALUE, 255));
    }

    @Test
    void invalidNegativeDurationsAndAmplifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> StaffStateCodec.validatePotionEffectValues(-2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StaffStateCodec.validatePotionEffectValues(Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StaffStateCodec.validatePotionEffectValues(PotionEffect.INFINITE_DURATION, -1));
    }
}
