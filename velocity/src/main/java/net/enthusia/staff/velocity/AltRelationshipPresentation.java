package net.enthusia.staff.velocity;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.enthusia.staff.domain.alt.AltRelationshipSummary;
import net.enthusia.staff.domain.moderation.CurrentLinkedMinecraftAccount;
import net.enthusia.staff.domain.player.PlayerIdentity;
import net.kyori.adventure.text.Component;

/** Staff-safe text presentation for current verified links and network-derived alt relationships. */
final class AltRelationshipPresentation {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private AltRelationshipPresentation() {
    }

    static List<Component> render(
            PlayerIdentity target,
            List<CurrentLinkedMinecraftAccount> linkedAccounts,
            boolean linkedAccountsAvailable,
            List<AltRelationshipSummary> relationships
    ) {
        if (target == null || linkedAccounts == null || relationships == null) {
            throw new IllegalArgumentException("alt relationship presentation fields must be present");
        }
        List<Component> lines = new ArrayList<>();
        String targetName = target.currentUsername().orElse(target.playerId().toString());
        lines.add(Component.text("Alt review for " + targetName + ":"));
        appendVerifiedLinks(lines, linkedAccounts, linkedAccountsAvailable);
        appendNetworkRelationships(lines, relationships);
        return List.copyOf(lines);
    }

    private static void appendVerifiedLinks(
            List<Component> lines,
            List<CurrentLinkedMinecraftAccount> linkedAccounts,
            boolean linkedAccountsAvailable
    ) {
        if (!linkedAccountsAvailable) {
            lines.add(Component.text("Verified linked Minecraft accounts: unavailable."));
            return;
        }
        lines.add(Component.text("Verified linked Minecraft accounts: " + linkedAccounts.size()));
        for (CurrentLinkedMinecraftAccount account : linkedAccounts) {
            String name = account.currentUsername().orElse(account.playerId().toString());
            lines.add(Component.text("- " + name + " (linked " + TIMESTAMP.format(account.linkedAt()) + ')'));
        }
    }

    private static void appendNetworkRelationships(List<Component> lines, List<AltRelationshipSummary> relationships) {
        lines.add(Component.text("Network relationships: " + relationships.size()));
        for (AltRelationshipSummary relationship : relationships) {
            lines.add(Component.text("- " + relationship.otherPlayerId() + " "
                    + relationship.state() + " confidence="
                    + Math.round(relationship.confidence() * 100.0) + "%"
                    + (relationship.lockedUntilReopened() ? " locked" : "")));
        }
    }
}
