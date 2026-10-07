package net.enthusia.staff.paper.policyv2;

import java.util.Objects;
import net.enthusia.staff.common.CaseId;
import net.enthusia.staff.domain.policyv2.RemedySpec;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Lifecycle;
import net.enthusia.staff.domain.policyv2.enforcement.PolicyV2RemedyEnforcement.Scope;
import net.enthusia.staff.paper.inventory.ConfiscationCoordinator;
import org.bukkit.entity.Player;

/**
 * Routes Policy v2 confiscation through the existing fenced asset workflow.
 *
 * <p>This adapter deliberately does not advance the remedy lifecycle. The caller must only mark
 * enforcement complete after the durable inventory journal reports a committed confiscation.
 */
public final class PolicyV2AssetRemedyAdapter {
    private final ConfiscationCoordinator confiscation;

    public PolicyV2AssetRemedyAdapter(ConfiscationCoordinator confiscation) {
        this.confiscation = Objects.requireNonNull(confiscation, "confiscation");
    }

    public void open(Player actor, Player target, PolicyV2RemedyEnforcement enforcement) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        requireConfiscation(enforcement, target);
        confiscation.open(actor, target, new CaseId(enforcement.caseId()));
    }

    public void restore(Player actor, Player target, PolicyV2RemedyEnforcement enforcement) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        requireEnforcedConfiscation(enforcement, target);
        confiscation.restore(actor, target, new CaseId(enforcement.caseId()));
    }

    private static void requireConfiscation(
            PolicyV2RemedyEnforcement enforcement,
            Player target
    ) {
        Objects.requireNonNull(enforcement, "enforcement");
        if (enforcement.remedyType() != RemedySpec.Type.CONFISCATE
                || enforcement.scope() != Scope.ASSET
                || enforcement.lifecycle() != Lifecycle.REQUIRED
                || !enforcement.subjectId().equals(target.getUniqueId())) {
            throw new IllegalArgumentException(
                    "Policy v2 confiscation must be a required asset remedy for the target"
            );
        }
    }

    private static void requireEnforcedConfiscation(
            PolicyV2RemedyEnforcement enforcement,
            Player target
    ) {
        Objects.requireNonNull(enforcement, "enforcement");
        if (enforcement.remedyType() != RemedySpec.Type.CONFISCATE
                || enforcement.scope() != Scope.ASSET
                || enforcement.lifecycle() != Lifecycle.ENFORCED
                || !enforcement.subjectId().equals(target.getUniqueId())) {
            throw new IllegalArgumentException(
                    "Policy v2 restoration must be an enforced asset remedy for the target"
            );
        }
    }
}
