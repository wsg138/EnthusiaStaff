package net.enthusia.staff.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rosewood.rosechat.api.staff.RoseChatStaffService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.enthusia.staff.domain.auth.DefaultAuthorizationPolicy;
import net.enthusia.staff.domain.evidence.IntegrationAvailability;
import net.enthusia.staff.paper.client.ClientEvidenceCollector;
import net.enthusia.staff.paper.economy.EnthusiaCurrencyGateway;
import net.enthusia.staff.paper.integration.MarketIntegration;
import net.enthusia.staff.paper.integration.ReputationIntegration;
import net.enthusia.staff.protocol.BackendVerificationReport;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;

final class PaperVerificationReporter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private PaperVerificationReporter() {
    }

    static String payload(String backendId) {
        Plugin raw = Bukkit.getPluginManager().getPlugin("EnthusiaStaff");
        if (!(raw instanceof EnthusiaStaffPaperPlugin staff)) {
            return serialize(failedReport(backendId, "EnthusiaStaff Paper runtime is unavailable"));
        }
        BackendVerificationReport report = new BackendVerificationReport(
                backendId,
                staff.operationalMode().name(),
                staff.moderationStore() != null,
                true,
                integrationChecks(staff),
                Map.of()
        );
        return serialize(report);
    }

    private static Map<String, BackendVerificationReport.Check> integrationChecks(
            EnthusiaStaffPaperPlugin staff
    ) {
        Map<String, BackendVerificationReport.Check> checks = new LinkedHashMap<>();
        PluginManager plugins = staff.getServer().getPluginManager();
        ServicesManager services = staff.getServer().getServicesManager();
        checks.put("Currency", currency(plugins, services));
        checks.put("Market", market(plugins, services));
        checks.put("Reputation", reputation(plugins, services));
        checks.put("RoseChat", roseChat(plugins, services));
        appendClientChecks(checks, staff);
        appendOptionalSummary(checks, plugins);
        return Map.copyOf(checks);
    }

    private static BackendVerificationReport.Check currency(
            PluginManager plugins,
            ServicesManager services
    ) {
        if (!plugins.isPluginEnabled("EnthusiaCurrency")) {
            return disabled("not installed or enabled");
        }
        EnthusiaCurrencyGateway.Discovery discovery = EnthusiaCurrencyGateway.discover(services);
        return discovery.gateway().isPresent() ? pass("moderation API available") : warning(discovery.issue());
    }

    private static BackendVerificationReport.Check market(
            PluginManager plugins,
            ServicesManager services
    ) {
        boolean enabled = plugins.isPluginEnabled("EnthusiaMarket");
        MarketIntegration integration = MarketIntegration.discover(services, enabled);
        return availability(integration.availability(), integration.issue(), "moderation API v1 available");
    }

    private static BackendVerificationReport.Check reputation(
            PluginManager plugins,
            ServicesManager services
    ) {
        boolean enabled = plugins.isPluginEnabled("EnthusiaCommend");
        ReputationIntegration integration = ReputationIntegration.discover(
                services,
                enabled,
                new DefaultAuthorizationPolicy()
        );
        return availability(integration.availability(), integration.issue(), "moderation API available");
    }

    private static BackendVerificationReport.Check roseChat(
            PluginManager plugins,
            ServicesManager services
    ) {
        if (!plugins.isPluginEnabled("RoseChat")) {
            return disabled("not installed or enabled");
        }
        try {
            RoseChatStaffService service = services.load(RoseChatStaffService.class);
            if (service == null) {
                return warning("staff API service is not registered");
            }
            if (service.apiVersion() != RoseChatStaffService.API_VERSION) {
                return warning("staff API version is incompatible");
            }
            String owner = service.getBridgeOwner().orElse("");
            if (!"EnthusiaStaff".equals(owner)) {
                return warning(owner.isBlank() ? "Staff moderation bridge is not installed" : "bridge owned by " + owner);
            }
            return pass("staff API and EnthusiaStaff bridge available");
        } catch (LinkageError | RuntimeException exception) {
            return warning("staff API link failed: " + exception.getClass().getSimpleName());
        }
    }

    private static void appendClientChecks(
            Map<String, BackendVerificationReport.Check> checks,
            EnthusiaStaffPaperPlugin staff
    ) {
        ClientEvidenceCollector collector = ClientEvidenceCollector.discover(staff, Clock.systemUTC());
        Map<String, String> issues = collector.issues();
        for (String name : new String[]{"ViaVersion", "Floodgate", "Geyser", "Enthusia AutoClicker", "Polar"}) {
            String issue = issues.get(name);
            checks.put(name, issue == null ? pass("integration available") : issueCheck(issue));
        }
    }

    private static void appendOptionalSummary(
            Map<String, BackendVerificationReport.Check> checks,
            PluginManager plugins
    ) {
        String[] names = {
                "voicechat", "CombatLogX", "ProtocolLib", "DiscordSRV", "LuckPerms",
                "EnthusiaTeleport", "EnthusiaPlaytime", "InventoryRollbackPlus"
        };
        int enabled = 0;
        for (String name : names) {
            if (plugins.isPluginEnabled(name)) {
                enabled++;
            }
        }
        checks.put("Other optional integrations", pass(enabled + "/" + names.length + " enabled"));
    }

    private static BackendVerificationReport.Check availability(
            IntegrationAvailability availability,
            String issue,
            String passDetail
    ) {
        return switch (availability) {
            case AVAILABLE -> pass(passDetail);
            case NOT_INSTALLED -> disabled(issue);
            case INCOMPATIBLE, UNAVAILABLE -> warning(issue);
        };
    }

    private static BackendVerificationReport.Check issueCheck(String issue) {
        return issue.startsWith("NOT_INSTALLED") ? disabled(issue) : warning(issue);
    }

    private static BackendVerificationReport.Check pass(String detail) {
        return new BackendVerificationReport.Check(BackendVerificationReport.State.PASS, detail);
    }

    private static BackendVerificationReport.Check warning(String detail) {
        return new BackendVerificationReport.Check(BackendVerificationReport.State.WARNING, detail);
    }

    private static BackendVerificationReport.Check disabled(String detail) {
        return new BackendVerificationReport.Check(BackendVerificationReport.State.DISABLED, detail);
    }

    private static BackendVerificationReport failedReport(String backendId, String reason) {
        return new BackendVerificationReport(
                backendId,
                "DEGRADED",
                false,
                true,
                Map.of(),
                Map.of("runtime", reason)
        );
    }

    private static String serialize(BackendVerificationReport report) {
        try {
            return JSON.writeValueAsString(report);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize backend verification report", exception);
        }
    }
}
