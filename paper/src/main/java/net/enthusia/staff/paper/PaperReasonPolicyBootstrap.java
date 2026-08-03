package net.enthusia.staff.paper;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

final class PaperReasonPolicyBootstrap {
    private final Logger logger;

    PaperReasonPolicyBootstrap(Logger logger) {
        this.logger = logger;
    }

    Optional<AtomicReasonPolicyRepository> load(Path file, Consumer<String> degrade) {
        try {
            ReasonPolicyConfigurationLoader.LoadedPolicies loaded =
                    new ReasonPolicyConfigurationLoader().load(file);
            return Optional.of(new AtomicReasonPolicyRepository(
                    loaded.version(),
                    loaded.policies(),
                    loaded.aliases(),
                    loaded.removedReasons()
            ));
        } catch (ConfigurationValidationException exception) {
            degrade.accept(exception.getMessage());
            logger.log(
                    Level.SEVERE,
                    "Punishment policy validation failed; punishment commands are disabled",
                    exception
            );
            return Optional.empty();
        }
    }
}
