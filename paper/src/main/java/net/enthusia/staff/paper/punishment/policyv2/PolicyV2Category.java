package net.enthusia.staff.paper.punishment.policyv2;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Presentation-only Policy v2 navigation. Offense identity and policy
 * relationships remain owned by the W1 PolicySnapshot contract.
 */
public enum PolicyV2Category {
    CHAT_SPAM(
            "chat-spam", "Chat & Spam", "Chat misuse, spam, language, and public-topic rules",
            Material.WRITABLE_BOOK, NamedTextColor.AQUA,
            Set.of("chat-spam", "chat", "spam", "language")
    ),
    HARASSMENT_ABUSE(
            "harassment-abuse", "Harassment & Abuse", "Targeted abuse, bullying, and harassment",
            Material.REDSTONE, NamedTextColor.RED,
            Set.of("harassment-abuse", "harassment", "abuse")
    ),
    HATE_EXTREMISM(
            "hate-extremism", "Hate & Extremism", "Hate content and extremist advocacy or glorification",
            Material.WITHER_SKELETON_SKULL, NamedTextColor.DARK_RED,
            Set.of("hate-extremism", "hate", "extremism")
    ),
    SEXUAL_INAPPROPRIATE(
            "sexual-inappropriate", "Sexual/Inappropriate Content", "Sexual, explicit, or otherwise inappropriate content",
            Material.PAINTING, NamedTextColor.LIGHT_PURPLE,
            Set.of("sexual-inappropriate", "sexual", "inappropriate-content", "content")
    ),
    SAFETY_THREATS_PRIVACY(
            "safety-threats-privacy", "Safety/Threats/Privacy", "Threats, personal safety, privacy, and doxxing",
            Material.SHIELD, NamedTextColor.RED,
            Set.of("safety-threats-privacy", "safety", "threats", "privacy")
    ),
    ADVERTISING_SCAMS(
            "advertising-scams", "Advertising/Scams", "Advertising, malicious links, scams, and deception",
            Material.EMERALD, NamedTextColor.GREEN,
            Set.of("advertising-scams", "advertising", "scams")
    ),
    CHEATING(
            "cheating", "Cheating", "Unauthorized clients, automation, X-ray, and unfair modifications",
            Material.DIAMOND_SWORD, NamedTextColor.GOLD,
            Set.of("cheating", "unauthorized-modifications")
    ),
    EXPLOITS_BUG_ABUSE_DUPLICATION(
            "exploits-bug-abuse-duplication", "Exploits/Bug Abuse/Duplication",
            "Intentional exploit abuse, bug abuse, and duplication",
            Material.TNT, NamedTextColor.GOLD,
            Set.of("exploits-bug-abuse-duplication", "exploits", "exploit", "bug-abuse", "duplication")
    ),
    SERVER_DISRUPTION(
            "server-disruption", "Server Disruption", "Technical abuse or conduct intended to disrupt the server",
            Material.REPEATER, NamedTextColor.GOLD,
            Set.of("server-disruption", "disruption", "technical-abuse")
    ),
    ACCOUNTS_VPN_ACCESS(
            "accounts-vpn-access", "Accounts/VPN/Access", "Account, VPN, and access-policy violations",
            Material.ENDER_EYE, NamedTextColor.LIGHT_PURPLE,
            Set.of("accounts-vpn-access", "accounts", "account", "vpn", "access")
    ),
    EVASION_ALT_ABUSE(
            "evasion-alt-abuse", "Evasion/Alt Abuse", "Punishment evasion and abusive alternate-account use",
            Material.ENDER_PEARL, NamedTextColor.LIGHT_PURPLE,
            Set.of("evasion-alt-abuse", "evasion", "alt-abuse")
    ),
    PROFILES_IDENTITY(
            "profiles-identity", "Profiles/Identity", "Usernames, skins, profiles, and impersonation",
            Material.NAME_TAG, NamedTextColor.LIGHT_PURPLE,
            Set.of("profiles-identity", "profiles", "profile", "identity")
    ),
    REPORTS_EVIDENCE_COOPERATION(
            "reports-evidence-cooperation", "Reports/Evidence/Staff Cooperation",
            "Reports, evidence integrity, and required staff cooperation",
            Material.LECTERN, NamedTextColor.AQUA,
            Set.of("reports-evidence-cooperation", "reports", "evidence", "staff-cooperation", "staff")
    ),
    COMPLICITY_ASSISTANCE(
            "complicity-assistance", "Complicity & Assistance",
            "Encouraging, assisting, or knowingly benefiting from another player's prohibited conduct",
            Material.IRON_INGOT, NamedTextColor.GOLD,
            Set.of("complicity-assistance", "complicity", "assistance")
    ),
    ECONOMY_MARKET(
            "economy-market", "Economy/Market", "Market, trading, and economy abuse",
            Material.GOLD_INGOT, NamedTextColor.GREEN,
            Set.of("economy-market", "economy", "market", "trading")
    ),
    REPUTATION(
            "reputation", "Reputation", "Reputation manipulation and related abuse",
            Material.NETHER_STAR, NamedTextColor.GREEN,
            Set.of("reputation", "reputation-manipulation")
    ),
    POLICY_GAP(
            "policy-gap", "Policy Gap", "Conduct not safely covered by configured policy; review only",
            Material.MAP, NamedTextColor.YELLOW,
            Set.of("policy-gap", "unclassified")
    );

    private final String id;
    private final String title;
    private final String description;
    private final Material material;
    private final NamedTextColor color;
    private final Set<String> navigationIds;

    PolicyV2Category(
            String id,
            String title,
            String description,
            Material material,
            NamedTextColor color,
            Set<String> navigationIds
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.material = material;
        this.color = color;
        this.navigationIds = Set.copyOf(navigationIds);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    Material material() {
        return material;
    }

    NamedTextColor color() {
        return color;
    }

    boolean matchesNavigationId(String navigationId) {
        return navigationId != null && navigationIds.contains(navigationId);
    }

    public boolean reviewOnly() {
        return this == POLICY_GAP;
    }

    public static List<PolicyV2Category> ordered() {
        return List.of(values());
    }

    public static PolicyV2Category byId(String id) {
        return Arrays.stream(values()).filter(category -> category.id.equals(id)).findFirst().orElse(null);
    }
}
