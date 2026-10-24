package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.enthusia.staff.protocol.ChatBridgeArtifact;
import org.junit.jupiter.api.Test;

class DiscordRichItemEmbedsTest {
    private static final String THUMBNAIL_FILE = "Item-1.png";

    @Test
    void itemTooltipAndThumbnailRenderInOneDiscordEmbed() {
        var artifacts = List.of(
                image(ChatBridgeArtifact.Kind.ITEM, THUMBNAIL_FILE, "Diamond Spear"),
                image(ChatBridgeArtifact.Kind.TOOLTIP, "ToolTip-1.png", "Minecraft item details"));
        var embeds = DiscordRichItemEmbeds.create(artifacts, "Qaysoz");
        assertEquals(1, embeds.size());
        var embed = embeds.getFirst();
        assertEquals("Qaysoz's Item", embed.getTitle());
        assertEquals("Diamond Spear", embed.getDescription());
        assertEquals("attachment://" + THUMBNAIL_FILE, embed.getThumbnail().getUrl());
        assertEquals("attachment://ToolTip-1.png", embed.getImage().getUrl());
    }

    @Test
    void missingTooltipKeepsItemCardAndInventoryGetsOwnImage() {
        var embeds = DiscordRichItemEmbeds.create(List.of(
                image(ChatBridgeArtifact.Kind.ITEM, THUMBNAIL_FILE, "Diamond Spear"),
                image(ChatBridgeArtifact.Kind.INVENTORY, "Inventory.png", "Shared inventory")),
                "Qaysoz");
        assertEquals(2, embeds.size());
        assertNull(embeds.getFirst().getImage());
        assertEquals("Qaysoz's Inventory", embeds.get(1).getTitle());
        assertEquals("attachment://Inventory.png", embeds.get(1).getImage().getUrl());
    }

    @Test
    void multipleItemSharesPairWithOwnTooltipAndOrphansAreNotUploaded() {
        var artifacts = List.of(
                imageAt(ChatBridgeArtifact.Kind.ITEM, 10, "Item-A.png", "Spear"),
                imageAt(ChatBridgeArtifact.Kind.TOOLTIP, 40, "Tooltip-B.png", "Details B"),
                imageAt(ChatBridgeArtifact.Kind.TOOLTIP, 10, "Tooltip-A.png", "Details A"),
                imageAt(ChatBridgeArtifact.Kind.ITEM, 40, "Item-B.png", "Axe"),
                imageAt(ChatBridgeArtifact.Kind.TOOLTIP, 99, "Orphan.png", "Not shown"));
        var selected = DiscordRichItemEmbeds.referencedArtifacts(artifacts);
        assertEquals(4, selected.size());
        assertTrue(selected.stream().noneMatch(a -> a.filename().equals("Orphan.png")));
        var embeds = DiscordRichItemEmbeds.create(artifacts, "Qaysoz");
        assertEquals(2, embeds.size());
        assertEquals("Spear", embeds.get(0).getDescription());
        assertEquals("attachment://Tooltip-A.png", embeds.get(0).getImage().getUrl());
        assertEquals("Axe", embeds.get(1).getDescription());
        assertEquals("attachment://Tooltip-B.png", embeds.get(1).getImage().getUrl());
    }

    @Test
    void mapAndCustomImagesArePreservedInsteadOfDiscarded() {
        var artifacts = List.of(
                imageAt(ChatBridgeArtifact.Kind.TOOLTIP, 7, "Orphan.png", "Details"),
                imageAt(ChatBridgeArtifact.Kind.MAP, 10, "Map.png", "Treasure map"),
                imageAt(ChatBridgeArtifact.Kind.OTHER, 20, "Preview.png", "Shared preview"));
        var selected = DiscordRichItemEmbeds.referencedArtifacts(artifacts);
        assertEquals(List.of("Map.png", "Preview.png"),
                selected.stream().map(ChatBridgeArtifact::filename).toList());

        var embeds = DiscordRichItemEmbeds.create(artifacts, "Qaysoz");
        assertEquals(2, embeds.size());
        assertEquals("Qaysoz's Map", embeds.get(0).getTitle());
        assertEquals("Treasure map", embeds.get(0).getDescription());
        assertEquals("attachment://Map.png", embeds.get(0).getImage().getUrl());
        assertEquals("Qaysoz's Image", embeds.get(1).getTitle());
        assertEquals("Shared preview", embeds.get(1).getDescription());
        assertEquals("attachment://Preview.png", embeds.get(1).getImage().getUrl());
    }

    @Test
    void mixedItemTooltipAndMapPreserveMessageOrder() {
        var artifacts = List.of(
                imageAt(ChatBridgeArtifact.Kind.ITEM, 30, "Sword.png", "Sword"),
                imageAt(ChatBridgeArtifact.Kind.TOOLTIP, 30, "SwordTooltip.png", "Sword details"),
                imageAt(ChatBridgeArtifact.Kind.MAP, 2, "Map.png", "Map"));
        var selected = DiscordRichItemEmbeds.referencedArtifacts(artifacts);
        assertEquals(List.of("Map.png", "Sword.png", "SwordTooltip.png"),
                selected.stream().map(ChatBridgeArtifact::filename).toList());

        var embeds = DiscordRichItemEmbeds.create(artifacts, "Qaysoz");
        assertEquals(2, embeds.size());
        assertEquals("Qaysoz's Map", embeds.get(0).getTitle());
        assertEquals("Qaysoz's Item", embeds.get(1).getTitle());
        assertEquals("attachment://SwordTooltip.png", embeds.get(1).getImage().getUrl());
    }

    @Test
    void orphanTooltipDoesNotInventAnItemCard() {
        assertTrue(DiscordRichItemEmbeds.create(List.of(
                image(ChatBridgeArtifact.Kind.TOOLTIP, "ToolTip.png", "Minecraft item details")),
                "Qaysoz").isEmpty());
    }

    private static ChatBridgeArtifact image(ChatBridgeArtifact.Kind kind, String filename, String altText) {
        return imageAt(kind, 0, filename, altText);
    }

    private static ChatBridgeArtifact imageAt(
            ChatBridgeArtifact.Kind kind, int position, String filename, String altText) {
        return new ChatBridgeArtifact(kind, position, filename, "image/png",
                altText, new byte[]{1, 2, 3});
    }
}
