package net.enthusia.staff.paper.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.enthusia.staff.domain.history.*;
import net.enthusia.staff.domain.player.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

class HistoryPaginationIdentityTest {
    @Test
    void historicalNameNextPageKeepsTheSelectedIdentityAfterRename() {
        UUID id = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(id, Optional.of("SharedHistoricalName"),
                PlayerPlatform.UNKNOWN, Instant.EPOCH, Instant.EPOCH);
        ModerationHistoryEntry entry = new ModerationHistoryEntry("case", HistoryEventType.CASE_CREATED,
                Instant.EPOCH, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "OPEN", "reason", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        var rendered = HistoryCommand.render(identity, PlayerResolution.MatchKind.HISTORICAL_USERNAME,
                new ModerationHistoryPage(id, 1, 1, 2, 2, List.of(entry)), ZoneOffset.UTC, false, UUID::toString);
        assertTrue(rendered.stream().map(HistoryPaginationIdentityTest::text)
                .anyMatch(line -> line.equals("Next page  /history " + id + " 2")));
        assertTrue(rendered.stream().map(HistoryPaginationIdentityTest::text)
                .anyMatch(line -> line.contains("SharedHistoricalName")));
    }

    private static String text(Component component) {
        StringBuilder result = new StringBuilder(component instanceof TextComponent value ? value.content() : "");
        component.children().forEach(child -> result.append(text(child)));
        return result.toString();
    }
}
