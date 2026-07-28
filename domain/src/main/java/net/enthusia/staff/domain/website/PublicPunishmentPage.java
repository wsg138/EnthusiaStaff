package net.enthusia.staff.domain.website;

import java.util.List;
import java.util.Optional;

public record PublicPunishmentPage(List<PublicPunishment> items, Optional<String> nextCursor) {
    public PublicPunishmentPage {
        items = List.copyOf(items);
        nextCursor = nextCursor == null ? Optional.empty() : nextCursor;
    }
}
