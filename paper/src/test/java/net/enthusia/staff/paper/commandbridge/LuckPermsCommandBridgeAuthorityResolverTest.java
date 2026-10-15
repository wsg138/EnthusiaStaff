package net.enthusia.staff.paper.commandbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.UserManager;
import org.junit.jupiter.api.Test;

class LuckPermsCommandBridgeAuthorityResolverTest {
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void offDutyActorCannotLoadRankEvenWithDiscordOrLuckPermsIdentity() {
        AtomicInteger lookups = new AtomicInteger();
        LuckPerms permissions = fakeLuckPerms(lookups);
        var resolver = new LuckPermsCommandBridgeAuthorityResolver(permissions, player -> false);

        assertTrue(resolver.current(ACTOR, "enthusia.console.list").isEmpty());
        assertEquals(0, lookups.get(), "off-duty actor must not reach LuckPerms");
    }

    @Test
    void dutyRevokedDuringAsyncLookupFailsClosed() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger dutyChecks = new AtomicInteger();
        var resolver = new LuckPermsCommandBridgeAuthorityResolver(
                fakeLuckPerms(lookups),
                player -> dutyChecks.incrementAndGet() == 1
        );

        assertTrue(resolver.current(ACTOR, "enthusia.console.list").isEmpty());
        assertEquals(1, lookups.get());
        assertEquals(2, dutyChecks.get(), "duty must be checked after the lookup, not only before it");
    }

    @Test
    void missingDutyGateCannotEnableConsole() {
        assertThrows(NullPointerException.class,
                () -> new LuckPermsCommandBridgeAuthorityResolver(fakeLuckPerms(new AtomicInteger()), null));
    }

    private static LuckPerms fakeLuckPerms(AtomicInteger lookups) {
        UserManager users = (UserManager) Proxy.newProxyInstance(
                UserManager.class.getClassLoader(), new Class<?>[] {UserManager.class},
                (proxy, method, arguments) -> {
                    if ("loadUser".equals(method.getName())) {
                        lookups.incrementAndGet();
                        return CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (LuckPerms) Proxy.newProxyInstance(
                LuckPerms.class.getClassLoader(), new Class<?>[] {LuckPerms.class},
                (proxy, method, arguments) -> {
                    if ("getUserManager".equals(method.getName())) {
                        return users;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
