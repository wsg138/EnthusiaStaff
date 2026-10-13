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

    /**
     * Keep only artifacts actually referenced by embeds. An unattached tooltip otherwise
     * appears as a raw Discord file / ALT image when its item could not be rendered.
     * Item and tooltip pairs share their original placeholder position.
     */
    static List<ChatBridgeArtifact> referencedArtifacts(List<ChatBridgeArtifact> artifacts) {
        Objects.requireNonNull(artifacts, "artifacts");
        List<ChatBridgeArtifact> ordered = artifacts.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ChatBridgeArtifact::position)
                        .thenComparingInt(artifact -> artifact.kind() == ChatBridgeArtifact.Kind.ITEM
                                ? 0 : artifact.kind() == ChatBridgeArtifact.Kind.TOOLTIP ? 1 : 2)
                        .thenComparing(ChatBridgeArtifact::filename))
                .toList();
        List<ChatBridgeArtifact> tooltips = new ArrayList<>(ordered.stream()
                .filter(a -> a.kind() == ChatBridgeArtifact.Kind.TOOLTIP).toList());
        List<ChatBridgeArtifact> kept = new ArrayList<>();
        for (ChatBridgeArtifact artifact : ordered) {
            if (artifact.kind() == ChatBridgeArtifact.Kind.ITEM) {
                kept.add(artifact);
                for (int index = 0; index < tooltips.size(); index++) {
                    ChatBridgeArtifact candidate = tooltips.get(index);
                    if (candidate.position() == artifact.position()) {
                        kept.add(candidate);
                        tooltips.remove(index);
                        break;
                    }
                }
            } else if (artifact.kind() == ChatBridgeArtifact.Kind.INVENTORY
                    || artifact.kind() == ChatBridgeArtifact.Kind.ENDER_CHEST) {
                kept.add(artifact);
            }
        }
        return List.copyOf(kept);
    }

    static List<MessageEmbed> create(List<ChatBridgeArtifact> artifacts, String minecraftName) {
        List<ChatBridgeArtifact> selected = referencedArtifacts(artifacts);
        String player = validPlayerName(minecraftName);
        List<MessageEmbed> result = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            ChatBridgeArtifact artifact = selected.get(index);
            if (artifact.kind() == ChatBridgeArtifact.Kind.ITEM) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(ITEM_BORDER_COLOR)
                        .setTitle(player + "'s Item")
                        .setDescription(artifact.altText())
                        .setThumbnail("attachment://" + artifact.filename());
                if (index + 1 < selected.size()) {
                    ChatBridgeArtifact next = selected.get(index + 1);
                    if (next.kind() == ChatBridgeArtifact.Kind.TOOLTIP
                            && next.position() == artifact.position()) {
                        embed.setImage("attachment://" + next.filename());
                        index++;
                    }
                }
                result.add(embed.build());
            } else if (artifact.kind() == ChatBridgeArtifact.Kind.INVENTORY
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
