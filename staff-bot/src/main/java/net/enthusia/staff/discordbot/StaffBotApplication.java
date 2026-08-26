package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.time.Duration;

/** Executable entry point for the isolated Java 21 staff Discord bot process. */
public final class StaffBotApplication {
    private static final System.Logger LOGGER = System.getLogger(StaffBotApplication.class.getName());
    private static final Duration SMOKE_READY_TIMEOUT = Duration.ofSeconds(45);
    private static final int SUCCESS = 0;
    private static final int CONFIGURATION_ERROR = 2;
    private static final int RUNTIME_ERROR = 3;
    private static final int INTERRUPTED = 130;
    private static final int NO_ARGUMENTS = 0;
    private static final int SMOKE_TEST_ARGUMENT_COUNT = 1;
    private static final String SMOKE_TEST_ARGUMENT = "--smoke-test";

    private StaffBotApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != SUCCESS) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        final boolean smokeTest;
        try {
            smokeTest = parseSmokeTest(args);
        } catch (IllegalArgumentException exception) {
            log(System.Logger.Level.ERROR, "staff_bot_invalid_arguments");
            return CONFIGURATION_ERROR;
        }
        return runConfigured(smokeTest);
    }

    private static int runConfigured(boolean smokeTest) {
        final StaffBotConfiguration configuration;
        try {
            configuration = StaffBotConfiguration.fromSystemEnvironment();
        } catch (IllegalArgumentException exception) {
            log(System.Logger.Level.ERROR, "staff_bot_configuration_invalid");
            return CONFIGURATION_ERROR;
        }
        return runRuntime(configuration, smokeTest);
    }

    private static int runRuntime(StaffBotConfiguration configuration, boolean smokeTest) {
        try (StaffBotRuntime runtime = StaffBotRuntime.create(configuration)) {
            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("staff-bot-shutdown").unstarted(runtime::close));
            runtime.start();

            if (smokeTest) {
                return runSmokeTest(runtime, configuration);
            }

            runtime.awaitTermination();
            return runtime.health().failedEver() ? RUNTIME_ERROR : SUCCESS;
        } catch (IOException | RuntimeException exception) {
            log(System.Logger.Level.ERROR, "staff_bot_startup_failed");
            return RUNTIME_ERROR;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log(System.Logger.Level.WARNING, "staff_bot_interrupted");
            return INTERRUPTED;
        }
    }

    private static int runSmokeTest(StaffBotRuntime runtime, StaffBotConfiguration configuration)
            throws InterruptedException {
        boolean ready = runtime.awaitReady(SMOKE_READY_TIMEOUT);
        if (!ready || !runtime.health().isReady() || runtime.health().failedEver()) {
            return RUNTIME_ERROR;
        }
        log(
                System.Logger.Level.INFO,
                "staff_bot_smoke_ready environment={0}",
                configuration.environment().label());
        return SUCCESS;
    }

    private static boolean parseSmokeTest(String[] args) {
        if (args.length == NO_ARGUMENTS) {
            return false;
        }
        if (args.length == SMOKE_TEST_ARGUMENT_COUNT && SMOKE_TEST_ARGUMENT.equals(args[0])) {
            return true;
        }
        throw new IllegalArgumentException("unsupported staff bot arguments");
    }

    private static void log(System.Logger.Level level, String message, Object... parameters) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, message, parameters);
        }
    }
}
