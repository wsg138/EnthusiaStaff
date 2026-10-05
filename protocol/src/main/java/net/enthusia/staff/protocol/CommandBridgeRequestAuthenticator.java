package net.enthusia.staff.protocol;

import java.time.Clock;
import java.time.Duration;

/** Verifies body-bound request signatures and consumes nonces exactly once. */
public final class CommandBridgeRequestAuthenticator {
    private static final int DEFAULT_REPLAY_CAPACITY = 4_096;
    private static final Duration DEFAULT_REPLAY_RETENTION = Duration.ofMinutes(2);

    private final String credential;
    private final String replayScope;
    private final Clock clock;
    private final ReplayGuard replayGuard;

    public CommandBridgeRequestAuthenticator(String credential, String replayScope) {
        this(
                credential,
                replayScope,
                Clock.systemUTC(),
                new ReplayGuard(DEFAULT_REPLAY_CAPACITY, DEFAULT_REPLAY_RETENTION)
        );
    }

    CommandBridgeRequestAuthenticator(
            String credential,
            String replayScope,
            Clock clock,
            ReplayGuard replayGuard
    ) {
        if (credential == null || credential.isBlank() || replayScope == null || replayScope.isBlank()
                || clock == null || replayGuard == null) {
            throw new IllegalArgumentException("command bridge authenticator configuration is incomplete");
        }
        this.credential = credential;
        this.replayScope = replayScope;
        this.clock = clock;
        this.replayGuard = replayGuard;
    }

    public Result authenticate(
            String method,
            String target,
            String body,
            String timestamp,
            String nonce,
            String signature
    ) {
        CommandBridgeHttpSigning.Verification verification = CommandBridgeHttpSigning.verifyRequest(
                credential,
                method,
                target,
                body,
                timestamp,
                nonce,
                signature,
                clock
        );
        if (verification != CommandBridgeHttpSigning.Verification.ACCEPTED) {
            return map(verification);
        }
        return replayGuard.recordIfNew(replayScope, nonce, clock.instant()) ? Result.ACCEPTED : Result.REPLAYED;
    }

    public String signResponse(String nonce, int status, String body) {
        return CommandBridgeHttpSigning.signResponse(credential, nonce, status, body);
    }

    private static Result map(CommandBridgeHttpSigning.Verification verification) {
        return switch (verification) {
            case MALFORMED -> Result.MALFORMED;
            case EXPIRED -> Result.EXPIRED;
            case INVALID_SIGNATURE -> Result.INVALID_SIGNATURE;
            case ACCEPTED -> throw new IllegalStateException("accepted verification must consume the nonce");
        };
    }

    public enum Result {
        ACCEPTED,
        MALFORMED,
        EXPIRED,
        INVALID_SIGNATURE,
        REPLAYED
    }
}
