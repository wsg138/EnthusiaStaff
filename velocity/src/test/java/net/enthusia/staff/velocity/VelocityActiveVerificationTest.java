package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.enthusia.staff.domain.OperationalMode;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class VelocityActiveVerificationTest {
    @Test
    void committedActivationRemainsActiveWhileReportingMissingServices() {
        var snapshot = new NetworkVerificationState.Snapshot(
                OperationalMode.ACTIVE, null, null, Set.of(), Set.of(), true, false, false);
        var lines = new VelocityVerificationRenderer().render(snapshot, Map.of(),
                new NetworkVerificationState.Cutover(true, true, List.of(), true))
                .stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
        assertTrue(lines.stream().anyMatch(line -> line.contains("durable activation receipt verified")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Discord webhook delivery worker is unavailable")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("ACTIVE • FOLLOW-UP REQUIRED")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("Why not ACTIVE") || line.contains("writes frozen")));
    }
}
