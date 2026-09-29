package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.protocol.ProtocolEnvelope;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VelocityNetworkVerifierTest {
    @TempDir
    Path directory;

    @Test
    void missingCoreDependenciesProduceShortReadableBlockers() {
        VelocityNetworkVerifier verifier = verifier();

        List<String> lines = verifier.verify().stream().map(VelocityNetworkVerifierTest::plain).toList();

        assertTrue(lines.stream().anyMatch(line -> line.contains("Core")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Discord & Website")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Why not ACTIVE")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("NOT READY FOR ACTIVE TESTING")));
        assertTrue(lines.size() < 24, "verification output should stay dashboard-sized");
        assertFalse(lines.stream().anyMatch(line -> line.length() > 140), "individual lines should stay readable");
    }

    @Test
    void malformedAuthenticatedReportIsConsumedWithoutBecomingReadinessEvidence() {
        VelocityNetworkVerifier verifier = verifier();
        ProtocolEnvelope envelope = new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "SMP",
                VelocityNetworkVerifier.VERIFY_REPORT,
                Clock.systemUTC().millis(),
                "nonce",
                "{not-json}",
                "mac"
        );

        assertTrue(verifier.acceptReport(envelope));
        assertTrue(verifier.verify().stream().map(VelocityNetworkVerifierTest::plain)
                .anyMatch(line -> line.contains("NOT READY FOR ACTIVE TESTING")));
    }

    @Test
    void unrelatedChannelMessageIsNotClaimedByVerifier() {
        VelocityNetworkVerifier verifier = verifier();
        ProtocolEnvelope envelope = new ProtocolEnvelope(
                1,
                UUID.randomUUID(),
                "SMP",
                "PUNISHMENT_CREATED",
                Clock.systemUTC().millis(),
                "nonce",
                "{}",
                "mac"
        );

        assertFalse(verifier.acceptReport(envelope));
    }

    private VelocityNetworkVerifier verifier() {
        return new VelocityNetworkVerifier(new VelocityNetworkVerifier.Dependencies(
                () -> OperationalMode.SHADOW_MIGRATION,
                () -> null,
                () -> null,
                () -> null,
                () -> false,
                () -> false,
                () -> false,
                directory
        ));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
