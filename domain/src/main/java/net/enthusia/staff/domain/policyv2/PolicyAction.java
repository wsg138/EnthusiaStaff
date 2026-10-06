package net.enthusia.staff.domain.policyv2;

import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.domain.auth.StaffRank;
import net.enthusia.staff.domain.sanction.SanctionSpec;

public sealed interface PolicyAction permits PolicyAction.Exact, PolicyAction.Bounded, PolicyAction.RequiresReview {
    record Exact(List<SanctionSpec> sanctions) implements PolicyAction {
        public Exact {
            if (sanctions == null || sanctions.isEmpty()
                    || sanctions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("exact sanctions must be non-empty");
            }
            requirePunitive(sanctions);
            sanctions = List.copyOf(sanctions);
        }
    }

    record Bounded(List<List<SanctionSpec>> allowedOptions, StaffRank minimumRank) implements PolicyAction {
        public Bounded {
            if (allowedOptions == null || allowedOptions.size() < 2 || minimumRank == null) {
                throw new IllegalArgumentException("bounded discretion requires options and an authority rank");
            }
            if (minimumRank != StaffRank.MOD && minimumRank != StaffRank.ADMIN && minimumRank != StaffRank.FOUNDER) {
                throw new IllegalArgumentException("bounded discretion requires MOD, ADMIN, or FOUNDER authority");
            }
            List<List<SanctionSpec>> copied = new ArrayList<>();
            for (List<SanctionSpec> option : allowedOptions) {
                if (option == null || option.isEmpty()
                        || option.stream().anyMatch(java.util.Objects::isNull)) {
                    throw new IllegalArgumentException("bounded sanction options must be non-empty");
                }
                requirePunitive(option);
                copied.add(List.copyOf(option));
            }
            allowedOptions = List.copyOf(copied);
            if (allowedOptions.stream().distinct().count() < 2) {
                throw new IllegalArgumentException("bounded discretion requires distinct options");
            }
        }
    }

    private static void requirePunitive(List<SanctionSpec> sanctions) {
        boolean remedyOnly = sanctions.stream().map(SanctionSpec::type).anyMatch(type -> switch (type) {
            case CONTENT_REMOVAL, STALL_OWNERSHIP_REMOVAL, INVENTORY_CONFISCATION,
                    ENDER_CHEST_CONFISCATION, ECONOMY_CONFISCATION -> true;
            default -> false;
        });
        if (remedyOnly) {
            throw new IllegalArgumentException("remedy actions must use RemedySpec, not punitive sanctions");
        }
    }

    record RequiresReview(String reasonCode) implements PolicyAction {
        public RequiresReview {
            reasonCode = PolicyIds.require(reasonCode, "review reason code");
        }
    }
}
