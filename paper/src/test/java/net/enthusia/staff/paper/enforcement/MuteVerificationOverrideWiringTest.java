package net.enthusia.staff.paper.enforcement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MuteVerificationOverrideWiringTest {
    @Test
    void vanillaChatHonorsOverrideOnlyBeforeUnverifiedCancellation() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/enforcement/MuteEnforcementListener.java"));
        int cacheLookup = source.indexOf("Entry entry = cache.get(player.getUniqueId())", source.indexOf("private void enforce("));
        int staleCheck = source.indexOf("if (entry == null ||", cacheLookup);
        int overrideCheck = source.indexOf("player.hasPermission(VERIFICATION_OVERRIDE_PERMISSION)", staleCheck);
        int staleCancel = source.indexOf("cancellation.accept(true)", staleCheck);
        int confirmedMuteCheck = source.indexOf("if (!entry.active().isEmpty())", staleCancel);

        assertTrue(cacheLookup >= 0 && staleCheck > cacheLookup);
        assertTrue(overrideCheck > staleCheck && overrideCheck < staleCancel);
        assertTrue(confirmedMuteCheck > staleCancel, "confirmed sanctions must still be blocked");
    }

    @Test
    void roseChatUsesTheSameOverrideForUnavailableAndUnverifiedEnforcement() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/enthusia/staff/paper/integration/RoseChatIntegration.java"));

        assertTrue(source.contains("case UNVERIFIED -> unverifiedDecision(context);"));
        assertTrue(source.contains("return unverifiedDecision(context);"));
        assertTrue(source.contains("sender.hasPermission(MuteEnforcementListener.VERIFICATION_OVERRIDE_PERMISSION)"));
    }
}
