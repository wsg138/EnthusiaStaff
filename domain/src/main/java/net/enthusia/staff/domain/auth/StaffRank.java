package net.enthusia.staff.domain.auth;

public enum StaffRank {
    MOD,
    DEVELOPER,
    ADMIN,
    FOUNDER,
    SYSTEM;

    public boolean atLeast(StaffRank required) {
        return ordinal() >= required.ordinal();
    }
}
