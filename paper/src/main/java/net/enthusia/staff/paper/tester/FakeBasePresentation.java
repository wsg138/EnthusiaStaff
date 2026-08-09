package net.enthusia.staff.paper.tester;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;

final class FakeBasePresentation {
    private FakeBasePresentation() {
    }

    static double distanceSquared(Location location, FakeBasePlacementPlanner.Anchor anchor) {
        double dx = location.getX() - (anchor.x() + 0.5D);
        double dy = location.getY() - anchor.y();
        double dz = location.getZ() - (anchor.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    static Component controls(String prefix, String targetName, NamedTextColor color) {
        return Component.text(prefix + " ", color)
                .append(Component.text("[Extend]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/cheattester base extend " + targetName)))
                .append(Component.space())
                .append(Component.text("[Clear]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/cheattester base clear " + targetName)))
                .append(Component.space())
                .append(Component.text("[Teleport]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.runCommand("/cheattester base teleport " + targetName)));
    }
}
