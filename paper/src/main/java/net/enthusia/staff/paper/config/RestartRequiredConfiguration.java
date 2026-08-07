package net.enthusia.staff.paper.config;

import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.paper.tester.CheatTesterSettings;

public record RestartRequiredConfiguration(
        String storageJdbcUrlEnvironment,
        String storageUsernameEnvironment,
        String storagePasswordEnvironment,
        int storageMaximumPoolSize,
        long storageConnectionTimeoutMillis,
        int workerThreads,
        int workerQueueCapacity,
        String networkServerId,
        String inventoryScopeId,
        boolean channelEnabled,
        String channelHost,
        int channelPort,
        String channelProxyId,
        String channelBackendSecretEnvironment,
        String channelProxySecretEnvironment,
        String channelTrustStore,
        String channelTrustStorePasswordEnvironment,
        CheatTesterSettings cheatTesterSettings
) {
    public RestartRequiredConfiguration {
        if (cheatTesterSettings == null) {
            cheatTesterSettings = CheatTesterSettings.defaults();
        }
    }

    /** Compatibility constructor for existing test/runtime fixtures that predate ES-P10 settings. */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public RestartRequiredConfiguration(
            String storageJdbcUrlEnvironment,
            String storageUsernameEnvironment,
            String storagePasswordEnvironment,
            int storageMaximumPoolSize,
            long storageConnectionTimeoutMillis,
            int workerThreads,
            int workerQueueCapacity,
            String networkServerId,
            String inventoryScopeId,
            boolean channelEnabled,
            String channelHost,
            int channelPort,
            String channelProxyId,
            String channelBackendSecretEnvironment,
            String channelProxySecretEnvironment,
            String channelTrustStore,
            String channelTrustStorePasswordEnvironment
    ) {
        this(
                storageJdbcUrlEnvironment,
                storageUsernameEnvironment,
                storagePasswordEnvironment,
                storageMaximumPoolSize,
                storageConnectionTimeoutMillis,
                workerThreads,
                workerQueueCapacity,
                networkServerId,
                inventoryScopeId,
                channelEnabled,
                channelHost,
                channelPort,
                channelProxyId,
                channelBackendSecretEnvironment,
                channelProxySecretEnvironment,
                channelTrustStore,
                channelTrustStorePasswordEnvironment,
                CheatTesterSettings.defaults()
        );
    }

    public List<String> differencePaths(RestartRequiredConfiguration candidate) {
        if (candidate == null) {
            return List.of("configuration");
        }
        List<String> differences = new ArrayList<>();
        compare(differences, "storage.jdbc-url-environment", storageJdbcUrlEnvironment, candidate.storageJdbcUrlEnvironment);
        compare(differences, "storage.username-environment", storageUsernameEnvironment, candidate.storageUsernameEnvironment);
        compare(differences, "storage.password-environment", storagePasswordEnvironment, candidate.storagePasswordEnvironment);
        compare(differences, "storage.maximum-pool-size", storageMaximumPoolSize, candidate.storageMaximumPoolSize);
        compare(differences, "storage.connection-timeout-millis", storageConnectionTimeoutMillis, candidate.storageConnectionTimeoutMillis);
        compare(differences, "workers.threads", workerThreads, candidate.workerThreads);
        compare(differences, "workers.queue-capacity", workerQueueCapacity, candidate.workerQueueCapacity);
        compare(differences, "network.server-id", networkServerId, candidate.networkServerId);
        compare(differences, "inventory.scope-id", inventoryScopeId, candidate.inventoryScopeId);
        compare(differences, "channel.enabled", channelEnabled, candidate.channelEnabled);
        compare(differences, "channel.host", channelHost, candidate.channelHost);
        compare(differences, "channel.port", channelPort, candidate.channelPort);
        compare(differences, "channel.proxy-id", channelProxyId, candidate.channelProxyId);
        compare(differences, "channel.backend-secret-environment", channelBackendSecretEnvironment, candidate.channelBackendSecretEnvironment);
        compare(differences, "channel.proxy-secret-environment", channelProxySecretEnvironment, candidate.channelProxySecretEnvironment);
        compare(differences, "channel.tls.trust-store", channelTrustStore, candidate.channelTrustStore);
        compare(differences, "channel.tls.trust-store-password-environment", channelTrustStorePasswordEnvironment, candidate.channelTrustStorePasswordEnvironment);
        compare(differences, "staff-tools.cheat-tester", cheatTesterSettings, candidate.cheatTesterSettings);
        return List.copyOf(differences);
    }

    private static void compare(List<String> differences, String path, Object active, Object candidate) {
        if (!java.util.Objects.equals(active, candidate)) {
            differences.add(path);
        }
    }
}
