package net.enthusia.staff.discordbot;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;

/** Resolves a Bloom split hostname to a private IPv4 address before the authority request is sent. */
final class PrivateSplitAuthorityEndpointResolver {
    URI resolve(URI endpoint) {
        InetAddress[] addresses = addresses(endpoint.getHost());
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !privateAddress(address))) {
            throw new StaffAuthorityClient.UnavailableException(
                    "staff authority private split hostname did not resolve exclusively to private addresses");
        }
        Inet4Address selected = Arrays.stream(addresses)
                .filter(Inet4Address.class::isInstance)
                .map(Inet4Address.class::cast)
                .findFirst()
                .orElseThrow(() -> new StaffAuthorityClient.UnavailableException(
                        "staff authority private split hostname has no private IPv4 address"));
        return numericEndpoint(endpoint, selected.getHostAddress());
    }

    private static InetAddress[] addresses(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new StaffAuthorityClient.UnavailableException(
                    "staff authority private split hostname could not be resolved", exception);
        }
    }

    static boolean privateAddress(InetAddress address) {
        return address != null && (address.isLoopbackAddress() || address.isSiteLocalAddress());
    }

    private static URI numericEndpoint(URI endpoint, String host) {
        try {
            return new URI(
                    "http",
                    null,
                    host,
                    endpoint.getPort(),
                    endpoint.getPath(),
                    null,
                    null
            );
        } catch (URISyntaxException exception) {
            throw new StaffAuthorityClient.UnavailableException(
                    "staff authority private split endpoint could not be constructed", exception);
        }
    }
}
