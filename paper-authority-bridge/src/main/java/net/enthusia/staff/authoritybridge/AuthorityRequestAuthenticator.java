package net.enthusia.staff.authoritybridge;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import net.enthusia.staff.protocol.ReplayGuard;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Private-peer, signed and replay-resistant authentication for the acceptance-only bridge. */
final class AuthorityRequestAuthenticator {
    private static final String REPLAY_SCOPE = "es-d16-owner-authority-bridge";
    private static final int REPLAY_CAPACITY = 4_096;
    private static final Duration REPLAY_RETENTION = Duration.ofMinutes(2);

    private final String keyMaterial;
    private final Clock clock;
    private final ReplayGuard replayGuard;

    AuthorityRequestAuthenticator(String keyMaterial) {
        this(keyMaterial, Clock.systemUTC(), new ReplayGuard(REPLAY_CAPACITY, REPLAY_RETENTION));
    }

    AuthorityRequestAuthenticator(String keyMaterial, Clock clock, ReplayGuard replayGuard) {
        if (keyMaterial == null || keyMaterial.isBlank() || clock == null || replayGuard == null) {
            throw new IllegalArgumentException("authority authenticator configuration is incomplete");
        }
        this.keyMaterial = keyMaterial;
        this.clock = clock;
        this.replayGuard = replayGuard;
    }

    Result authenticate(
            InetAddress remoteAddress,
            String method,
            String target,
            String timestamp,
            String nonce,
            String signature
    ) {
        if (!privatePeer(remoteAddress)) {
            return Result.rejected();
        }
        StaffAuthorityHttpSigning.Verification verification = StaffAuthorityHttpSigning.verifyRequest(
                keyMaterial,
                method,
                target,
                timestamp,
                nonce,
                signature,
                clock
        );
        if (verification != StaffAuthorityHttpSigning.Verification.ACCEPTED) {
            return Result.rejected();
        }
        return replayGuard.recordIfNew(REPLAY_SCOPE, nonce, clock.instant())
                ? Result.accepted(nonce)
                : Result.rejected();
    }

    String signResponse(Result result, int status, String body) {
        if (result == null || !result.accepted() || result.nonce() == null) {
            return null;
        }
        return StaffAuthorityHttpSigning.signResponse(keyMaterial, result.nonce(), status, body);
    }

    static boolean privatePeer(InetAddress address) {
        return address != null && (address.isLoopbackAddress() || address.isSiteLocalAddress());
    }

    record Result(boolean accepted, String nonce) {
        static Result rejected() {
            return new Result(false, null);
        }

        static Result accepted(String nonce) {
            return new Result(true, nonce);
        }
    }
}
