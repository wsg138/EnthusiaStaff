package net.enthusia.staff.domain.policyv2.publicview;

import java.util.Optional;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;

/**
 * Public-consumer read boundary for Policy v2.
 *
 * <p>Website, Discord, and other public surfaces should depend on this port
 * instead of receiving the private PolicyV2Store contract.</p>
 */
@FunctionalInterface
public interface PolicyV2PublicProjectionReader {
    Optional<PolicyV2PublicProjection> publicProjection(String caseId);
}
