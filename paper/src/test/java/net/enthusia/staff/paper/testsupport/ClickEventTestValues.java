package net.enthusia.staff.paper.testsupport;

import java.util.Objects;
import net.kyori.adventure.text.event.ClickEvent;

public final class ClickEventTestValues {
    private ClickEventTestValues() { }

    @SuppressWarnings("deprecation")
    public static String textValue(ClickEvent event) {
        return Objects.requireNonNull(event, "event").value();
    }
}
