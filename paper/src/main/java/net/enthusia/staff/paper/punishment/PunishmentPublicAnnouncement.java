package net.enthusia.staff.paper.punishment;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.casefile.CaseVisibility;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.domain.sanction.SanctionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.Plugin;

/** Announces only newly committed public punishments, never requests or private case details. */
final class PunishmentPublicAnnouncement {
    private PunishmentPublicAnnouncement() {
    }

    static void publish(Plugin plugin, CaseVisibility visibility, boolean replayed,
            String playerName, List<SanctionSpec> sanctions, String publicReason) {
        Objects.requireNonNull(plugin, "plugin");
        if (visibility != CaseVisibility.PUBLIC || replayed) {
            return;
        }
        Component notice = message(playerName, sanctions, publicReason);
        // Broadcast on Folia's global region scheduler, not the asynchronous database worker.
        plugin.getServer().getGlobalRegionScheduler().execute(
                plugin, () -> plugin.getServer().broadcast(notice)
        );
    }

    static Component message(String playerName, List<SanctionSpec> sanctions, String publicReason) {
        String name = compact(Objects.requireNonNull(playerName, "playerName"), 32);
        String reason = compact(Objects.requireNonNull(publicReason, "publicReason"), 100);
        String action = Objects.requireNonNull(sanctions, "sanctions").stream()
                .map(SanctionSpec::type)
                .map(PunishmentPublicAnnouncement::label)
                .distinct()
                .collect(Collectors.joining(" + "));
        if (action.isBlank()) {
            action = "punishment";
        }
        return Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD)
                .append(Component.newline())
                .append(Component.text("PUBLIC PUNISHMENT", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" received ", NamedTextColor.GRAY))
                .append(Component.text(action, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Reason: ", NamedTextColor.GRAY))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
    }

    private static String label(SanctionType type) {
        if (type.isBan()) {
            return "a ban";
        }
        return switch (type) {
            case KICK -> "a kick";
            case WARNING -> "a warning";
            case MUTE, PUBLIC_MUTE -> "a mute";
            case REPORT_RESTRICTION -> "a report restriction";
            case REPUTATION_BLACKLIST -> "a reputation restriction";
            case MARKET_BLACKLIST -> "a market restriction";
            default -> "a disciplinary action";
        };
    }

    private static String compact(String value, int maximum) {
        String normalized = value.replaceAll("[\\p{Cntrl}\\n\\r\\t]+", " ").trim();
        if (normalized.isEmpty()) {
            return "Unspecified";
        }
        return normalized.length() <= maximum ? normalized
                : normalized.substring(0, maximum - 1).stripTrailing() + "…";
    }
}
