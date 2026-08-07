package net.enthusia.staff.paper.tester;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns optional packet-adapter installation and durable fake-entity handle decoding. */
final class CheatTesterFakeEntitySupport {
    private final JavaPlugin plugin;
    private final CheatTesterFakeEntityState state;
    private final Runnable failureHandler;
    private final ObjectMapper json = new ObjectMapper();

    CheatTesterFakeEntitySupport(
            JavaPlugin plugin,
            CheatTesterFakeEntityState state,
            Runnable failureHandler
    ) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.failureHandler = java.util.Objects.requireNonNull(failureHandler, "failureHandler");
    }

    FakeEntityAdapter install() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            plugin.getLogger().warning("ProtocolLib is unavailable; fake-entity Cheat Tester is disabled fail-closed");
            return FakeEntityAdapter.unavailable();
        }
        try {
            return ProtocolLibFakeEntityAdapter.install(plugin, state::recordInteraction, failureHandler);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "ProtocolLib fake-entity adapter could not start; fake-entity Cheat Tester is disabled",
                    failure
            );
            return FakeEntityAdapter.unavailable();
        }
    }

    Optional<FakeEntityAdapter.Handle> decodeHandle(String configuration) {
        try {
            JsonNode node = json.readTree(configuration);
            JsonNode id = node.get("fakeEntityId");
            JsonNode uuid = node.get("fakeEntityUuid");
            if (id == null || uuid == null) {
                return Optional.empty();
            }
            return Optional.of(new FakeEntityAdapter.Handle(id.asInt(), UUID.fromString(uuid.asText())));
        } catch (RuntimeException | JsonProcessingException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to decode recovered fake-entity handle", exception);
            return Optional.empty();
        }
    }
}
