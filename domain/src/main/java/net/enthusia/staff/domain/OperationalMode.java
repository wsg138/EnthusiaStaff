package net.enthusia.staff.domain;

public enum OperationalMode {
    BOOTSTRAP(false),
    DEGRADED(false),
    SHADOW_MIGRATION(false),
    ACTIVE(true),
    MAINTENANCE(false),
    READ_ONLY_FAILURE(false);

    private final boolean destructiveWritesAllowed;

    OperationalMode(boolean destructiveWritesAllowed) {
        this.destructiveWritesAllowed = destructiveWritesAllowed;
    }

    public boolean destructiveWritesAllowed() {
        return destructiveWritesAllowed;
    }
}
