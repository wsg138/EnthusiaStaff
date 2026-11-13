package net.enthusia.staff.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.escalation.DecayEligibility;
import net.enthusia.staff.domain.escalation.EscalationDecision;
import net.enthusia.staff.domain.escalation.EscalationEngine;
import net.enthusia.staff.domain.escalation.PriorOffense;
import net.enthusia.staff.domain.escalation.PunishmentStep;
import net.enthusia.staff.domain.escalation.ReasonPolicy;
import net.enthusia.staff.domain.sanction.SanctionSpec;
import net.enthusia.staff.paper.config.ReasonPolicyConfigurationLoader;

/** In-memory what-if evaluation using the same validated policy loader and engine as Paper.
 * No Minecraft server, database, permission changes, or punishment dispatch is possible. */
public final class SimulatorModel {
    private final String version;
    private final String source;
    private final List<ReasonPolicy> policies;
    private final Map<String, ReasonPolicy> byId;
    private final EscalationEngine engine = new EscalationEngine();

    public SimulatorModel(Path snapshot) {
        var loaded = new ReasonPolicyConfigurationLoader().load(snapshot);
        version = loaded.version();
        source = snapshot.toAbsolutePath().toString();
        policies = loaded.policies();
        byId = policies.stream().collect(Collectors.toUnmodifiableMap(ReasonPolicy::id, Function.identity()));
    }

    public Map<String, Object> catalog() {
        return Map.of("version", version, "source", source,
                "count", policies.size(), "reasons", policies.stream().map(this::reason).toList());
    }

    public Map<String, Object> evaluate(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        ReasonPolicy policy = require(request.path("reasonId").asText(""));
        LocalDate date = LocalDate.parse(request.path("asOf").asText(""));
        Instant now = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
        JsonNode items = request.path("history");
        if (!items.isArray() || items.size() > 150) {
            throw new IllegalArgumentException("History must be an array of at most 150 items");
        }
        List<PriorOffense> history = new ArrayList<>();
        List<HistoryRow> rows = new ArrayList<>();
        for (JsonNode input : items) {
            if (!input.isObject()) throw new IllegalArgumentException("Invalid history item");
            ReasonPolicy prior = require(input.path("reasonId").asText(""));
            JsonNode days = input.path("daysAgo");
            if (!days.isIntegralNumber() || days.asInt(-1) < 0 || days.asInt() > 36500) {
                throw new IllegalArgumentException("Days since a previous punishment ended must be 0–36500");
            }
            int daysAgo = days.asInt();
            boolean contributes = booleanField(input, "contributes", true);
            boolean overturned = booleanField(input, "overturned", false);
            DecayEligibility decay = prior.decayEnabled()
                    ? DecayEligibility.ELIGIBLE : DecayEligibility.INELIGIBLE;
            var offense = new PriorOffense(prior.family(), prior.severity(), 0,
                    now.minus(Duration.ofDays(daysAgo)), contributes, overturned, decay);
            history.add(offense);
            rows.add(new HistoryRow(prior, offense, daysAgo));
        }
        EscalationDecision decision = engine.decide(policy, history, now);
        List<HistoryRow> included = rows.stream()
                .filter(r -> r.offense.contributes() && !r.offense.overturned()
                        && r.offense.family().equals(policy.family()))
                .sorted(Comparator.comparing(r -> r.offense.endedAt()))
                .toList();
        List<Map<String, Object>> contributions = new ArrayList<>();
        for (int i = 0; i < included.size(); i++) {
            HistoryRow row = included.get(i);
            var value = decision.contributions().get(i);
            contributions.add(Map.of(
                    "reason", row.policy.publicReason(), "reasonId", row.policy.id(),
                    "daysAgo", row.daysAgo, "base", value.base(),
                    "decayedBy", value.decayedBy(), "effective", value.effective(),
                    "decayEligibility", value.decayEligibility().name(),
                    "severity", value.priorSeverity()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", version);
        result.put("reason", reason(policy));
        result.put("asOf", date.toString());
        result.put("rawOrdinal", decision.rawOrdinal());
        result.put("effectiveOrdinal", decision.effectiveOrdinal());
        result.put("recencyBonus", decision.recencyBonus());
        result.put("selected", step(decision.selectedStep()));
        result.put("contributions", contributions);
        result.put("ignoredCount", rows.size() - included.size());
        result.put("historyCount", rows.size());
        result.put("source", source);
        return result;
    }

    private ReasonPolicy require(String id) {
        ReasonPolicy policy = byId.get(id);
        if (policy == null) throw new IllegalArgumentException("Unknown reason ID");
        return policy;
    }

    private static boolean booleanField(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        if (value == null) return fallback;
        if (!value.isBoolean()) throw new IllegalArgumentException(field + " must be true or false");
        return value.asBoolean();
    }

    private Map<String, Object> reason(ReasonPolicy policy) {
        return Map.of("id", policy.id(), "family", policy.family(),
                "name", policy.publicReason(), "severity", policy.severity(),
                "decayEnabled", policy.decayEnabled(), "requiredRank", policy.requiredRank().name(),
                "examples", policy.examples(), "steps",
                policy.steps().stream().map(SimulatorModel::step).toList());
    }

    private static Map<String, Object> step(PunishmentStep step) {
        return Map.of("ordinal", step.ordinal(), "label", step.label(), "sanctions",
                step.sanctions().stream().map(SimulatorModel::sanction).toList());
    }

    private static Map<String, Object> sanction(SanctionSpec spec) {
        String duration = switch (spec.length().kind()) {
            case INSTANT -> "instant";
            case PERMANENT -> "permanent";
            case TEMPORARY -> {
                long seconds = spec.length().temporary().orElseThrow().toSeconds();
                yield seconds % 86400 == 0 ? (seconds / 86400) + "d"
                        : seconds % 3600 == 0 ? (seconds / 3600) + "h" : seconds + "s";
            }
        };
        return Map.of("type", spec.type().name(), "duration", duration);
    }

    private record HistoryRow(ReasonPolicy policy, PriorOffense offense, int daysAgo) {
    }
}
