package net.enthusia.staff.domain.ports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionLength;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import org.junit.jupiter.api.Test;

class AtomicReasonPolicyRepositoryTest {
    @Test
    void createsIndependentPolarFamiliesFromTheValidatedTemplate() {
        ReasonPolicy template = new ReasonPolicy(
                "cheating.polar.template",
                "cheating.polar.template",
                "Polar automatic detection",
                75,
                false,
                List.of(new PunishmentStep(0, "30 day ban", List.of(
                        new SanctionSpec(SanctionType.NETWORK_BAN, SanctionLength.temporary(Duration.ofDays(30)))
                )))
        );
        AtomicReasonPolicyRepository repository = new AtomicReasonPolicyRepository("v1", List.of(template));

        ReasonPolicy speed = repository.find("cheating.polar.speed").orElseThrow();
        ReasonPolicy reach = repository.find("cheating.polar.reach").orElseThrow();

        assertEquals("cheating.polar.speed", speed.family());
        assertEquals("cheating.polar.reach", reach.family());
        assertNotEquals(speed.family(), reach.family());
        assertTrue(repository.find("cheating.polar.Invalid Family").isEmpty());
    }
}
