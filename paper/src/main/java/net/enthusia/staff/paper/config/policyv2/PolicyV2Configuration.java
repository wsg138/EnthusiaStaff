package net.enthusia.staff.paper.config.policyv2;

import java.util.Map;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;

public record PolicyV2Configuration(
        int schemaVersion,
        PolicyV2FeatureMode mode,
        String activeVersion,
        Map<String, PolicySnapshot> snapshots
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PolicyV2Configuration {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported Policy v2 schema version " + schemaVersion);
        }
        if (mode == null || activeVersion == null || activeVersion.isBlank() || snapshots == null) {
            throw new IllegalArgumentException("Policy v2 publication metadata must be present");
        }
        activeVersion = activeVersion.trim();
        snapshots = Map.copyOf(snapshots);
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("Policy v2 must define at least one versioned snapshot");
        }
        if (!snapshots.containsKey(activeVersion)) {
            throw new IllegalArgumentException("active Policy v2 version is not present in versions");
        }
        snapshots.forEach((version, snapshot) -> {
            if (version == null || snapshot == null || !version.equals(snapshot.version())) {
                throw new IllegalArgumentException("Policy v2 version index does not match its snapshot");
            }
        });
    }

    public PolicySnapshot activeSnapshot() {
        return snapshots.get(activeVersion);
    }
}
