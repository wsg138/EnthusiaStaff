package net.enthusia.staff.domain.policyv2.legacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.enthusia.staff.domain.policyv2.BehavioralHistoryEntry;

/**
 * Owner-approved compatibility mapping from Policy v1 reason IDs into the
 * canonical Policy v2 behavioral taxonomy.
 *
 * <p>The mapping is deliberately conservative. A legacy reason that does not
 * prove the factual distinction required by Policy v2 is explicitly skipped
 * instead of being guessed into adverse behavioral history.</p>
 */
public final class PolicyV1HistoryCarryForward {
    private static final Map<String, Decision> DECISIONS = decisions();

    public Optional<BehavioralHistoryEntry> convert(
            PolicyV1BehavioralHistorySource.LegacyFinding legacy
    ) {
        if (legacy == null) {
            throw new IllegalArgumentException("legacy finding must be present");
        }
        Decision decision = DECISIONS.get(legacy.exactReasonId());
        if (decision == null || decision.v2OffenseId().isEmpty()) {
            return Optional.empty();
        }
        String offenseId = decision.v2OffenseId().orElseThrow();
        return Optional.of(new BehavioralHistoryEntry(
                "v1:" + legacy.caseId(),
                legacy.occurredAt(),
                offenseId,
                offenseId,
                BehavioralHistoryEntry.FindingState.CONFIRMED
        ));
    }

    public List<BehavioralHistoryEntry> convertAll(
            List<PolicyV1BehavioralHistorySource.LegacyFinding> legacy
    ) {
        if (legacy == null) {
            throw new IllegalArgumentException("legacy history must be present");
        }
        return legacy.stream()
                .map(this::convert)
                .flatMap(Optional::stream)
                .sorted(java.util.Comparator.comparing(BehavioralHistoryEntry::occurredAt)
                        .thenComparing(BehavioralHistoryEntry::caseId))
                .toList();
    }

    public Optional<Decision> decision(String legacyReasonId) {
        if (legacyReasonId == null || legacyReasonId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DECISIONS.get(legacyReasonId.trim()));
    }

    public Map<String, Decision> decisions() {
        return DECISIONS;
    }

    public record Decision(
            String legacyReasonId,
            Optional<String> v2OffenseId,
            String rationale
    ) {
        public Decision {
            if (legacyReasonId == null || legacyReasonId.isBlank()
                    || v2OffenseId == null || rationale == null || rationale.isBlank()) {
                throw new IllegalArgumentException("legacy mapping decision is invalid");
            }
            legacyReasonId = legacyReasonId.trim();
            v2OffenseId = v2OffenseId.map(String::trim).filter(value -> !value.isEmpty());
            rationale = rationale.trim();
        }

        public boolean mapped() {
            return v2OffenseId.isPresent();
        }
    }

    private static Map<String, Decision> decisions() {
        Map<String, Decision> values = new LinkedHashMap<>();

        map(values, "hate.general-toxicity", "abuse.general-toxicity");
        map(values, "hate.targeted-harassment", "abuse.targeted-harassment");
        map(values, "hate.discriminatory-statement", "hate.discriminatory-expression");
        map(values, "hate.abbreviated-slur-untargeted", "hate.slur");
        map(values, "hate.abbreviated-slur-targeted", "hate.slur");
        map(values, "hate.full-slur-untargeted", "hate.slur");
        map(values, "hate.full-slur-targeted", "hate.slur");
        map(values, "hate.extremist-symbol", "hate.extremist-symbol");
        map(values, "hate.advocating-hate-violence", "hate.extremist-advocacy");
        map(values, "harassment.sexual", "abuse.sexual-harassment");

        map(values, "safety.encouraging-self-harm", "safety.encouraging-self-harm");
        map(values, "privacy.personal-information-careless", "privacy.personal-information-exposure");
        map(values, "safety.credible-threat", "safety.credible-threat");
        map(values, "privacy.doxxing", "privacy.doxxing");
        map(values, "safety.blackmail-extortion", "safety.blackmail-extortion");
        map(values, "safety.grooming", "safety.grooming");
        map(values, "safety.illegal-exploitative-content", "safety.illegal-exploitative-content");

        map(values, "spam.low-level", "chat.spam.low-level");
        map(values, "spam.message-flooding", "chat.spam.flooding");
        map(values, "spam.private-message", "chat.spam.private-message");
        map(values, "spam.noise-pollution", "chat.disruption.noise");
        map(values, "language.non-english-public-chat", "chat.language.non-english-public");
        map(values, "spam.begging", "chat.begging");

        map(values, "content.moderate-inappropriate", "content.inappropriate");
        map(values, "content.extreme-inappropriate", "content.inappropriate");
        map(values, "content.explicit-sexual", "content.explicit-sexual");
        skip(values, "identity.inappropriate-profile",
                "Legacy reason does not identify username, skin, or another correctable profile surface.");

        map(values, "advertising.minecraft-server", "advertising.minecraft-server");
        map(values, "advertising.unrelated-self-promotion", "advertising.unrelated-self-promotion");
        map(values, "identity.staff-impersonation", "identity.staff-impersonation");
        map(values, "advertising.malicious-link", "security.malicious-link-file");

        map(values, "politics.casual", "chat.sensitive-topic-public");
        map(values, "politics.moderate", "chat.sensitive-topic-public");
        map(values, "politics.extreme", "chat.sensitive-topic-public",
                "Legacy severity does not prove extremist advocacy; carry forward conservatively as sensitive-topic history.");

        skip(values, "account.unapproved-vpn",
                "Policy v2 treats first VPN detection as a compliance condition rather than adverse behavioral history.");
        map(values, "account.sharing", "account.sharing");
        map(values, "account.headless-client", "account.headless-client");
        skip(values, "account.unsafe-download",
                "Legacy reason does not prove intentional malware, phishing, or credential theft.");
        map(values, "account.theft", "account.theft");

        map(values, "complicity.encouraging-rule-breaking", "complicity.encouraging-rule-breaking");
        map(values, "complicity.encouraging-cheating", "complicity.encouraging-cheating");
        map(values, "complicity.assisting-cheating", "complicity.assisting-cheating");
        map(values, "complicity.laundering-duplicated-items", "complicity.laundering-duplicated-items");

        map(values, "evasion.harboring-ban-evader", "evasion.assisting-other");
        map(values, "dishonesty.falsifying-evidence", "integrity.evidence-falsification");
        map(values, "dishonesty.lying-investigation", "integrity.investigation-dishonesty");
        map(values, "staff.refusing-instruction", "staff.instruction-refusal");
        map(values, "evasion.mute", "evasion.mute");
        map(values, "evasion.ban", "evasion.ban");
        map(values, "evasion.network-identity-ban", "evasion.ban");
        map(values, "evasion.helping-another", "evasion.assisting-other");

        map(values, "exploit.illegal-duplication", "exploit.prohibited-duplication");
        skip(values, "exploit.duplicated-possession-unclear",
                "Owner policy makes knowledge-unproven duplicated possession remedy-only.");
        map(values, "exploit.duplicated-possession-knowing", "exploit.duplicated-item-possession-knowing");
        map(values, "exploit.withholding", "exploit.bug-withholding");
        map(values, "exploit.minor-abuse", "exploit.bug-abuse");
        map(values, "exploit.major-abuse", "exploit.bug-abuse");
        map(values, "mechanics.chunk-loader", "disruption.chunk-loading");
        map(values, "mechanics.laggy-farm", "disruption.laggy-build");
        map(values, "mechanics.end-portal-obstruction", "disruption.end-portal-obstruction");
        map(values, "exploit.server-crash-attempt", "disruption.server-crash-attempt");

        skip(values, "market.compliance-failure",
                "Retired generic selector does not identify the underlying Market conduct.");
        map(values, "market.extra-stall-alt", "market.extra-stall-alt");
        map(values, "market.blacklist-evasion", "evasion.restriction");
        map(values, "reputation.false", "reputation.false");
        map(values, "reputation.coordinated", "reputation.coordinated");
        map(values, "reputation.alt-manipulation", "reputation.alt-manipulation");
        map(values, "reputation.blacklist-evasion", "evasion.restriction");

        map(values, "cheating.polar.template", "cheating.hacked-client",
                "Polar is a legacy detection source, not a distinct behavioral family.");
        map(values, "cheating.manual-client", "cheating.hacked-client");
        map(values, "cheating.xray-esp", "cheating.xray-esp");
        map(values, "cheating.freecam", "cheating.freecam");
        map(values, "cheating.baritone", "cheating.pathfinding-automation");
        map(values, "cheating.easy-place-printer", "cheating.build-automation");
        map(values, "cheating.pvp-indicator", "cheating.pvp-indicator");
        map(values, "cheating.minimap-display", "cheating.minimap-display");
        map(values, "cheating.unauthorized-autoclicker-mod", "cheating.autoclicker-unauthorized");
        map(values, "cheating.combat-autoclicker", "cheating.autoclicker-combat");
        map(values, "cheating.autoclicker-bypass", "cheating.autoclicker-bypass");
        map(values, "cheating.other-modification", "cheating.other-unfair-modification");

        map(values, "staff.public-punishment-argument", "chat.public-case-argument");
        map(values, "reports.spam", "reports.spam");
        map(values, "reports.false", "reports.false");
        skip(values, "reports.abusive-content",
                "Legacy reason records the reporting surface but not the underlying abusive conduct.");
        map(values, "reports.harassing-staff", "abuse.targeted-harassment");

        return Collections.unmodifiableMap(values);
    }

    private static void map(Map<String, Decision> values, String legacy, String target) {
        map(values, legacy, target, "Unambiguous factual Policy v1 to Policy v2 mapping.");
    }

    private static void map(
            Map<String, Decision> values,
            String legacy,
            String target,
            String rationale
    ) {
        put(values, new Decision(legacy, Optional.of(target), rationale));
    }

    private static void skip(Map<String, Decision> values, String legacy, String rationale) {
        put(values, new Decision(legacy, Optional.empty(), rationale));
    }

    private static void put(Map<String, Decision> values, Decision decision) {
        if (values.putIfAbsent(decision.legacyReasonId(), decision) != null) {
            throw new IllegalStateException("duplicate Policy v1 history mapping for " + decision.legacyReasonId());
        }
    }
}
