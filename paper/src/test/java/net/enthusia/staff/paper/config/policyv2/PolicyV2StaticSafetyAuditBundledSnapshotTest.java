package net.enthusia.staff.paper.config.policyv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.enthusia.staff.domain.policyv2.PolicyV2StaticSafetyAudit;
import org.junit.jupiter.api.Test;

/** Integration evidence for the immutable first owner policy snapshot. */
class PolicyV2StaticSafetyAuditBundledSnapshotTest {
    private static final String ARCHIVED_VERSION = "owner.2026-10-07.1";

    @Test
    void publishedFirstOwnerSnapshotIsExplicitlyNotCutoverSafe() {
        var config = new PolicyV2ConfigurationLoader().load(
                PolicyV2StaticSafetyAuditBundledSnapshotTest.class.getResourceAsStream("/policy-v2.yml"),
                "policy-v2.yml"
        );
        assertEquals(PolicyV2FeatureMode.DISABLED, config.mode());
        var archived = config.snapshots().get(ARCHIVED_VERSION);
        var audit = PolicyV2StaticSafetyAudit.inspect(archived);
        assertEquals(ARCHIVED_VERSION, audit.snapshotVersion());
        assertTrue(audit.findings().stream().anyMatch(item ->
                item.code().equals("blackmail.missing-real-world-evidence-fields")));
        assertTrue(audit.findings().stream().anyMatch(item ->
                item.code().equals("blackmail.ungated-punitive-rule")));
        assertTrue(audit.findings().stream().anyMatch(item ->
                item.code().equals("remedy.unsupported-other")));
        assertTrue(!audit.passesStaticChecks());
    }
}
