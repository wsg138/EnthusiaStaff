package net.enthusia.staff.paper;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;

final class PaperDatabaseConfiguration {
    private final Settings settings;
    private final Function<String, String> environment;

    PaperDatabaseConfiguration(Settings settings, Function<String, String> environment) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    static Settings snapshot(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new Settings(
                configuration.getString("storage.jdbc-url-environment"),
                configuration.getString("storage.username-environment"),
                configuration.getString("storage.password-environment"),
                configuration.getInt("storage.maximum-pool-size", 8),
                configuration.getLong("storage.connection-timeout-millis", 5_000)
        );
    }

    Optional<DatabaseConfig> load() {
        String url = environment(settings.jdbcUrlEnvironment());
        String username = environment(settings.usernameEnvironment());
        String password = environment(settings.passwordEnvironment());
        if (url == null || username == null || password == null) {
            return Optional.empty();
        }
        return Optional.of(new DatabaseConfig(
                url,
                username,
                password,
                settings.maximumPoolSize(),
                settings.connectionTimeoutMillis()
        ));
    }

    private String environment(String variable) {
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String value = environment.apply(variable);
        return value == null || value.isBlank() ? null : value;
    }

    record Settings(
            String jdbcUrlEnvironment,
            String usernameEnvironment,
            String passwordEnvironment,
            int maximumPoolSize,
            long connectionTimeoutMillis
    ) {
    }
}
