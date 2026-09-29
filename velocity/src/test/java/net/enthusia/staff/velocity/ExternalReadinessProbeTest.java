package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class ExternalReadinessProbeTest {
    @Test
    void missingEndpointIsReportedAsDisabled() {
        ExternalReadinessProbe.Result result = ExternalReadinessProbe.probe(null, 500);

        assertEquals(ExternalReadinessProbe.State.DISABLED, result.state());
    }

    @Test
    void successfulEndpointPasses() throws Exception {
        try (ServerFixture fixture = new ServerFixture(204)) {
            ExternalReadinessProbe.Result result = ExternalReadinessProbe.probe(fixture.endpoint(), 1_000);

            assertEquals(ExternalReadinessProbe.State.PASS, result.state());
        }
    }

    @Test
    void nonSuccessEndpointWarns() throws Exception {
        try (ServerFixture fixture = new ServerFixture(503)) {
            ExternalReadinessProbe.Result result = ExternalReadinessProbe.probe(fixture.endpoint(), 1_000);

            assertEquals(ExternalReadinessProbe.State.WARNING, result.state());
        }
    }

    @Test
    void unreachableEndpointWarnsWithoutLeakingAddress() {
        PrivateReadinessEndpoint endpoint = PrivateReadinessEndpoint.parseOptional(
                "http://127.0.0.1:1/ready"
        ).orElseThrow();
        ExternalReadinessProbe.Result result = ExternalReadinessProbe.probe(endpoint, 250);

        assertEquals(ExternalReadinessProbe.State.WARNING, result.state());
        assertEquals("readiness endpoint is unreachable", result.detail());
    }

    private static final class ServerFixture implements AutoCloseable {
        private final HttpServer server;

        private ServerFixture(int status) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/ready", exchange -> {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
        }

        private PrivateReadinessEndpoint endpoint() {
            return PrivateReadinessEndpoint.parseOptional(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/ready"
            ).orElseThrow();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
