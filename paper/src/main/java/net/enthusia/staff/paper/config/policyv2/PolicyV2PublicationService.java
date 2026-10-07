package net.enthusia.staff.paper.config.policyv2;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.enthusia.staff.domain.policyv2.PolicySnapshot;

public final class PolicyV2PublicationService {
    private static final int MAX_DIAGNOSTIC_LENGTH = 300;

    private final Path file;
    private final PolicyV2ConfigurationLoader loader;
    private final PolicyV2SnapshotPublisher publisher;
    private final Logger logger;
    private final Consumer<Optional<String>> issueSink;

    public PolicyV2PublicationService(
            Path file,
            Logger logger,
            Consumer<Optional<String>> issueSink
    ) {
        this(file, new PolicyV2ConfigurationLoader(), new PolicyV2SnapshotPublisher(), logger, issueSink);
    }

    PolicyV2PublicationService(
            Path file,
            PolicyV2ConfigurationLoader loader,
            PolicyV2SnapshotPublisher publisher,
            Logger logger,
            Consumer<Optional<String>> issueSink
    ) {
        this.file = java.util.Objects.requireNonNull(file, "file");
        this.loader = java.util.Objects.requireNonNull(loader, "loader");
        this.publisher = java.util.Objects.requireNonNull(publisher, "publisher");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
        this.issueSink = java.util.Objects.requireNonNull(issueSink, "issueSink");
    }

    public ReloadResult loadInitial() {
        return attempt("startup");
    }

    public ReloadResult reload() {
        return attempt("reload");
    }

    public boolean shadowEnabled() {
        return publisher.shadowEnabled();
    }

    public PolicyV2FeatureMode mode() {
        return publisher.mode();
    }

    public PolicySnapshot activeSnapshot() {
        return publisher.activeSnapshot();
    }

    public Optional<PolicySnapshot> snapshot(String version) {
        return publisher.snapshot(version);
    }

    public PolicyV2SnapshotPublisher.View view() {
        return publisher.view();
    }

    private ReloadResult attempt(String operation) {
        try {
            PolicyV2Configuration candidate = loader.load(file);
            PolicyV2SnapshotPublisher.Publication published = publisher.publish(candidate);
            issueSink.accept(Optional.empty());
            ReloadResult result = success(operation, published);
            if (logger.isLoggable(Level.INFO)) {
                logger.info(result.message());
            }
            return result;
        } catch (RuntimeException exception) {
            PolicyV2SnapshotPublisher.View retained = publisher.view();
            String detail = diagnostic(exception);
            String message = "Policy v2 " + operation + " rejected; previous valid publication retained";
            issueSink.accept(Optional.of(message));
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, message + ": " + detail);
            }
            return new ReloadResult(
                    Outcome.REJECTED,
                    retained.mode(),
                    retained.activeVersion(),
                    retained.retainedVersions(),
                    message,
                    List.of(detail)
            );
        }
    }

    private static ReloadResult success(
            String operation,
            PolicyV2SnapshotPublisher.Publication publication
    ) {
        Outcome outcome = publication.changed() ? Outcome.PUBLISHED : Outcome.UNCHANGED;
        String version = publication.activeVersion().orElse("none");
        String message = "Policy v2 " + operation + " "
                + (publication.changed() ? "published" : "validated without changes")
                + ": mode=" + publication.mode().name().toLowerCase(java.util.Locale.ROOT)
                + ", active-version=" + version
                + ", retained-versions=" + publication.retainedVersions();
        return new ReloadResult(
                outcome,
                publication.mode(),
                publication.activeVersion(),
                publication.retainedVersions(),
                message,
                List.of()
        );
    }

    private static String diagnostic(RuntimeException exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= MAX_DIAGNOSTIC_LENGTH
                ? value
                : value.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
    }

    public enum Outcome {
        PUBLISHED,
        UNCHANGED,
        REJECTED
    }

    public record ReloadResult(
            Outcome outcome,
            PolicyV2FeatureMode mode,
            Optional<String> activeVersion,
            int retainedVersions,
            String message,
            List<String> details
    ) {
        public ReloadResult {
            activeVersion = activeVersion == null ? Optional.empty() : activeVersion;
            details = List.copyOf(details == null ? List.of() : details);
        }

        public boolean successful() {
            return outcome != Outcome.REJECTED;
        }
    }
}
