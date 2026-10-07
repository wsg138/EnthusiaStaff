package net.enthusia.staff.paper.config.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolicyV2PublicationServiceTest {
    private static final String POLICY_ONE = "policy.1";
    private static final String POLICY_FILE = "policy-v2.yml";
    private static final String STABLE = "Stable";

    @TempDir
    Path temp;

    @Test
    void failedReloadRetainsLastKnownGoodAndPublishesDiagnostic() throws Exception {
        Path file = temp.resolve(POLICY_FILE);
        Files.writeString(file, PolicyV2ConfigurationLoaderTest.validConfiguration(
                "shadow", POLICY_ONE, "First"
        ));
        AtomicReference<Optional<String>> issue = new AtomicReference<>(Optional.empty());
        PolicyV2PublicationService service = new PolicyV2PublicationService(
                file,
                Logger.getLogger("PolicyV2PublicationServiceTest"),
                issue::set
        );

        assertTrue(service.loadInitial().successful());
        Files.writeString(file, """
                schema-version: 1
                mode: shadow
                active-version: missing
                versions: []
                """);

        PolicyV2PublicationService.ReloadResult rejected = service.reload();

        assertEquals(PolicyV2PublicationService.Outcome.REJECTED, rejected.outcome());
        assertEquals(POLICY_ONE, service.activeSnapshot().version());
        assertTrue(service.shadowEnabled());
        assertTrue(issue.get().isPresent());
    }

    @Test
    void restartRecoversActiveAndHistoricalSnapshotsDeterministically() throws Exception {
        Path file = temp.resolve(POLICY_FILE);
        String configured = """
                schema-version: 1
                mode: shadow
                active-version: policy.2
                versions:
                %s%s
                """.formatted(
                PolicyV2ConfigurationLoaderTest.versionBlock(POLICY_ONE, "Historical"),
                PolicyV2ConfigurationLoaderTest.versionBlock("policy.2", "Active")
        );
        Files.writeString(file, configured);

        PolicyV2PublicationService first = service(file);
        PolicyV2PublicationService second = service(file);

        assertTrue(first.loadInitial().successful());
        assertTrue(second.loadInitial().successful());
        assertEquals(first.activeSnapshot(), second.activeSnapshot());
        assertEquals(first.snapshot(POLICY_ONE), second.snapshot(POLICY_ONE));
        assertEquals(POLICY_ONE, second.snapshot(POLICY_ONE).orElseThrow().version());
        assertEquals(2, second.view().retainedVersions());
        assertEquals(PolicyV2FeatureMode.SHADOW, second.mode());
    }

    @Test
    void reloadMovesDisabledToShadowAndBackToDisabled() throws Exception {
        Path file = temp.resolve(POLICY_FILE);
        Files.writeString(file, PolicyV2ConfigurationLoaderTest.validConfiguration(
                "disabled", POLICY_ONE, STABLE
        ));
        PolicyV2PublicationService service = service(file);

        assertTrue(service.loadInitial().successful());
        assertFalse(service.shadowEnabled());

        Files.writeString(file, PolicyV2ConfigurationLoaderTest.validConfiguration(
                "shadow", POLICY_ONE, STABLE
        ));
        assertTrue(service.reload().successful());
        assertTrue(service.shadowEnabled());

        Files.writeString(file, PolicyV2ConfigurationLoaderTest.validConfiguration(
                "disabled", POLICY_ONE, STABLE
        ));
        assertTrue(service.reload().successful());
        assertFalse(service.shadowEnabled());
    }

    @Test
    void identicalReloadIsIdempotent() throws Exception {
        Path file = temp.resolve(POLICY_FILE);
        Files.writeString(file, PolicyV2ConfigurationLoaderTest.validConfiguration(
                "disabled", POLICY_ONE, STABLE
        ));
        PolicyV2PublicationService service = service(file);

        assertEquals(PolicyV2PublicationService.Outcome.PUBLISHED, service.loadInitial().outcome());
        assertEquals(PolicyV2PublicationService.Outcome.UNCHANGED, service.reload().outcome());
        assertEquals(1, service.view().retainedVersions());
    }

    private static PolicyV2PublicationService service(Path file) {
        return new PolicyV2PublicationService(
                file,
                Logger.getLogger("PolicyV2PublicationServiceTest"),
                ignored -> {
                }
        );
    }
}
