package net.enthusia.staff.paper.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class AtomicReportConfiguration {
    private final AtomicReference<ReportConfigurationSnapshot> active;

    public AtomicReportConfiguration(ReportConfigurationSnapshot initial) {
        active = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public ReportConfigurationSnapshot snapshot() {
        return active.get();
    }

    public boolean replace(ReportConfigurationSnapshot expected, ReportConfigurationSnapshot candidate) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(candidate, "candidate");
        return active.compareAndSet(expected, candidate);
    }
}
