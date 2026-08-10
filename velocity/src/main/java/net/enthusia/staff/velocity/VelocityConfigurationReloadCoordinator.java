package net.enthusia.staff.velocity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class VelocityConfigurationReloadCoordinator {
    private final CandidateLoader candidates;
    private final Consumer<VelocityConfiguration> publisher;
    private final BooleanSupplier stopping;
    private final AtomicReference<VelocityConfiguration> active;

    VelocityConfigurationReloadCoordinator(
            VelocityConfiguration initial,
            CandidateLoader candidates,
            Consumer<VelocityConfiguration> publisher,
            BooleanSupplier stopping
    ) {
        this.active = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.stopping = Objects.requireNonNull(stopping, "stopping");
    }

    VelocityConfiguration active() {
        return active.get();
    }

    VelocityConfigurationReloadResult reload() {
        synchronized (this) {
            return reloadLocked();
        }
    }

    private VelocityConfigurationReloadResult reloadLocked() {
        if (stopping.getAsBoolean()) {
            return shuttingDown("Velocity configuration reload was rejected because shutdown has started");
        }
        final VelocityConfiguration candidate;
        try {
            candidate = candidates.load();
        } catch (IOException | RuntimeException exception) {
            return validationFailed(exception);
        }
        return applyCandidate(candidate);
    }

    private VelocityConfigurationReloadResult applyCandidate(VelocityConfiguration candidate) {
        VelocityConfiguration current = active.get();
        List<String> restartRequired = restartRequiredChanges(current, candidate);
        if (!restartRequired.isEmpty()) {
            return result(
                    VelocityConfigurationReloadResult.Outcome.RESTART_REQUIRED,
                    "Velocity configuration candidate changes resource-bound settings and was not applied",
                    restartRequired
            );
        }
        if (candidate.equals(current)) {
            return result(
                    VelocityConfigurationReloadResult.Outcome.NO_CHANGES,
                    "Velocity configuration is unchanged",
                    List.of()
            );
        }
        if (stopping.getAsBoolean()) {
            return shuttingDown("Velocity configuration reload was rejected because shutdown started before publication");
        }
        try {
            publisher.accept(candidate);
            active.set(candidate);
            return result(
                    VelocityConfigurationReloadResult.Outcome.APPLIED,
                    "Velocity fail-closed and appeal URL settings were reloaded atomically",
                    List.of()
            );
        } catch (RuntimeException publicationFailure) {
            return restorePreviousConfiguration(current, publicationFailure);
        }
    }

    private VelocityConfigurationReloadResult restorePreviousConfiguration(
            VelocityConfiguration current,
            RuntimeException publicationFailure
    ) {
        RuntimeException rollbackFailure = null;
        try {
            publisher.accept(current);
        } catch (RuntimeException exception) {
            rollbackFailure = exception;
        }
        List<String> details = new ArrayList<>();
        details.add("Candidate publication failed: " + publicationFailure.getClass().getSimpleName());
        if (rollbackFailure != null) {
            details.add("Previous configuration restoration failed: "
                    + rollbackFailure.getClass().getSimpleName());
        }
        return result(
                VelocityConfigurationReloadResult.Outcome.UNAVAILABLE,
                rollbackFailure == null
                        ? "Velocity configuration publication failed; the previous configuration was restored"
                        : "Velocity configuration publication and restoration both failed",
                details
        );
    }

    private static VelocityConfigurationReloadResult validationFailed(Exception exception) {
        return result(
                VelocityConfigurationReloadResult.Outcome.VALIDATION_FAILED,
                "Velocity configuration candidate was rejected; the live configuration is unchanged",
                List.of(sanitized(exception))
        );
    }

    private static VelocityConfigurationReloadResult shuttingDown(String message) {
        return result(VelocityConfigurationReloadResult.Outcome.SHUTTING_DOWN, message, List.of());
    }

    private static List<String> restartRequiredChanges(
            VelocityConfiguration current,
            VelocityConfiguration candidate
    ) {
        List<String> changes = new ArrayList<>();
        storageAndWebsiteChanges(changes, current, candidate);
        channelAndIdentityChanges(changes, current, candidate);
        discordAndLiteBansChanges(changes, current, candidate);
        return List.copyOf(changes);
    }

    private static void storageAndWebsiteChanges(
            List<String> changes,
            VelocityConfiguration current,
            VelocityConfiguration candidate
    ) {
        changed(changes, "storage.jdbc-url-environment", current.jdbcUrlEnvironment(), candidate.jdbcUrlEnvironment());
        changed(changes, "storage.username-environment", current.usernameEnvironment(), candidate.usernameEnvironment());
        changed(changes, "storage.password-environment", current.passwordEnvironment(), candidate.passwordEnvironment());
        changed(changes, "storage.maximum-pool-size", current.maximumPoolSize(), candidate.maximumPoolSize());
        changed(changes, "storage.connection-timeout-millis",
                current.connectionTimeoutMillis(), candidate.connectionTimeoutMillis());
        changed(changes, "server.id", current.serverId(), candidate.serverId());
        changed(changes, "website-api.enabled", current.websiteApiEnabled(), candidate.websiteApiEnabled());
        changed(changes, "website-api.bind-address",
                current.websiteApiBindAddress(), candidate.websiteApiBindAddress());
        changed(changes, "website-api.port", current.websiteApiPort(), candidate.websiteApiPort());
        changed(changes, "website-api.bearer-token-environment",
                current.websiteApiBearerTokenEnvironment(), candidate.websiteApiBearerTokenEnvironment());
        changed(changes, "website-api.hmac-secret-environment",
                current.websiteApiHmacSecretEnvironment(), candidate.websiteApiHmacSecretEnvironment());
        changed(changes, "website-api.code-key-version",
                current.punishmentCodeKeyVersion(), candidate.punishmentCodeKeyVersion());
        changed(changes, "website-api.code-secret-environment",
                current.punishmentCodeSecretEnvironment(), candidate.punishmentCodeSecretEnvironment());
        changed(changes, "website-api.timestamp-skew-seconds",
                current.websiteApiTimestampSkewSeconds(), candidate.websiteApiTimestampSkewSeconds());
        changed(changes, "website-api.maximum-body-bytes",
                current.websiteApiMaximumBodyBytes(), candidate.websiteApiMaximumBodyBytes());
        changed(changes, "website-api.worker-threads",
                current.websiteApiWorkerThreads(), candidate.websiteApiWorkerThreads());
        changed(changes, "website-api.queue-capacity",
                current.websiteApiQueueCapacity(), candidate.websiteApiQueueCapacity());
    }

    private static void channelAndIdentityChanges(
            List<String> changes,
            VelocityConfiguration current,
            VelocityConfiguration candidate
    ) {
        changed(changes, "channel.enabled", current.channelEnabled(), candidate.channelEnabled());
        changed(changes, "channel.bind-address", current.channelBindAddress(), candidate.channelBindAddress());
        changed(changes, "channel.port", current.channelPort(), candidate.channelPort());
        changed(changes, "channel.proxy-id", current.channelProxyId(), candidate.channelProxyId());
        changed(changes, "channel.proxy-secret-environment",
                current.channelProxySecretEnvironment(), candidate.channelProxySecretEnvironment());
        changed(changes, "channel.tls-key-store", current.channelTlsKeyStorePath(), candidate.channelTlsKeyStorePath());
        changed(changes, "channel.tls-key-store-password-environment",
                current.channelTlsKeyStorePasswordEnvironment(), candidate.channelTlsKeyStorePasswordEnvironment());
        changed(changes, "channel.backend.*.secret-environment",
                current.backendSecretEnvironments(), candidate.backendSecretEnvironments());
        changed(changes, "network-identity.enabled",
                current.networkIdentityEnabled(), candidate.networkIdentityEnabled());
        changed(changes, "network-identity.hmac-key-version",
                current.networkIdentityHmacKeyVersion(), candidate.networkIdentityHmacKeyVersion());
        changed(changes, "network-identity.hmac-secret-environment",
                current.networkIdentityHmacSecretEnvironment(), candidate.networkIdentityHmacSecretEnvironment());
        changed(changes, "network-identity.encryption-key-version",
                current.networkIdentityEncryptionKeyVersion(), candidate.networkIdentityEncryptionKeyVersion());
        changed(changes, "network-identity.encryption-secret-environment",
                current.networkIdentityEncryptionSecretEnvironment(), candidate.networkIdentityEncryptionSecretEnvironment());
    }

    private static void discordAndLiteBansChanges(
            List<String> changes,
            VelocityConfiguration current,
            VelocityConfiguration candidate
    ) {
        changed(changes, "discord.enabled", current.discordEnabled(), candidate.discordEnabled());
        changed(changes, "discord.*.webhook-environment",
                current.discordWebhookEnvironments(), candidate.discordWebhookEnvironments());
        changed(changes, "discord.maximum-attempts", current.discordMaximumAttempts(), candidate.discordMaximumAttempts());
        changed(changes, "discord.failure-threshold", current.discordFailureThreshold(), candidate.discordFailureThreshold());
        changed(changes, "discord.circuit-open-seconds",
                current.discordCircuitOpenSeconds(), candidate.discordCircuitOpenSeconds());
        changed(changes, "discord.request-timeout-millis",
                current.discordRequestTimeoutMillis(), candidate.discordRequestTimeoutMillis());
        changed(changes, "litebans.jdbc-url-environment",
                current.liteBansJdbcUrlEnvironment(), candidate.liteBansJdbcUrlEnvironment());
        changed(changes, "litebans.username-environment",
                current.liteBansUsernameEnvironment(), candidate.liteBansUsernameEnvironment());
        changed(changes, "litebans.password-environment",
                current.liteBansPasswordEnvironment(), candidate.liteBansPasswordEnvironment());
        changed(changes, "litebans.maximum-pool-size",
                current.liteBansMaximumPoolSize(), candidate.liteBansMaximumPoolSize());
        changed(changes, "litebans.connection-timeout-millis",
                current.liteBansConnectionTimeoutMillis(), candidate.liteBansConnectionTimeoutMillis());
        changed(changes, "litebans.table-prefix", current.liteBansTablePrefix(), candidate.liteBansTablePrefix());
        changed(changes, "litebans.batch-size", current.liteBansBatchSize(), candidate.liteBansBatchSize());
        changed(changes, "litebans.shadow-schedule-enabled",
                current.liteBansShadowScheduleEnabled(), candidate.liteBansShadowScheduleEnabled());
        changed(changes, "litebans.shadow-interval-hours",
                current.liteBansShadowIntervalHours(), candidate.liteBansShadowIntervalHours());
    }

    private static void changed(List<String> changes, String key, Object current, Object candidate) {
        if (!Objects.equals(current, candidate)) {
            changes.add(key + " changed and requires a proxy restart");
        }
    }

    private static String sanitized(Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return "Configuration validation failed: " + exception.getClass().getSimpleName();
        }
        if (exception instanceof IOException) {
            return "Configuration file could not be read";
        }
        return "Configuration candidate could not be loaded: " + exception.getClass().getSimpleName();
    }

    private static VelocityConfigurationReloadResult result(
            VelocityConfigurationReloadResult.Outcome outcome,
            String message,
            List<String> details
    ) {
        return new VelocityConfigurationReloadResult(outcome, message, details);
    }

    @FunctionalInterface
    interface CandidateLoader {
        VelocityConfiguration load() throws IOException;
    }
}
