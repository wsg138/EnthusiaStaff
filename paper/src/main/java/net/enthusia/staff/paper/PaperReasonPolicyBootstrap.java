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
            warnIfPolicyVersionDiffers(loaded.version());
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

    private void warnIfPolicyVersionDiffers(String installedVersion) {
        try (InputStream source = bundledPolicy.get()) {
            if (source == null) {
                logger.warning("Packaged punishment policy unavailable for version comparison");
                return;
            }
            String packagedVersion = new ReasonPolicyConfigurationLoader()
                    .load(source, "bundled reason-policies.yml").version();
            if (!installedVersion.equals(packagedVersion) && logger.isLoggable(Level.WARNING)) {
                logger.warning("Installed punishment policy version " + installedVersion
                        + " differs from bundled version " + packagedVersion
                        + "; review the policy changes before applying a migration. "
                        + "Existing settings remain unchanged.");
            }
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Unable to compare installed and bundled punishment policy versions; "
                    + "existing policy remains unchanged", exception);
        }
    }
}
