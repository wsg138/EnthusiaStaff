package net.enthusia.staff.paper.config;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.domain.sanction.SanctionActionLimits;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReloadableModerationFeatureSettings {
    private final AtomicReference<ModerationFeatureSettings> active;

    public ReloadableModerationFeatureSettings(ModerationFeatureSettings initial) {
        active = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public ModerationFeatureSettings current() {
        return active.get();
    }

    public void reloadFrom(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.reloadConfig();
        active.set(read(plugin.getConfig()));
    }

    public static ModerationFeatureSettings read(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        ModerationFeatureSettings defaults = ModerationFeatureSettings.defaults();
        int pageSize = configuration.getInt("history.page-size", defaults.historyPageSize());
        boolean includeRequests = configuration.getBoolean(
                "history.include-request-events",
                defaults.includeRequestEvents()
        );
        boolean includeAppeals = configuration.getBoolean(
                "history.include-appeal-events",
                defaults.includeAppealEvents()
        );
        String timezoneName = configuration.getString(
                "history.timezone",
                defaults.historyTimezone().getId()
        );
        ZoneId timezone;
        try {
            timezone = ZoneId.of(Objects.requireNonNull(timezoneName, "timezoneName"));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("history.timezone is invalid", exception);
        }
        int minimum = configuration.getInt(
                "sanction-actions.minimum-reason-length",
                defaults.sanctionActionLimits().minimumReasonLength()
        );
        int maximum = configuration.getInt(
                "sanction-actions.maximum-reason-length",
                defaults.sanctionActionLimits().maximumReasonLength()
        );
        boolean allowPermanentReduction = configuration.getBoolean(
                "sanction-actions.allow-permanent-reduction",
                defaults.sanctionActionLimits().allowPermanentReduction()
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
