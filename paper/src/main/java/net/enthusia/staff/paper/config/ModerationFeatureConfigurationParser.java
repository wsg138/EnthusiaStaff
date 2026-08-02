package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;

final class ModerationFeatureConfigurationParser {
    ModerationFeatureSettings parse(JsonNode root, List<String> errors) {
        ModerationFeatureSettings defaults = ModerationFeatureSettings.defaults();
        JsonNode history = ConfigurationNodes.optionalMapping(root, "history", "history", errors);
        JsonNode actions = ConfigurationNodes.optionalMapping(
                root,
                "sanction-actions",
                "sanction-actions",
                errors
        );
        ConfigurationNodes.rejectUnknown(
                history,
                Set.of("page-size", "include-request-events", "include-appeal-events", "timezone"),
                "history",
                errors
        );
        ConfigurationNodes.rejectUnknown(
                actions,
                Set.of("minimum-reason-length", "maximum-reason-length", "allow-permanent-reduction"),
                "sanction-actions",
                errors
        );

        int pageSize = ConfigurationNodes.boundedInteger(
                history,
                "page-size",
                "history.page-size",
                defaults.historyPageSize(),
                1,
                100,
                errors
        );
        boolean includeRequests = ConfigurationNodes.bool(
                history,
                "include-request-events",
                "history.include-request-events",
                defaults.includeRequestEvents(),
                errors
        );
        boolean includeAppeals = ConfigurationNodes.bool(
                history,
                "include-appeal-events",
                "history.include-appeal-events",
                defaults.includeAppealEvents(),
                errors
        );
        String timezoneName = ConfigurationNodes.text(
                history,
                "timezone",
                "history.timezone",
                defaults.historyTimezone().getId(),
                errors
        );
        ZoneId timezone = defaults.historyTimezone();
        try {
            timezone = ZoneId.of(timezoneName);
        } catch (DateTimeException exception) {
            errors.add("history.timezone must be a valid IANA timezone such as UTC or America/Chicago");
        }

        int minimum = ConfigurationNodes.boundedInteger(
                actions,
                "minimum-reason-length",
                "sanction-actions.minimum-reason-length",
                defaults.sanctionActionLimits().minimumReasonLength(),
                1,
                512,
                errors
        );
        int maximum = ConfigurationNodes.boundedInteger(
                actions,
                "maximum-reason-length",
                "sanction-actions.maximum-reason-length",
                defaults.sanctionActionLimits().maximumReasonLength(),
                1,
                512,
                errors
        );
        if (maximum < minimum) {
            errors.add("sanction-actions.maximum-reason-length must be at least the minimum");
            maximum = Math.max(minimum, defaults.sanctionActionLimits().maximumReasonLength());
        }
        boolean allowPermanentReduction = ConfigurationNodes.bool(
                actions,
                "allow-permanent-reduction",
                "sanction-actions.allow-permanent-reduction",
                defaults.sanctionActionLimits().allowPermanentReduction(),
                errors
        );
        return new ModerationFeatureSettings(
                pageSize,
                includeRequests,
                includeAppeals,
                timezone,
                new SanctionActionLimits(minimum, maximum, allowPermanentReduction)
        );
    }
}
