package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Executable entry point for the isolated Java 21 staff Discord bot process. */
public final class StaffBotApplication {
    private static final System.Logger LOGGER = System.getLogger(StaffBotApplication.class.getName());
    private static final Duration SMOKE_READY_TIMEOUT = Duration.ofSeconds(45);
    private static final int SUCCESS = 0;
    private static final int CONFIGURATION_ERROR = 2;
    private static final int RUNTIME_ERROR = 3;
    private static final int INTERRUPTED = 130;

    private StaffBotApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != SUCCESS) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        final StaffBotCommandLine commandLine;
        try {
            commandLine = StaffBotCommandLine.parse(args);
        } catch (IllegalArgumentException exception) {
            logIfEnabled(System.Logger.Level.ERROR, "staff_bot_invalid_arguments");
            return CONFIGURATION_ERROR;
        }
        return runConfigured(commandLine);
    }

    private static int runConfigured(StaffBotCommandLine commandLine) {
        final StaffBotConfiguration configuration;
        try {
            configuration = StaffBotConfiguration.fromStartup(commandLine);
        } catch (IllegalArgumentException exception) {
            logIfEnabled(System.Logger.Level.ERROR, "staff_bot_configuration_invalid");
            return CONFIGURATION_ERROR;
        }
        return runRuntime(
                configuration,
                commandLine.smokeTest(),
                commandLine.moderationConfigFile(),
                commandLine.tunnelFiles());
    }

    private static int runRuntime(
            StaffBotConfiguration configuration,
            boolean smokeTest,
            Optional<Path> moderationConfigFile,
            Optional<StaffBotCommandLine.TunnelFiles> tunnelFiles
    ) {
        try (StaffBotRuntime runtime = StaffBotRuntime.create(
                configuration, moderationConfigFile, tunnelFiles)) {
            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("staff-bot-shutdown").unstarted(runtime::close));
            runtime.start();

            if (smokeTest) {
                return runSmokeTest(runtime, configuration);
            }

            runtime.awaitTermination();
            return runtime.health().failedEver() ? RUNTIME_ERROR : SUCCESS;
        } catch (IllegalArgumentException exception) {
            logIfEnabled(System.Logger.Level.ERROR, "staff_bot_configuration_invalid");
            return CONFIGURATION_ERROR;
        } catch (IOException | RuntimeException exception) {
            logIfEnabled(System.Logger.Level.ERROR, "staff_bot_startup_failed");
            return RUNTIME_ERROR;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logIfEnabled(System.Logger.Level.WARNING, "staff_bot_interrupted");
            return INTERRUPTED;
        }
    }

    private static int runSmokeTest(StaffBotRuntime runtime, StaffBotConfiguration configuration)
            throws InterruptedException {
        boolean ready = runtime.awaitReady(SMOKE_READY_TIMEOUT);
        if (!ready || !runtime.health().isReady() || runtime.health().failedEver()) {
            return RUNTIME_ERROR;
        }
        logIfEnabled(
                System.Logger.Level.INFO,
                "staff_bot_smoke_ready environment={0}",
                configuration.environment().label());
        return SUCCESS;
    }

    private static void logIfEnabled(System.Logger.Level level, String message, Object... parameters) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, message, parameters);
        }
    }
}
