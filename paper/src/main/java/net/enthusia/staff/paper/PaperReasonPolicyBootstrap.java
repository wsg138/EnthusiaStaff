package net.enthusia.staff.paper;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.ports.AtomicReasonPolicyRepository;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

final class PaperReasonPolicyBootstrap {
    private final Logger logger;
    private final Supplier<InputStream> bundledPolicy;

    PaperReasonPolicyBootstrap(Logger logger) {
        this(logger, () -> PaperReasonPolicyBootstrap.class.getResourceAsStream("/reason-policies.yml"));
    }

    PaperReasonPolicyBootstrap(Logger logger, Supplier<InputStream> bundledPolicy) {
        this.logger = Objects.requireNonNull(logger);
        this.bundledPolicy = Objects.requireNonNull(bundledPolicy);
    }

    Optional<AtomicReasonPolicyRepository> load(Path file, Consumer<String> degrade) {
        try {
            ReasonPolicyConfigurationLoader.LoadedPolicies loaded =
                    new ReasonPolicyConfigurationLoader().load(file);
            warnIfPolicyDrifts(loaded);
            return Optional.of(new AtomicReasonPolicyRepository(
                    loaded.version(),
                    loaded.policies(),
                    loaded.aliases(),
                    loaded.removedReasons()
            ));
        } catch (IllegalArgumentException exception) {
            degrade.accept(exception.getMessage());
            logger.log(
                    Level.SEVERE,
                    "Punishment policy validation failed; punishment commands are disabled",
                    exception
            );
            return Optional.empty();
        }
    }

    private void warnIfPolicyDrifts(ReasonPolicyConfigurationLoader.LoadedPolicies installed) {
        try (InputStream source = bundledPolicy.get()) {
            if (source == null) {
                logger.warning("Packaged punishment policy unavailable for version comparison");
                return;
            }
            ReasonPolicyConfigurationLoader.LoadedPolicies packaged =
                    new ReasonPolicyConfigurationLoader().load(source, "bundled reason-policies.yml");
            if (!installed.version().equals(packaged.version()) && logger.isLoggable(Level.WARNING)) {
                logger.warning("Installed punishment policy version " + installed.version()
                        + " differs from bundled version " + packaged.version()
                        + "; review the policy changes before applying a migration. "
                        + "Existing settings remain unchanged.");
            } else if (installed.version().equals(packaged.version())
                    && !installed.equals(packaged) && logger.isLoggable(Level.WARNING)) {
                // A copied or locally edited policy can retain its version while
                // changing sanctions. Warn rather than overwrite the custom file.
                logger.warning("Installed punishment policy content differs from bundled policy "
                        + "despite identical version " + installed.version()
                        + "; review custom policy changes before any migration. "
                        + "Existing settings remain unchanged.");
            }
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Unable to compare installed and bundled punishment policy versions; "
                    + "existing policy remains unchanged", exception);
        }
    }
}
