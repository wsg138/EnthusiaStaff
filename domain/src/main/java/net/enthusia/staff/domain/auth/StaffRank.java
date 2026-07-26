package net.enthusia.staff.domain.auth;

public enum StaffRank {
    MOD,
    DEVELOPER,
    ADMIN,
    FOUNDER,
    SYSTEM;

    public boolean atLeast(StaffRank required) {
        if (required == null) {
            return false;
        }
        return switch (this) {
            case MOD -> required == MOD;
            case DEVELOPER -> required == DEVELOPER;
            case ADMIN -> required == MOD || required == ADMIN;
            case FOUNDER -> required == MOD || required == ADMIN || required == FOUNDER;
            case SYSTEM -> required == SYSTEM;
        };
    }
}
