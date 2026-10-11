package net.enthusia.staff.paper.testsupport;

import java.util.Objects;
import net.kyori.adventure.text.event.ClickEvent;

/** Accesses only text click payloads, using the Adventure 5 API. */
public final class ClickEventTestValues {
    private ClickEventTestValues() { }

    public static String textValue(ClickEvent<?> event) {
        Object payload = Objects.requireNonNull(event, "event").payload();
        if (!(payload instanceof ClickEvent.Payload.Text text)) {
            throw new IllegalArgumentException("Click event does not have a text payload");
        }
        return text.value();
    }
}
