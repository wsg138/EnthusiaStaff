package net.enthusia.staff.discordbot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.enthusia.staff.protocol.ChatBridgeArtifact;

/**
 * Native JDA rendering of the existing InteractiveChat image artifacts.
 * An item thumbnail + full tooltip belong in one Discord embed (not bare files).
 */
final class DiscordRichItemEmbeds {
    private static final int ITEM_BORDER_COLOR = 0x50C7CF;
    private static final int INVENTORY_BORDER_COLOR = 0xD2A53F;
    private static final int ENDER_BORDER_COLOR = 0x9D72E3;

    private DiscordRichItemEmbeds() {
    }

    static List<MessageEmbed> create(List<ChatBridgeArtifact> artifacts, String minecraftName) {
        Objects.requireNonNull(artifacts, "artifacts");
        String player = validPlayerName(minecraftName);
        List<ChatBridgeArtifact> ordered = artifacts.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ChatBridgeArtifact::position)
                        .thenComparing(ChatBridgeArtifact::filename))
                .toList();
        List<MessageEmbed> result = new ArrayList<>();
        ChatBridgeArtifact icon = ordered.stream()
                .filter(item -> item.kind() == ChatBridgeArtifact.Kind.ITEM)
                .findFirst().orElse(null);
        ChatBridgeArtifact tooltip = ordered.stream()
                .filter(item -> item.kind() == ChatBridgeArtifact.Kind.TOOLTIP)
                .findFirst().orElse(null);
        if (icon != null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(ITEM_BORDER_COLOR)
                    .setTitle(player + "'s Item")
                    .setDescription(icon.altText())
                    .setThumbnail("attachment://" + icon.filename());
            if (tooltip != null) {
                embed.setImage("attachment://" + tooltip.filename());
            }
            result.add(embed.build());
        }
        for (ChatBridgeArtifact artifact : ordered) {
            if (artifact.kind() == ChatBridgeArtifact.Kind.INVENTORY
                    || artifact.kind() == ChatBridgeArtifact.Kind.ENDER_CHEST) {
                boolean ender = artifact.kind() == ChatBridgeArtifact.Kind.ENDER_CHEST;
                result.add(new EmbedBuilder()
                        .setColor(ender ? ENDER_BORDER_COLOR : INVENTORY_BORDER_COLOR)
                        .setTitle(player + (ender ? "'s Ender Chest" : "'s Inventory"))
                        .setImage("attachment://" + artifact.filename())
                        .build());
            }
        }
        return List.copyOf(result);
    }

    private static String validPlayerName(String minecraftName) {
        if (minecraftName == null || !minecraftName.matches("[A-Za-z0-9_]{3,16}")) {
            return "Player";
        }
        return minecraftName;
    }
}
