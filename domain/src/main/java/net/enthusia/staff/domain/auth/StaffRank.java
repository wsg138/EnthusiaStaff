package net.enthusia.staff.domain.auth;

public enum StaffRank {
    HELPER,
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
            case HELPER -> required == HELPER;
            case MOD -> required == HELPER || required == MOD;
            case DEVELOPER -> required == DEVELOPER;
            case ADMIN -> required == HELPER || required == MOD || required == ADMIN;
            case FOUNDER -> required == HELPER || required == MOD || required == ADMIN || required == FOUNDER;
            case SYSTEM -> required == SYSTEM;
        };
    }

    public boolean canApprovePunishmentRequests() {
        return this == MOD || this == ADMIN || this == FOUNDER;
    }
}
