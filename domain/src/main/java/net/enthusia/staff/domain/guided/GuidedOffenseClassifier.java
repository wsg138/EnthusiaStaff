package net.enthusia.staff.domain.guided;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Factual answers to the broad questionnaire map to existing v1 policy reasons. */
public final class GuidedOffenseClassifier {
    private GuidedOffenseClassifier() { }

    private static GuidedIncidentWizard.Choice c(String id, String label) {
        return new GuidedIncidentWizard.Choice(id, label);
    }

    private static final Map<String, List<GuidedIncidentWizard.Choice>> DETAILS = Map.ofEntries(
            Map.entry("chat/harassment", List.of(c("sexual", "Sexual harassment"))),
            Map.entry("chat/hate", List.of(c("discrimination", "Discriminatory statement"), c("symbol", "Extremist symbol"), c("advocacy", "Advocating hate violence"))),
            Map.entry("chat/politics", List.of(c("casual", "Casual mention"), c("moderate", "Disruptive political discussion"), c("extreme", "Severe political content"))),
            Map.entry("gameplay/cheating", List.of(c("xray", "X-ray ESP"), c("freecam", "Freecam"), c("baritone", "Baritone pathing"), c("printer", "Easy place printer"), c("autoclick", "Combat autoclicker"), c("client", "Unauthorized client"), c("other", "Other cheating"))),
            Map.entry("gameplay/exploit", List.of(c("duplication", "Illegal duplication"), c("unclear", "Possessed unexplained dupes"), c("knowingly", "Knowingly kept dupes"), c("withholding", "Withheld exploit knowledge"), c("minor", "Minor bug abuse"), c("major", "Major bug abuse"), c("crash", "Server crash attempt"))),
            Map.entry("gameplay/mechanics", List.of(c("loader", "Chunk loader"), c("farm", "Laggy farm"), c("portal", "Portal obstruction"))),
            Map.entry("gameplay/complicity", List.of(c("rule", "Encouraged rule-breaking"), c("cheat", "Encouraged cheating"), c("assisted", "Assisted cheater"), c("laundering", "Laundered duplicated items"))),
            Map.entry("safety/threat", List.of(c("credible", "Credible threat"), c("blackmail", "Blackmail extortion"), c("grooming", "Grooming"), c("self-harm", "Encouraged self-harm"), c("illegal", "Illegal exploitative content"), c("unknown", "Other uncertain threat"))),
            Map.entry("safety/privacy", List.of(c("careless", "Careless personal information"), c("doxxing", "Doxxing"), c("unknown", "Unclear privacy allegation"))),
            Map.entry("accounts/access", List.of(c("vpn", "Unapproved VPN"), c("sharing", "Account sharing"), c("headless", "Headless client"), c("download", "Unsafe download"), c("theft", "Account theft"))),
            Map.entry("accounts/evasion", List.of(c("mute", "Evaded mute"), c("ban", "Evaded ban"), c("network", "Network identity ban"), c("help", "Helped evader"), c("harbor", "Harbored evader"))),
            Map.entry("accounts/identity", List.of(c("staff", "Staff impersonation"), c("profile", "Inappropriate profile"))),
            Map.entry("community/advertising", List.of(c("server", "Another Minecraft server"), c("self", "Unrelated self promotion"), c("malicious", "Malicious link"))),
            Map.entry("community/reports", List.of(c("spam", "Report spam"), c("false", "False report"), c("abusive", "Abusive content"), c("harass", "Harassed staff"))),
            Map.entry("community/content", List.of(c("moderate", "Moderately inappropriate"), c("extreme", "Extremely inappropriate"), c("explicit", "Explicit sexual content"))),
            Map.entry("community/staff", List.of(c("refuse", "Refused staff instruction"), c("argument", "Public punishment argument"))),
            Map.entry("community/dishonesty", List.of(c("falsify", "Falsified evidence"), c("lie", "Lied in investigation"))),
            Map.entry("economy/market", List.of(c("compliance", "Market compliance"), c("extra-stall", "Extra alt stall"), c("blacklist", "Market blacklist evasion"))),
            Map.entry("economy/reputation", List.of(c("false", "False reputation"), c("coordinated", "Coordinated manipulation"), c("alt", "Alt manipulation"), c("blacklist", "Reputation blacklist evasion")))
    );
    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("chat/harassment/sexual", "harassment.sexual"),
            Map.entry("chat/hate/discrimination", "hate.discriminatory-statement"),
            Map.entry("chat/hate/symbol", "hate.extremist-symbol"),
            Map.entry("chat/hate/advocacy", "hate.advocating-hate-violence"),
            Map.entry("chat/politics/casual", "politics.casual"),
            Map.entry("chat/politics/moderate", "politics.moderate"),
            Map.entry("chat/politics/extreme", "politics.extreme"),
            Map.entry("gameplay/cheating/xray", "cheating.xray-esp"),
            Map.entry("gameplay/cheating/freecam", "cheating.freecam"),
            Map.entry("gameplay/cheating/baritone", "cheating.baritone"),
            Map.entry("gameplay/cheating/printer", "cheating.easy-place-printer"),
            Map.entry("gameplay/cheating/autoclick", "cheating.combat-autoclicker"),
            Map.entry("gameplay/cheating/client", "cheating.manual-client"),
            Map.entry("gameplay/exploit/duplication", "exploit.illegal-duplication"),
            Map.entry("gameplay/exploit/unclear", "exploit.duplicated-possession-unclear"),
            Map.entry("gameplay/exploit/knowingly", "exploit.duplicated-possession-knowing"),
            Map.entry("gameplay/exploit/withholding", "exploit.withholding"),
            Map.entry("gameplay/exploit/minor", "exploit.minor-abuse"),
            Map.entry("gameplay/exploit/major", "exploit.major-abuse"),
            Map.entry("gameplay/exploit/crash", "exploit.server-crash-attempt"),
            Map.entry("gameplay/mechanics/loader", "mechanics.chunk-loader"),
            Map.entry("gameplay/mechanics/farm", "mechanics.laggy-farm"),
            Map.entry("gameplay/mechanics/portal", "mechanics.end-portal-obstruction"),
            Map.entry("gameplay/complicity/rule", "complicity.encouraging-rule-breaking"),
            Map.entry("gameplay/complicity/cheat", "complicity.encouraging-cheating"),
            Map.entry("gameplay/complicity/assisted", "complicity.assisting-cheating"),
            Map.entry("gameplay/complicity/laundering", "complicity.laundering-duplicated-items"),
            Map.entry("safety/threat/credible", "safety.credible-threat"),
            Map.entry("safety/threat/blackmail", "safety.blackmail-extortion"),
            Map.entry("safety/threat/grooming", "safety.grooming"),
            Map.entry("safety/threat/self-harm", "safety.encouraging-self-harm"),
            Map.entry("safety/threat/illegal", "safety.illegal-exploitative-content"),
            Map.entry("safety/privacy/careless", "privacy.personal-information-careless"),
            Map.entry("safety/privacy/doxxing", "privacy.doxxing"),
            Map.entry("accounts/access/vpn", "account.unapproved-vpn"),
            Map.entry("accounts/access/sharing", "account.sharing"),
            Map.entry("accounts/access/headless", "account.headless-client"),
            Map.entry("accounts/access/download", "account.unsafe-download"),
            Map.entry("accounts/access/theft", "account.theft"),
            Map.entry("accounts/evasion/mute", "evasion.mute"),
            Map.entry("accounts/evasion/ban", "evasion.ban"),
            Map.entry("accounts/evasion/network", "evasion.network-identity-ban"),
            Map.entry("accounts/evasion/help", "evasion.helping-another"),
            Map.entry("accounts/evasion/harbor", "evasion.harboring-ban-evader"),
            Map.entry("accounts/identity/staff", "identity.staff-impersonation"),
            Map.entry("accounts/identity/profile", "identity.inappropriate-profile"),
            Map.entry("community/advertising/server", "advertising.minecraft-server"),
            Map.entry("community/advertising/self", "advertising.unrelated-self-promotion"),
            Map.entry("community/advertising/malicious", "advertising.malicious-link"),
            Map.entry("community/reports/spam", "reports.spam"),
            Map.entry("community/reports/false", "reports.false"),
            Map.entry("community/reports/abusive", "reports.abusive-content"),
            Map.entry("community/reports/harass", "reports.harassing-staff"),
            Map.entry("community/content/moderate", "content.moderate-inappropriate"),
            Map.entry("community/content/extreme", "content.extreme-inappropriate"),
            Map.entry("community/content/explicit", "content.explicit-sexual"),
            Map.entry("community/staff/refuse", "staff.refusing-instruction"),
            Map.entry("community/staff/argument", "staff.public-punishment-argument"),
            Map.entry("community/dishonesty/falsify", "dishonesty.falsifying-evidence"),
            Map.entry("community/dishonesty/lie", "dishonesty.lying-investigation"),
            Map.entry("economy/market/compliance", "market.compliance-failure"),
            Map.entry("economy/market/extra-stall", "market.extra-stall-alt"),
            Map.entry("economy/market/blacklist", "market.blacklist-evasion"),
            Map.entry("economy/reputation/false", "reputation.false"),
            Map.entry("economy/reputation/coordinated", "reputation.coordinated"),
            Map.entry("economy/reputation/alt", "reputation.alt-manipulation"),
            Map.entry("economy/reputation/blacklist", "reputation.blacklist-evasion")
    );

    public static List<GuidedIncidentWizard.Choice> details(String category, String conduct) {
        return DETAILS.getOrDefault(category + "/" + conduct, List.of());
    }

    public static Set<String> mappedReasonIds() { return Set.copyOf(REASONS.values()); }

    public static String resolve(String category, String medium, String conduct, String detail,
                                 String extent, String targeting) {
        if ("chat".equals(category)) {
            if ("spam".equals(conduct)) {
                return switch (medium) {
                    case "public-chat" -> "sustained".equals(extent) || "large-scale".equals(extent)
                            ? "spam.message-flooding" : "spam.low-level";
                    case "direct-message" -> "spam.private-message";
                    case "voice" -> "spam.noise-pollution";
                    default -> null;
                };
            }
            if ("harassment".equals(conduct) && "verbal".equals(detail)) {
                return "targeted".equals(targeting)
                        ? "hate.targeted-harassment" : "hate.general-toxicity";
            }
            if ("slur".equals(conduct)) {
                return switch (detail) {
                    case "abbreviated" -> "targeted".equals(targeting)
                            ? "hate.abbreviated-slur-targeted" : "hate.abbreviated-slur-untargeted";
                    case "full" -> "targeted".equals(targeting)
                            ? "hate.full-slur-targeted" : "hate.full-slur-untargeted";
                    default -> null;
                };
            }
            if ("language".equals(conduct) && "public-chat".equals(medium)) {
                return "language.non-english-public-chat";
            }
        }
        String prefix = category + "/" + conduct;
        return REASONS.get(detail == null ? prefix : prefix + "/" + detail);
    }
}
