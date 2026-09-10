package net.enthusia.staff.discordbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModerationReadApiModelTest {
    @Test
    void aroundCursorIsAllowlistedIndependently() {
        var query = new ModerationReadApiModel.MessageQuery(
                Optional.of("1541286004298752091"),
                Optional.empty(),
                Optional.empty(),
                Optional.of("1541300000000000001"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                50);

        assertEquals(Optional.of("1541300000000000001"), query.aroundMessageId());
        assertEquals(50, query.limit());
    }

    @Test
    void conflictingMessageCursorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ModerationReadApiModel.MessageQuery(
                Optional.of("1541286004298752091"),
                Optional.of("1541300000000000001"),
                Optional.empty(),
                Optional.of("1541300000000000002"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                50));
    }
}
