package net.enthusia.staff.paper.command;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

final class InspectActionSection {
    private static final String HISTORY_PERMISSION = "enthusiastaff.history.view";
    private static final String CLIENT_PERMISSION = "enthusiastaff.client";
    private static final String INVENTORY_PERMISSION = "enthusiastaff.inventory.view";
    private static final String PUNISH_PERMISSION = "enthusiastaff.punish";
    private static final String CONFIGURED_PUNISH_PERMISSION = "enthusiastaff.punish.configured";

    private InspectActionSection() {
    }

    static List<Component> render(UUID playerId, Access access) {
        if (!access.any()) {
            return List.of();
        }
        Component line = Component.text("Actions:", NamedTextColor.GOLD);
        if (access.history()) {
            line = append(line, "History", "/history " + playerId);
        }
        if (access.clientEvidence()) {
            line = append(line, "Client", "/client " + playerId);
        }
        if (access.inventory()) {
            line = append(line, "Inventory", "/inspect inventory " + playerId);
            line = append(line, "Ender", "/inspect ender " + playerId);
        }
        if (access.punishment()) {
            line = append(line, "Punish", "/punish " + playerId);
        }
        return List.of(line);
    }

    static Access access(Predicate<String> allowed, boolean punishmentAuthority) {
        java.util.Objects.requireNonNull(allowed, "allowed");
        return new Access(
                allowed.test(HISTORY_PERMISSION),
                allowed.test(CLIENT_PERMISSION),
                allowed.test(INVENTORY_PERMISSION),
                punishmentAuthority
                        && allowed.test(PUNISH_PERMISSION)
                        && allowed.test(CONFIGURED_PUNISH_PERMISSION)
        );
    }

    private static Component append(Component line, String label, String command) {
        return line.append(Component.space()).append(
                Component.text('[' + label + ']', NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text(command, NamedTextColor.GRAY)))
        );
    }

    record Access(
            boolean history,
            boolean clientEvidence,
            boolean inventory,
            boolean punishment
    ) {
        boolean any() {
            return history || clientEvidence || inventory || punishment;
        }
    }
}
