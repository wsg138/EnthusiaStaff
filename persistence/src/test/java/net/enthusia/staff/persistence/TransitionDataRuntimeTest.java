package net.enthusia.staff.persistence;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TransitionDataRuntimeTest {
    private static final String INITIAL_MIGRATION = "db/migration/V1__initial_schema.sql";
    private static final String DISCORD_PERSISTENCE_MIGRATION =
            "db/migration/V19__discord_moderation_persistence.sql";
    private static final String DISCORD_LINKING_MIGRATION =
            "db/migration/V20__discord_account_linking.sql";

    @Test
    void migrationResourcesUseOwningClassLoaderWhenHostContextCannotSeePluginResources() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader hostOnly = new ClassLoader(null) {
        };
        Thread.currentThread().setContextClassLoader(hostOnly);
        try {
            assertNull(hostOnly.getResource(DISCORD_PERSISTENCE_MIGRATION));

            ClassLoader migrationLoader = TransitionDataRuntime.migrationClassLoader();
            assertNotSame(hostOnly, migrationLoader);
            assertNotNull(migrationLoader.getResource(INITIAL_MIGRATION));
            assertNotNull(migrationLoader.getResource(DISCORD_PERSISTENCE_MIGRATION));
            assertNotNull(migrationLoader.getResource(DISCORD_LINKING_MIGRATION));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void migrationResourceGuardRejectsAHostLoaderWithoutBundledMigrations() {
        ClassLoader hostOnly = new ClassLoader(null) {
        };

        assertThrows(
                IllegalStateException.class,
                () -> TransitionDataRuntime.requireMigrationResources(hostOnly));
    }
}
