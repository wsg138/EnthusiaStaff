package net.enthusia.staff.paper.auth;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import net.enthusia.staff.protocol.ReplayGuard;
import net.enthusia.staff.protocol.StaffAuthorityHttpSigning;

/** Authenticates loopback bearer requests and Bloom-private signed requests without mixing the modes. */
final class DiscordStaffAuthorityAuthenticator {
    private static final String REPLAY_SCOPE = "discord-staff-authority";
    private static final int REPLAY_CAPACITY = 4_096;
    private static final Duration REPLAY_RETENTION = Duration.ofMinutes(2);

    private final String bearer;
    private final String credential;
    private final boolean privateSplit;
    private final Clock clock;
    private final ReplayGuard replayGuard;

    DiscordStaffAuthorityAuthenticator(String credential, boolean privateSplit) {
        this(
                credential,
                privateSplit,
                Clock.systemUTC(),
                new ReplayGuard(REPLAY_CAPACITY, REPLAY_RETENTION)
        );
    }

    DiscordStaffAuthorityAuthenticator(
            String credential,
            boolean privateSplit,
            Clock clock,
            ReplayGuard replayGuard
    ) {
        if (credential == null || credential.isBlank() || clock == null || replayGuard == null) {
            throw new IllegalArgumentException("authority authenticator configuration is incomplete");
        }
        this.credential = credential;
        this.bearer = "Bearer " + credential;
        this.privateSplit = privateSplit;
        this.clock = clock;
        this.replayGuard = replayGuard;
    }

    Result authenticate(
            InetAddress remoteAddress,
            String method,
            String target,
            String authorization,
            String timestamp,
            String nonce,
            String signature
    ) {
        if (privateSplit) {
            return authenticatePrivate(remoteAddress, method, target, timestamp, nonce, signature);
        }
        return authenticateLoopback(remoteAddress, authorization);
    }

    String responseSignature(Result result, int status, String body) {
        if (!result.accepted() || result.nonce() == null) {
            return null;
        }
        return StaffAuthorityHttpSigning.signResponse(credential, result.nonce(), status, body);
    }

    private Result authenticateLoopback(InetAddress remoteAddress, String supplied) {
        if (remoteAddress == null || !remoteAddress.isLoopbackAddress() || supplied == null) {
            return Result.rejected();
        }
        boolean accepted = MessageDigest.isEqual(
                bearer.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
        return accepted ? Result.loopback() : Result.rejected();
    }

    private Result authenticatePrivate(
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
                credential, method, target, timestamp, nonce, signature, clock);
        if (verification != StaffAuthorityHttpSigning.Verification.ACCEPTED) {
            return Result.rejected();
        }
        return replayGuard.recordIfNew(REPLAY_SCOPE, nonce, clock.instant())
                ? Result.privateSplit(nonce)
                : Result.rejected();
    }

    static boolean privatePeer(InetAddress address) {
        return address != null && (address.isLoopbackAddress() || address.isSiteLocalAddress());
    }

    record Result(boolean accepted, String nonce) {
        private static Result rejected() {
            return new Result(false, null);
        }

        private static Result loopback() {
            return new Result(true, null);
        }

        private static Result privateSplit(String nonce) {
            return new Result(true, nonce);
        }
    }
}
