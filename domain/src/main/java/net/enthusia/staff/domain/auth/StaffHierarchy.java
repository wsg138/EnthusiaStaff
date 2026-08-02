package net.enthusia.staff.domain.auth;

public final class StaffHierarchy {
    private StaffHierarchy() {
    }

    public static boolean mayMutate(StaffRank actor, StaffRank issuer, boolean bypassHierarchy) {
        if (actor == null || issuer == null) {
            return false;
        }
        if (issuer == StaffRank.SYSTEM) {
            return false;
        }
        if (bypassHierarchy) {
            return actor == StaffRank.FOUNDER;
        }
        return switch (actor) {
            case FOUNDER -> issuer != StaffRank.SYSTEM;
            case ADMIN -> issuer == StaffRank.HELPER
                    || issuer == StaffRank.MOD
                    || issuer == StaffRank.DEVELOPER
                    || issuer == StaffRank.ADMIN;
            case MOD -> issuer == StaffRank.HELPER
                    || issuer == StaffRank.MOD
                    || issuer == StaffRank.DEVELOPER;
            case HELPER -> issuer == StaffRank.HELPER;
            case DEVELOPER, SYSTEM -> false;
        };
    }
}
