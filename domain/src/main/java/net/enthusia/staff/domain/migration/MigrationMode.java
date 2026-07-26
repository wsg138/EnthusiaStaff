package net.enthusia.staff.domain.migration;

public enum MigrationMode {
    DRY_RUN,
    IMPORT,
    SHADOW,
    CUTOVER
}
