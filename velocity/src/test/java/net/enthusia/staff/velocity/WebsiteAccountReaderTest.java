package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import net.enthusia.staff.domain.application.AccountLinkingService.IssuedCode;
import net.enthusia.staff.domain.ports.AccountLinkingStore.Direction;
import net.enthusia.staff.persistence.UuidBytes;
import org.junit.jupiter.api.Test;

final class WebsiteAccountReaderTest {
    private static final String USER = "111111111111111111";
    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    @Test
    void currentAccountResponseContainsOnlyCurrentPublicIdentityFields() {
        List<Object> bindings = new ArrayList<>();
        WebsiteAccountReader reader = reader(List.of(Map.of(
                "minecraft_player_id", UuidBytes.toBytes(PLAYER), "current_username", "Player", "linked_at", Timestamp.from(NOW)
        )), bindings);
        Map<String, Object> result = reader.read(USER);
        assertEquals(List.of(Map.of("uuid", PLAYER.toString(), "name", "Player", "linkedAt", NOW.toString())), result.get("accounts"));
        assertEquals(List.of(USER), bindings);
    }

    @Test
    void aLinkStatusRequestIsBoundToItsDiscordOwnerAndDirection() {
        List<Object> bindings = new ArrayList<>();
        WebsiteAccountReader reader = reader(List.of(), bindings);
        assertEquals(Map.of("status", "EXPIRED_OR_USED"), reader.status(USER, "a".repeat(64)));
        assertEquals(List.of("a".repeat(64), USER), bindings);
        assertThrows(WebsiteApiException.class, () -> reader.status(USER, "not-a-hash"));
    }

    @Test
    void issuedChallengesUseTheSharedServiceAndMinecraftLinkCommand() {
        Map<String, Object> result = reader(List.of(), new ArrayList<>()).start(USER);
        assertEquals("/link ABCDEFGHJK", result.get("command"));
        assertTrue(result.get("requestId").toString().matches("[a-f0-9]{64}"));
        assertEquals(NOW.plusSeconds(300).toString(), result.get("expiresAt"));
    }

    private static WebsiteAccountReader reader(List<Map<String, Object>> rows, List<Object> bindings) {
        int[] position = {-1};
        ResultSet result = proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
            case "next" -> ++position[0] < rows.size();
            case "getString", "getBytes", "getTimestamp" -> rows.get(position[0]).get(args[0]);
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
            case "setString" -> { bindings.add(args[1]); yield null; }
            case "executeQuery" -> result;
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        Connection connection = proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> {
                String sql = args[0].toString();
                assertTrue(sql.contains("unlinked_at IS NULL"));
                if (sql.contains("discord_link_codes")) assertTrue(sql.contains("DISCORD_TO_MINECRAFT"));
                yield statement;
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        DataSource source = proxy(DataSource.class, (object, method, args) -> {
            if (method.getName().equals("getConnection")) return connection;
            throw new UnsupportedOperationException(method.getName());
        });
        return new WebsiteAccountReader(source, CLOCK, id -> {
            assertEquals(USER, id.value());
            return new IssuedCode("ABCDEFGHJK", NOW.plusSeconds(300), Direction.DISCORD_TO_MINECRAFT);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
