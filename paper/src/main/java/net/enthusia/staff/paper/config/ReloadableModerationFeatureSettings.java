package net.enthusia.staff.paper.config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ReloadableModerationFeatureSettings {
    private final AtomicReference<ModerationFeatureSettings> active;

    public ReloadableModerationFeatureSettings(ModerationFeatureSettings initial) {
        active = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
    }

    public ModerationFeatureSettings current() {
        return active.get();
    }

    public void reloadFrom(ModerationFeatureSettings validated) {
        active.set(Objects.requireNonNull(validated, "validated"));
    }
}
