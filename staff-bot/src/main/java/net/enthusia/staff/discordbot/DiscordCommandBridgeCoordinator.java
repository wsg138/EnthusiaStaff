package net.enthusia.staff.discordbot;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.enthusia.staff.domain.commandbridge.CommandBridgeRequest;
import net.enthusia.staff.domain.commandbridge.CommandBridgeResponse;
import net.enthusia.staff.domain.moderation.DiscordUserId;

/** Resolves the Discord actor through canonical linking before creating the signed Minecraft request. */
public final class DiscordCommandBridgeCoordinator {
    private final DiscordCommandActorResolver actors;
    private final Transport transport;
    private final Clock clock;
    private final Supplier<UUID> requestIds;

    public DiscordCommandBridgeCoordinator(
            DiscordCommandActorResolver actors,
            HttpMinecraftCommandBridgeClient client
    ) {
        this(actors, client::dispatch, Clock.systemUTC(), UUID::randomUUID);
    }

    DiscordCommandBridgeCoordinator(
            DiscordCommandActorResolver actors,
            Transport transport,
            Clock clock,
            Supplier<UUID> requestIds
    ) {
        if (actors == null || transport == null || clock == null || requestIds == null) {
            throw new IllegalArgumentException("command bridge coordinator configuration is incomplete");
        }
        this.actors = actors;
        this.transport = transport;
        this.clock = clock;
        this.requestIds = requestIds;
    }

    public Result request(DiscordUserId discordUserId, String targetServer, String command) {
        if (discordUserId == null || targetServer == null || targetServer.isBlank()
                || command == null || command.isBlank()) {
            throw new IllegalArgumentException("Discord command request is incomplete");
        }
        UUID requestId = requireRequestId(requestIds.get());
        Optional<DiscordCommandActorResolver.ResolvedActor> actor = actors.resolve(discordUserId);
        if (actor.isEmpty()) {
            return Result.failed(requestId, Status.UNLINKED_ACTOR);
        }
        DiscordCommandActorResolver.ResolvedActor resolved = actor.orElseThrow();
        CommandBridgeRequest request = new CommandBridgeRequest(
                requestId,
                resolved.subjectId(),
                resolved.actorPlayerId(),
                targetServer,
                command,
                clock.instant()
        );
        return map(requestId, transport.dispatch(request));
    }

    private static Result map(UUID requestId, HttpMinecraftCommandBridgeClient.Result delivery) {
        return switch (delivery.status()) {
            case RECEIVED -> Result.received(requestId, delivery.response().orElseThrow());
            case INVALID_SERVER -> Result.failed(requestId, Status.INVALID_SERVER);
            case ENDPOINT_UNAVAILABLE -> Result.failed(requestId, Status.ENDPOINT_UNAVAILABLE);
            case AMBIGUOUS_FAILURE -> Result.failed(requestId, Status.AMBIGUOUS_FAILURE);
            case INVALID_RESPONSE -> Result.failed(requestId, Status.INVALID_RESPONSE);
        };
    }

    private static UUID requireRequestId(UUID value) {
        if (value == null) {
            throw new IllegalStateException("command bridge request ID source returned null");
        }
        return value;
    }

    @FunctionalInterface
    interface Transport {
        HttpMinecraftCommandBridgeClient.Result dispatch(CommandBridgeRequest request);
    }

    public record Result(UUID requestId, Status status, Optional<CommandBridgeResponse> response) {
        public Result {
            if (requestId == null || status == null || response == null
                    || (status == Status.RECEIVED) != response.isPresent()) {
                throw new IllegalArgumentException("command bridge coordinator result is invalid");
            }
        }

        static Result received(UUID requestId, CommandBridgeResponse response) {
            return new Result(requestId, Status.RECEIVED, Optional.of(response));
        }

        static Result failed(UUID requestId, Status status) {
            return new Result(requestId, status, Optional.empty());
        }
    }

    public enum Status {
        RECEIVED,
        UNLINKED_ACTOR,
        INVALID_SERVER,
        ENDPOINT_UNAVAILABLE,
        AMBIGUOUS_FAILURE,
        INVALID_RESPONSE
    }
}
