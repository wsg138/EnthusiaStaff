package net.enthusia.staff.paper.enforcement;

import java.time.Duration;
import net.enthusia.staff.common.CaseId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

final class StaffWarningPresentation {
    private StaffWarningPresentation() {}

    static void show(Player player, String reason, CaseId caseId) {
        player.sendMessage(chat(reason, caseId));
        player.showTitle(Title.title(
                Component.text("⚠ WARNING ⚠", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("For: " + reason, NamedTextColor.YELLOW, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(6), Duration.ofMillis(800))));
        player.playSound(player.getLocation(), "minecraft:block.note_block.pling", 1.0f, 0.8f);
    }

    static Component chat(String reason, CaseId caseId) {
        return Component.newline()
                .append(Component.text("━━━━━━━━━━ ⚠ WARNING ⚠ ━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("You have received a warning for: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(reason, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Case: " + caseId, NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());
    }
}
