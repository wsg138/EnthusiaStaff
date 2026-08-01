package net.enthusia.staff.paper;

import java.util.Optional;
import java.util.function.Function;
import net.enthusia.staff.persistence.DatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;

final class PaperDatabaseConfiguration {
    private final FileConfiguration configuration;
    private final Function<String, String> environment;

    PaperDatabaseConfiguration(FileConfiguration configuration, Function<String, String> environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    Optional<DatabaseConfig> load() {
        String url = environment("storage.jdbc-url-environment");
        String username = environment("storage.username-environment");
        String password = environment("storage.password-environment");
        if (url == null || username == null || password == null) {
            return Optional.empty();
        }
        return Optional.of(new DatabaseConfig(
                url,
                username,
                password,
                configuration.getInt("storage.maximum-pool-size", 8),
                configuration.getLong("storage.connection-timeout-millis", 5_000)
        ));
    }

    private String environment(String configurationPath) {
        String variable = configuration.getString(configurationPath);
        if (variable == null || variable.isBlank()) {
            return null;
        }
        String value = environment.apply(variable);
        return value == null || value.isBlank() ? null : value;
    }
}
