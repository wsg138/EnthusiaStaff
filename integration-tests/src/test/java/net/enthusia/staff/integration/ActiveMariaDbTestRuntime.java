package net.enthusia.staff.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import net.enthusia.staff.domain.OperationalMode;
import net.enthusia.staff.persistence.DatabaseConfig;
import net.enthusia.staff.persistence.MariaDb;
import net.enthusia.staff.persistence.MariaDbRuntime;

final class ActiveMariaDbTestRuntime {
    private static final UUID TEST_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000701");

    private ActiveMariaDbTestRuntime() {
    }

    static MariaDbRuntime open(DatabaseConfig config) {
        MariaDbRuntime runtime = MariaDb.initialize(config);
        var current = runtime.operationalStateStore().current();
        try {
            assertTrue(runtime.operationalStateStore().transition(
                    current.revision(),
                    OperationalMode.ACTIVE,
                    TEST_ACTOR,
                    "MariaDB integration fixture requires active authority",
                    Instant.now()
            ));
            return runtime;
        } catch (RuntimeException | Error exception) {
            runtime.close();
            throw exception;
        }
    }
}
