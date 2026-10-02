package net.enthusia.staff.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.Player;
import io.github.miniplaceholders.api.Expansion;
import io.github.miniplaceholders.api.MiniPlaceholders;
import java.lang.reflect.Proxy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MiniPlaceholdersPublicOnlineBridgeTest {
    @Test
    void registersNetworkAndViewerLocalSyntaxAndCleansThemUp() {
        Player viewer = viewer();
        Runnable cleanup = MiniPlaceholdersPublicOnlineBridge.register(
                () -> 7,
                player -> player == viewer ? 3 : 0,
                LoggerFactory.getLogger(MiniPlaceholdersPublicOnlineBridgeTest.class)
        );
        try {
            Expansion expansion = MiniPlaceholders.expansionByName("enthusiastaff");
            assertNotNull(expansion);
            assertTrue(expansion.hasGlobalPlaceholder("public_online"));
            assertTrue(expansion.hasGlobalPlaceholder("local_public_online"));
            assertEquals(Component.text("7"), render(PublicOnlineCountPolicy.PLACEHOLDER));
            assertEquals(Component.text("3"), render(LocalPublicOnlineCountPolicy.PLACEHOLDER, viewer));
            assertEquals(Component.text("0"), render(LocalPublicOnlineCountPolicy.PLACEHOLDER));
        } finally {
            cleanup.run();
        }
        assertNull(MiniPlaceholders.expansionByName("enthusiastaff"));
    }

    private static Component render(String placeholder) {
        return MiniMessage.miniMessage().deserialize(placeholder, MiniPlaceholders.globalPlaceholders());
    }

    private static Component render(String placeholder, Player viewer) {
        return MiniMessage.miniMessage().deserialize(
                placeholder,
                viewer,
                MiniPlaceholders.globalPlaceholders()
        );
    }

    private static Player viewer() {
        return (Player) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{Player.class},
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "viewer";
                    case "hashCode" -> System.identityHashCode(ignored);
                    case "equals" -> ignored == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
