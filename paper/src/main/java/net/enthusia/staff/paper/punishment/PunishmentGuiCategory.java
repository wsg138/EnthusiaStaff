package net.enthusia.staff.paper.punishment;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Six broad staff-facing navigation groups. Family/ID/escalation semantics are
 * deliberately unchanged; unknown future families go to OTHER.
 */
enum PunishmentGuiCategory {
    CHAT("chat", "Chat & Communication", "Chat abuse, harassment, language and spam",
            Material.WRITABLE_BOOK, NamedTextColor.AQUA,
            Set.of("chat", "spam", "language", "politics", "hate", "harassment")),
    GAMEPLAY("gameplay", "Cheating & Gameplay", "Unfair clients, exploits and disruption",
            Material.DIAMOND_SWORD, NamedTextColor.GOLD,
            Set.of("cheating", "exploit", "mechanics", "complicity")),
    SAFETY("safety", "Safety & Privacy", "Threats, dangerous conduct and private information",
            Material.SHIELD, NamedTextColor.RED,
            Set.of("safety", "privacy")),
    ACCOUNTS("accounts", "Accounts & Evasion", "Account misuse, evasion and impersonation",
            Material.ENDER_EYE, NamedTextColor.LIGHT_PURPLE,
            Set.of("account", "evasion", "identity")),
    COMMUNITY("community", "Community & Conduct", "Advertising, reports and public conduct",
            Material.LECTERN, NamedTextColor.GREEN,
            Set.of("advertising", "reports", "staff", "dishonesty", "content")),
    ECONOMY("economy", "Economy & Reputation", "Market rules and reputation abuse",
            Material.GOLD_INGOT, NamedTextColor.GREEN,
            Set.of("market", "reputation")),
    OTHER("other", "Other Rules", "Additional configured reasons", Material.MAP,
            NamedTextColor.GRAY, Set.of());

    private final String id;
    private final String title;
    private final String description;
    private final Material material;
    private final NamedTextColor color;
    private final Set<String> families;

    PunishmentGuiCategory(String id, String title, String description, Material material,
            NamedTextColor color, Set<String> families) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.material = material;
        this.color = color;
        this.families = Set.copyOf(families);
    }

    String id() { return id; }
    String title() { return title; }
    String description() { return description; }
    Material material() { return material; }
    NamedTextColor color() { return color; }
    Set<String> families() { return families; }

    boolean includes(String family) {
        return this == OTHER ? Arrays.stream(values()).filter(value -> value != OTHER)
                .noneMatch(value -> value.families.contains(family)) : families.contains(family);
    }

    static PunishmentGuiCategory forFamily(String family) {
        return Arrays.stream(values()).filter(value -> value != OTHER && value.families.contains(family))
                .findFirst().orElse(OTHER);
    }

    static PunishmentGuiCategory byId(String id) {
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    static List<PunishmentGuiCategory> ordered() {
        return List.of(values());
    }
}
