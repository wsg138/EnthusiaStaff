package net.enthusia.staff.discordbot;

import java.io.IOException;
import java.time.Duration;

/** Executable entry point for the isolated Java 21 staff Discord bot process. */
public final class StaffBotApplication {
    private static final System.Logger LOGGER = System.getLogger(StaffBotApplication.class.getName());
    private static final Duration SMOKE_READY_TIMEOUT = Duration.ofSeconds(45);

    private StaffBotApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        final boolean smokeTest;
        try {
            smokeTest = parseSmokeTest(args);
        } catch (IllegalArgumentException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "staff_bot_invalid_arguments");
            return 2;
        }

        final StaffBotConfiguration configuration;
        try {
            configuration = StaffBotConfiguration.fromSystemEnvironment();
        } catch (IllegalArgumentException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "staff_bot_configuration_invalid");
            return 2;
        }

        try (StaffBotRuntime runtime = StaffBotRuntime.create(configuration)) {
            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("staff-bot-shutdown").unstarted(runtime::close));
            runtime.start();

            if (smokeTest) {
                boolean ready = runtime.awaitReady(SMOKE_READY_TIMEOUT);
                if (!ready || runtime.health().failedEver()) {
                    return 3;
                }
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "staff_bot_smoke_ready environment={0}",
                        configuration.environment().label());
                return 0;
            }

            runtime.awaitTermination();
            return runtime.health().failedEver() ? 3 : 0;
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "staff_bot_startup_failed");
            return 3;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING, "staff_bot_interrupted");
            return 130;
        }
    }

    private static boolean parseSmokeTest(String[] args) {
        if (args.length == 0) {
            return false;
        }
        if (args.length == 1 && "--smoke-test".equals(args[0])) {
            return true;
        }
        throw new IllegalArgumentException("unsupported staff bot arguments");
    }
}
