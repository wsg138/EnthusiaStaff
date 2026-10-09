package net.enthusia.staff.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.ports.PlayerDirectory;
import org.junit.jupiter.api.Test;

class PlayerNamesTest {
    @Test
    void resolvesOfflineBedrockNamesOncePerResponseAndRefreshesAfterRename() {
        UUID id = UUID.randomUUID();
        AtomicReference<String> name = new AtomicReference<>(".BedrockPlayer");
        AtomicInteger reads = new AtomicInteger();
        PlayerDirectory directory = directory(input -> {
            assertEquals(id.toString(), input);
            reads.incrementAndGet();
            return Optional.of(identity(id, Optional.of(name.get())));
        });
        PlayerNames labels = new PlayerNames(directory);
        assertEquals(".BedrockPlayer", labels.apply(id));
        name.set(".RenamedPlayer");
        assertEquals(".BedrockPlayer", labels.apply(id));
        assertEquals(1, reads.get());
        assertEquals(".RenamedPlayer", new PlayerNames(directory).apply(id));
    }

    @Test
    void missingOrBlankNamesKeepDistinctExplicitUnknownIdentities() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlayerNames labels = new PlayerNames(directory(input -> Optional.of(
                identity(UUID.fromString(input), Optional.of(" ")))));
        assertEquals("Unknown player (" + first + ")", labels.apply(first));
        assertEquals("Unknown player (" + second + ")", labels.apply(second));
        assertEquals(PlayerNames.unknown(first), new PlayerNames(null).apply(first));
        assertEquals(PlayerNames.unknown(first), new PlayerNames(directory(input -> Optional.empty())).apply(first));
    }

    @Test
    void boundsDirectoryReadsAndDoesNotPretendStorageFailuresAreUnknownNames() {
        AtomicInteger reads = new AtomicInteger();
        PlayerNames labels = new PlayerNames(directory(input -> {
            reads.incrementAndGet();
            return Optional.empty();
        }));
        for (int i = 0; i < 600; i++) {
            labels.apply(new UUID(1L, i));
        }
        assertEquals(512, reads.get());
        PlayerNames failed = new PlayerNames(directory(input -> {
            throw new IllegalStateException("storage offline");
        }));
        assertThrows(IllegalStateException.class, () -> failed.apply(UUID.randomUUID()));
    }

    private static PlayerIdentity identity(UUID id, Optional<String> name) {
        return new PlayerIdentity(id, name, PlayerPlatform.UNKNOWN, Instant.EPOCH, Instant.EPOCH);
    }

    private static PlayerDirectory directory(java.util.function.Function<String, Optional<PlayerIdentity>> lookup) {
        return (PlayerDirectory) Proxy.newProxyInstance(PlayerDirectory.class.getClassLoader(),
                new Class<?>[]{PlayerDirectory.class}, (proxy, method, args) -> {
                    if (method.getName().equals("find")) {
                        return lookup.apply((String) args[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
