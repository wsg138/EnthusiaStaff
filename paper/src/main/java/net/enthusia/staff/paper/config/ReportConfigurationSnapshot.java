package net.enthusia.staff.paper.config;

import java.util.Objects;
import net.enthusia.staff.domain.report.ReportPolicy;
import net.enthusia.staff.paper.report.ReportGuiConfiguration;

public record ReportConfigurationSnapshot(
        String policyVersion,
        String guiVersion,
        ReportPolicy policy,
        ReportGuiConfiguration gui
) {
    public ReportConfigurationSnapshot {
        policyVersion = requireText(policyVersion, "policyVersion");
        guiVersion = requireText(guiVersion, "guiVersion");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(gui, "gui");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
