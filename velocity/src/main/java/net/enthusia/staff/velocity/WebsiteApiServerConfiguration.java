package net.enthusia.staff.velocity;

import java.net.InetAddress;

record WebsiteApiServerConfiguration(
        InetAddress bindAddress,
        int port,
        int maximumBodyBytes,
        int workerThreads,
        int queueCapacity
) {
    WebsiteApiServerConfiguration {
        if (bindAddress == null || !bindAddress.isLoopbackAddress()
                || port < 1 || port > 65_535
                || maximumBodyBytes < 1_024
                || workerThreads < 1
                || queueCapacity < 8) {
            throw new IllegalArgumentException("Website API server configuration is invalid");
        }
    }
}
