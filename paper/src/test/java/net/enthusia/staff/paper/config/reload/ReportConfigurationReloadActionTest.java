package net.enthusia.staff.paper.config.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.paper.config.AtomicReportConfiguration;
import net.enthusia.staff.paper.config.ConfigurationValidationException;
import net.enthusia.staff.paper.config.ReportConfigurationLoader;
import net.enthusia.staff.paper.config.ReportConfigurationSnapshot;
import org.junit.jupiter.api.Test;

class ReportConfigurationReloadActionTest {
    @Test
    void invalidCandidateLeavesPreviousSettingsAndDelegateUntouched() {
        ReportConfigurationSnapshot initial = defaults();
        AtomicReportConfiguration active = new AtomicReportConfiguration(initial);
        AtomicBoolean delegated = new AtomicBoolean();
        ReportConfigurationReloadAction action = new ReportConfigurationReloadAction(
                () -> {
                    delegated.set(true);
                    return unchanged();
                },
                () -> {
                    throw new ConfigurationValidationException("gui/reports.yml.slots overlap");
                },
                active,
                ignored -> { }
        );

        ConfigurationReloadResult result = action.reload();

        assertEquals(ConfigurationReloadResult.Outcome.VALIDATION_FAILED, result.outcome());
        assertFalse(delegated.get());
        assertSame(initial, active.snapshot());
    }

    @Test
    void successfulDelegatePublishesCandidateAtomically() {
        ReportConfigurationSnapshot initial = defaults();
        ReportPolicy original = initial.policy();
        ReportConfigurationSnapshot candidate = new ReportConfigurationSnapshot(
                "candidate",
                initial.guiVersion(),
                new ReportPolicy(
                        original.anyCooldown(),
                        original.targetCooldown(),
                        original.duplicateWindow(),
                        7,
                        original.queryLimit(),
                        original.recentlyClosedWindow(),
                        original.evidenceRetention(),
                        original.evidencePurgeBatchLimit()
                ),
                initial.gui()
        );
        AtomicReportConfiguration active = new AtomicReportConfiguration(initial);
        ReportConfigurationReloadAction action = new ReportConfigurationReloadAction(
                ReportConfigurationReloadActionTest::unchanged,
                () -> candidate,
                active,
                ignored -> { }
        );

        ConfigurationReloadResult result = action.reload();

        assertTrue(result.successful());
        assertEquals(ConfigurationReloadResult.Outcome.APPLIED, result.outcome());
        assertSame(candidate, active.snapshot());
    }

    private static ConfigurationReloadResult unchanged() {
        return new ConfigurationReloadResult(
                ConfigurationReloadResult.Outcome.NO_CHANGES,
                "No reloadable settings changed",
                List.of(),
                false
        );
    }

    private static ReportConfigurationSnapshot defaults() {
        try (InputStream policy = resource("reports.yml");
             InputStream gui = resource("gui/reports.yml")) {
            return new ReportConfigurationLoader().load(policy, "reports.yml", gui, "gui/reports.yml");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("unable to close report configuration resources", exception);
        }
    }

    private static InputStream resource(String name) {
        InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IllegalStateException("missing test resource " + name);
        }
        return input;
    }
}
