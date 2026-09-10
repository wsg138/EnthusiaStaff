package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class PrivateSplitAuthorityEndpointResolverTest {
    @Test
    void privateAndLoopbackAddressesAreAccepted() throws Exception {
        assertTrue(PrivateSplitAuthorityEndpointResolver.privateAddress(
                InetAddress.getByName("127.0.0.1")));
        assertTrue(PrivateSplitAuthorityEndpointResolver.privateAddress(
                InetAddress.getByName("10.0.0.2")));
        assertTrue(PrivateSplitAuthorityEndpointResolver.privateAddress(
                InetAddress.getByName("172.18.0.2")));
        assertTrue(PrivateSplitAuthorityEndpointResolver.privateAddress(
                InetAddress.getByName("192.168.1.2")));
    }

    @Test
    void numericPrivateEndpointIsPinnedWithoutChangingResource() {
        URI endpoint = URI.create("http://127.0.0.1:8771/v1/staff-rank");

        URI resolved = new PrivateSplitAuthorityEndpointResolver().resolve(endpoint);

        assertEquals(endpoint, resolved);
    }

    @Test
    void publicAddressIsRejected() {
        URI endpoint = URI.create("http://8.8.8.8:8771/v1/staff-rank");

        assertThrows(
                StaffAuthorityClient.UnavailableException.class,
                () -> new PrivateSplitAuthorityEndpointResolver().resolve(endpoint));
    }
}
