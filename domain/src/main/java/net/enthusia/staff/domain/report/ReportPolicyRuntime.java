package net.enthusia.staff.domain.report;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class ReportPolicyRuntime {
    private static final AtomicReference<Supplier<ReportPolicy>> PROVIDER =
            new AtomicReference<>(ReportPolicy::defaults);

    private ReportPolicyRuntime() {
    }

    public static ReportPolicy current() {
        return Objects.requireNonNull(PROVIDER.get().get(), "active report policy");
    }

    public static void install(Supplier<ReportPolicy> provider) {
        PROVIDER.set(Objects.requireNonNull(provider, "provider"));
    }
}
