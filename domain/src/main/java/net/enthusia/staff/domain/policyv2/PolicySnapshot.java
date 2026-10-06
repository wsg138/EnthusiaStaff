package net.enthusia.staff.domain.policyv2;

import java.util.List;
import java.util.Optional;

public record PolicySnapshot(String version, List<OffensePolicy> offenses) {
    public PolicySnapshot {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("policy version must not be blank");
        }
        version = version.trim();
        if (offenses == null) {
            throw new IllegalArgumentException("policy offenses must be present");
        }
        offenses = List.copyOf(offenses);
        PolicyConfigurationValidator.validate(offenses);
    }

    public Optional<OffensePolicy> offense(String offenseId) {
        return offenses.stream().filter(offense -> offense.id().equals(offenseId)).findFirst();
    }
}
