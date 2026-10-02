package net.enthusia.staff.velocity;

import com.velocitypowered.api.proxy.Player;
import io.github.miniplaceholders.api.Expansion;
import io.github.miniplaceholders.api.MiniPlaceholders;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.slf4j.Logger;

/** Registers Staff-owned public network and viewer-local counts through MiniPlaceholders. */
final class MiniPlaceholdersPublicOnlineBridge {
    private static final String EXPANSION = "enthusiastaff";
    private static final String NETWORK_KEY = "public_online";
    private static final String LOCAL_KEY = "local_public_online";

    private MiniPlaceholdersPublicOnlineBridge() {
    }

    static Runnable register(IntSupplier networkCount, ToIntFunction<Player> localCount, Logger logger) {
        Objects.requireNonNull(networkCount, "networkCount");
        Objects.requireNonNull(localCount, "localCount");
        Objects.requireNonNull(logger, "logger");
        if (MiniPlaceholders.expansionByName(EXPANSION) != null) {
            throw new IllegalStateException("MiniPlaceholders expansion 'enthusiastaff' is already registered");
        }
        Expansion expansion = Expansion.builder(EXPANSION)
                .globalPlaceholder(NETWORK_KEY, (queue, context) -> countTag(networkCount.getAsInt()))
                .globalPlaceholder(LOCAL_KEY, (queue, context) -> countTag(
                        context.target() instanceof Player viewer ? localCount.applyAsInt(viewer) : 0
                ))
                .build();
        expansion.register();
        if (logger.isInfoEnabled()) {
            logger.info("Registered Staff public-online placeholders {} and {}",
                    PublicOnlineCountPolicy.PLACEHOLDER, LocalPublicOnlineCountPolicy.PLACEHOLDER);
        }
        return () -> {
            if (expansion.registered()) {
                expansion.unregister();
            }
        };
    }

    private static Tag countTag(int count) {
        return Tag.selfClosingInserting(Component.text(Integer.toString(count)));
    }
}
