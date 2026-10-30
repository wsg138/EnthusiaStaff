package net.enthusia.staff.paper.integration;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.enthusia.staff.api.chat.RichChatArtifact;
import net.enthusia.staff.api.chat.RichChatArtifactProvider;
import net.enthusia.staff.api.chat.RichChatArtifactRequest;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import net.enthusia.staff.protocol.ChatBridgeArtifactBundle;
import net.enthusia.staff.protocol.ChatBridgeRenderedMessage;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional fail-open adapter from a Bukkit rich-chat service to the network artifact contract. */
final class RichChatArtifactService {
    static final Duration RENDER_TIMEOUT = Duration.ofMillis(1_500);

    @FunctionalInterface
    interface ProviderLookup {
        RichChatArtifactProvider provider();
    }

    private final ProviderLookup providers;
    private final Clock clock;

    private RichChatArtifactService(ProviderLookup providers, Clock clock) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    static RichChatArtifactService forPlugin(JavaPlugin plugin, Clock clock) {
        Objects.requireNonNull(plugin, "plugin");
        return new RichChatArtifactService(() -> {
            RegisteredServiceProvider<RichChatArtifactProvider> registration =
                    plugin.getServer().getServicesManager().getRegistration(
                            RichChatArtifactProvider.class
                    );
            return registration == null ? null : registration.getProvider();
        }, clock);
    }

    static RichChatArtifactService forTest(ProviderLookup providers, Clock clock) {
        return new RichChatArtifactService(providers, clock);
    }

    Optional<ChatBridgeArtifactBundle> render(
            String sourceServerId,
            ChatBridgeRenderedMessage message
    ) {
        Objects.requireNonNull(message, "message");
        RichChatArtifactProvider provider;
        try {
            provider = providers.provider();
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
        if (provider == null) {
            RichInventoryPreviewDiagnostics.ifInventoryMarker(
                    message.canonicalPlainText(), "rich_provider_unavailable");
            return Optional.empty();
        }

        long now = clock.millis();
        long remaining = message.expiresAtEpochMillis() - now;
        if (remaining <= 0) {
            return Optional.empty();
        }

        CompletableFuture<List<RichChatArtifact>> future;
        try {
            CompletionStage<List<RichChatArtifact>> stage = provider.render(new RichChatArtifactRequest(
                    message.eventId(),
                    message.minecraftPlayerId(),
                    message.displayName(),
                    message.logicalChannelId(),
                    message.canonicalPlainText(),
                    message.createdAtEpochMillis(),
                    message.expiresAtEpochMillis()
            ));
            if (stage == null) {
                RichInventoryPreviewDiagnostics.ifInventoryMarker(
                        message.canonicalPlainText(), "render_stage_missing");
                return Optional.empty();
            }
            future = stage.toCompletableFuture();
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }

        long timeoutMillis = Math.min(RENDER_TIMEOUT.toMillis(), remaining);
        try {
            List<RichChatArtifact> rendered =
                    future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (rendered == null || rendered.isEmpty()) {
                RichInventoryPreviewDiagnostics.ifInventoryMarker(
                        message.canonicalPlainText(), "renderer_returned_no_artifacts");
            }
            return toBundle(sourceServerId, message, rendered);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return Optional.empty();
        } catch (TimeoutException failure) {
            RichInventoryPreviewDiagnostics.ifInventoryMarker(
                    message.canonicalPlainText(), "renderer_timeout");
            future.cancel(true);
            return Optional.empty();
        } catch (ExecutionException | RuntimeException failure) {
            RichInventoryPreviewDiagnostics.ifInventoryMarker(
                    message.canonicalPlainText(), "renderer_execution_failed");
            future.cancel(true);
            return Optional.empty();
        }
    }

    private static Optional<ChatBridgeArtifactBundle> toBundle(
            String sourceServerId,
            ChatBridgeRenderedMessage message,
            List<RichChatArtifact> rendered
    ) {
        if (rendered == null || rendered.isEmpty()
                || rendered.size() > ChatBridgeArtifactBundle.MAX_ARTIFACTS) {
            return Optional.empty();
        }

        List<ChatBridgeArtifact> artifacts = new ArrayList<>(rendered.size());
        long totalBytes = 0L;
        try {
            for (RichChatArtifact value : rendered) {
                if (value == null) {
                    return Optional.empty();
                }
                byte[] data = value.data();
                totalBytes += data.length;
                if (totalBytes > ChatBridgeArtifactBundle.MAX_TOTAL_ARTIFACT_BYTES) {
                    RichInventoryPreviewDiagnostics.ifInventoryMarker(
                            message.canonicalPlainText(), "bundle_over_limit");
                    return Optional.empty();
                }
                artifacts.add(new ChatBridgeArtifact(
                        ChatBridgeArtifact.Kind.valueOf(value.kind().name()),
                        value.position(),
                        value.filename(),
                        value.contentType(),
                        value.altText(),
                        data
                ));
            }
            return Optional.of(new ChatBridgeArtifactBundle(
                    message.eventId(),
                    message.createdAtEpochMillis(),
                    message.expiresAtEpochMillis(),
                    sourceServerId,
                    message.logicalChannelId(),
                    artifacts
            ));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }
}
