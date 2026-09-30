package net.enthusia.staff.domain.commandbridge;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import net.enthusia.staff.domain.commandbridge.CommandBridgePolicy.Decision;
import net.enthusia.staff.domain.ports.CommandBridgeAuditStore;
import net.enthusia.staff.domain.ports.CommandBridgeAuditStore.Claim;
import net.enthusia.staff.domain.ports.CommandBridgeAuthorityResolver;
import net.enthusia.staff.domain.ports.CommandBridgeExecutor;
import net.enthusia.staff.domain.ports.CommandBridgeLinkVerifier;

/** Minecraft-side orchestration. Authority is re-read immediately before the single execution attempt. */
public final class CommandBridgeService {
    private final CommandBridgePolicy policy;
    private final CommandBridgeLinkVerifier links;
    private final CommandBridgeAuthorityResolver authority;
    private final CommandBridgeAuditStore audit;
    private final CommandBridgeExecutor executor;
    private final CommandBridgeOutputSanitizer sanitizer;
    private final Clock clock;

    public CommandBridgeService(
            CommandBridgePolicy policy,
            CommandBridgeLinkVerifier links,
            CommandBridgeAuthorityResolver authority,
            CommandBridgeAuditStore audit,
            CommandBridgeExecutor executor,
            CommandBridgeOutputSanitizer sanitizer,
            Clock clock
    ) {
        this.policy = require(policy, "policy");
        this.links = require(links, "links");
        this.authority = require(authority, "authority");
        this.audit = require(audit, "audit");
        this.executor = require(executor, "executor");
        this.sanitizer = require(sanitizer, "sanitizer");
        this.clock = require(clock, "clock");
    }

    public CommandBridgeResponse handle(CommandBridgeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("command bridge request is required");
        }
        Decision decision = policy.evaluate(request);
        Claim claim;
        try {
            claim = audit.claim(auditRequest(request, decision));
        } catch (RuntimeException failure) {
            return internalError();
        }
        CommandBridgeResponse repeated = repeated(claim);
        return repeated == null ? processClaimed(request, decision) : repeated;
    }

    private CommandBridgeResponse processClaimed(CommandBridgeRequest request, Decision decision) {
        if (!decision.accepted()) {
            return finish(request, rejection(decision), false);
        }
        try {
            if (!links.isCurrentLink(request.subjectId(), request.discordUserId(), request.actorPlayerId())) {
                return finish(request, response(
                        CommandBridgeOutcome.UNLINKED_ACTOR,
                        "Linked actor is no longer valid."
                ), false);
            }
            Optional<CommandBridgeAuthorityResolver.Snapshot> current = authority.current(
                    request.actorPlayerId(),
                    decision.rule().requiredPermission()
            );
            if (current.isEmpty() || !authorized(current.orElseThrow(), decision.rule())) {
                return finish(request, response(
                        CommandBridgeOutcome.UNAUTHORIZED_ACTOR,
                        "Current Minecraft authority rejected the request."
                ), false);
            }
        } catch (RuntimeException failure) {
            return finish(request, internalError(), false);
        }
        return execute(request, decision.normalizedCommand());
    }

    private CommandBridgeResponse execute(CommandBridgeRequest request, String normalizedCommand) {
        CommandBridgeExecutor.Execution execution;
        try {
            execution = executor.execute(normalizedCommand);
        } catch (RuntimeException failure) {
            return finish(request, response(
                    CommandBridgeOutcome.EXECUTION_UNKNOWN,
                    "Execution outcome is unknown; the command was not retried."
            ), true);
        }
        if (!execution.accepted()) {
            return finish(request, response(
                    CommandBridgeOutcome.EXECUTION_REJECTED,
                    "Minecraft rejected command execution."
            ), true);
        }
        CommandBridgeOutputSanitizer.Sanitized output = sanitizer.sanitize(execution.output());
        return finish(request, new CommandBridgeResponse(
                CommandBridgeOutcome.SUCCESS,
                "Command executed.",
                output.text(),
                output.truncated(),
                output.redacted()
        ), true);
    }

    private CommandBridgeResponse finish(
            CommandBridgeRequest request,
            CommandBridgeResponse response,
            boolean executionAttempted
    ) {
        try {
            audit.complete(request.requestId(), response.outcome(), clock.instant());
            return response;
        } catch (RuntimeException failure) {
            return executionAttempted
                    ? response(CommandBridgeOutcome.EXECUTION_UNKNOWN,
                            "Execution may have occurred but durable completion could not be confirmed; not safe to retry.")
                    : internalError();
        }
    }

    private static boolean authorized(
            CommandBridgeAuthorityResolver.Snapshot current,
            CommandBridgeRule rule
    ) {
        return current.permissionGranted() && current.rank().atLeast(rule.minimumRank());
    }

    private static CommandBridgeResponse rejection(Decision decision) {
        return switch (decision.status()) {
            case MALFORMED_COMMAND -> response(CommandBridgeOutcome.MALFORMED_COMMAND, "Command format is invalid.");
            case UNSUPPORTED_COMMAND -> response(CommandBridgeOutcome.UNSUPPORTED_COMMAND, "Command is not allowlisted.");
            case COMMAND_POLICY_REJECTED -> response(
                    CommandBridgeOutcome.COMMAND_POLICY_REJECTED,
                    "Command arguments exceed policy."
            );
            case INVALID_SERVER -> response(CommandBridgeOutcome.INVALID_SERVER, "Target server is not allowlisted.");
            case ACCEPTED -> throw new IllegalStateException("accepted request is not a rejection");
        };
    }

    private static CommandBridgeResponse repeated(Claim claim) {
        return switch (claim.status()) {
            case CLAIMED -> null;
            case TERMINAL_REPLAY -> response(CommandBridgeOutcome.DUPLICATE_REQUEST,
                    "Request was already completed and was not re-executed.");
            case UNRESOLVED_PRIOR_REQUEST -> response(CommandBridgeOutcome.EXECUTION_UNKNOWN,
                    "A prior request with this ID is unresolved and was not re-executed.");
            case REQUEST_ID_CONFLICT -> response(CommandBridgeOutcome.REQUEST_ID_CONFLICT,
                    "Request ID was already used for different content.");
        };
    }

    private CommandBridgeAuditStore.RequestAudit auditRequest(
            CommandBridgeRequest request,
            Decision decision
    ) {
        return new CommandBridgeAuditStore.RequestAudit(
                request.requestId(),
                request.subjectId(),
                request.discordUserId(),
                request.actorPlayerId(),
                request.targetServer(),
                decision.commandName(),
                fingerprint(request),
                clock.instant()
        );
    }

    private static String fingerprint(CommandBridgeRequest request) {
        String canonical = request.requestId() + "\n" + request.subjectId() + "\n" + request.discordUserId()
                + "\n" + request.actorPlayerId() + "\n" + request.targetServer()
                + "\n" + request.command() + "\n" + request.requestedAt();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static CommandBridgeResponse response(CommandBridgeOutcome outcome, String message) {
        return CommandBridgeResponse.withoutOutput(outcome, message);
    }

    private static CommandBridgeResponse internalError() {
        return response(CommandBridgeOutcome.INTERNAL_ERROR, "Command bridge request could not be safely processed.");
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
