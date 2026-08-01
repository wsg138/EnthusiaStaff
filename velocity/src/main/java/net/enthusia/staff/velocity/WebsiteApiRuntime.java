package net.enthusia.staff.velocity;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class WebsiteApiRuntime implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor executor;

    private WebsiteApiRuntime(HttpServer server, ThreadPoolExecutor executor) {
        this.server = server;
        this.executor = executor;
    }

    @SuppressWarnings("PMD.CloseResource") // Success transfers both resources; every failure closes them here.
    static WebsiteApiRuntime start(
            WebsiteApiServerConfiguration configuration,
            HttpHandler handler
    ) throws IOException {
        ThreadPoolExecutor executor = executor(configuration);
        HttpServer server = null;
        try {
            server = HttpServer.create(
                    new InetSocketAddress(configuration.bindAddress(), configuration.port()),
                    configuration.queueCapacity()
            );
            server.createContext("/", handler);
            server.setExecutor(executor);
            server.start();
            return new WebsiteApiRuntime(server, executor);
        } catch (IOException | RuntimeException exception) {
            if (server != null) {
                server.stop(0);
            }
            executor.shutdownNow();
            throw exception;
        }
    }

    private static ThreadPoolExecutor executor(WebsiteApiServerConfiguration configuration) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "EnthusiaStaff-Website-API-" + sequence.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                configuration.workerThreads(),
                configuration.workerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(configuration.queueCapacity()),
                threads,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
