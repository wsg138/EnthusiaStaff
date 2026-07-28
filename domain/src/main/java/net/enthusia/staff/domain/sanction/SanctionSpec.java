package net.enthusia.staff.domain.sanction;

public record SanctionSpec(SanctionType type, SanctionLength length) {
    public SanctionSpec {
        if (type == null || length == null) {
            throw new IllegalArgumentException("sanction type and length must be present");
        }
        boolean oneShot = type == SanctionType.WARNING || type == SanctionType.KICK
                || type == SanctionType.CONTENT_REMOVAL || type == SanctionType.STALL_OWNERSHIP_REMOVAL
                || type == SanctionType.INVENTORY_CONFISCATION || type == SanctionType.ENDER_CHEST_CONFISCATION
                || type == SanctionType.ECONOMY_CONFISCATION;
        if (oneShot != length.isInstant()) {
            throw new IllegalArgumentException(type + (oneShot
                    ? " requires an instant action length"
                    : " requires a temporary or permanent length"));
        }
    }
}
