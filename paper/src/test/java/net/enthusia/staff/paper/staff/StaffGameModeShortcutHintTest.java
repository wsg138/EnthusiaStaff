package net.enthusia.staff.paper.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.enthusia.staff.domain.auth.StaffRank;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StaffGameModeShortcutHintTest {
    @Test
    void enablesOnlyClientCapabilityAndRevokesItWhenCommandPermissionOrRankIsLost() {
        Harness h = new Harness();
        StaffGameModeShortcutHint hints = new StaffGameModeShortcutHint();
        hints.refresh(h.player, StaffRank.FOUNDER);
        h.allowed = false;
        hints.refresh(h.player, StaffRank.FOUNDER);
        h.allowed = true;
        hints.refresh(h.player, StaffRank.ADMIN);
        hints.refresh(h.player, StaffRank.MOD);
        assertEquals(List.of((byte) 2, (byte) 0, (byte) 2, (byte) 0), h.levels);
    }

    @Test
    void neverOverridesRealOperatorsOrEnablesLowerRanks() {
        Harness h = new Harness();
        StaffGameModeShortcutHint hints = new StaffGameModeShortcutHint();
        for (StaffRank rank : new StaffRank[]{StaffRank.MOD, StaffRank.DEVELOPER, StaffRank.HELPER, null}) {
            hints.refresh(h.player, rank);
        }
        h.op = true;
        hints.refresh(h.player, StaffRank.FOUNDER);
        assertEquals(List.of(), h.levels);
    }

    private static final class Harness {
        final UUID id = UUID.randomUUID();
        final List<Byte> levels = new ArrayList<>();
        boolean allowed = true;
        boolean op;
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isOp" -> op;
                    case "hasPermission" -> allowed;
                    case "sendOpLevel" -> {
                        levels.add((Byte) args[0]);
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected player mutation: " + method.getName());
                });
    }
}
