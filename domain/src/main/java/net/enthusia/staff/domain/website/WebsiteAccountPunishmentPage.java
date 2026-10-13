package net.enthusia.staff.domain.website;

import java.util.List;
import java.util.Optional;

public record WebsiteAccountPunishmentPage(List<WebsiteAccountPunishment> punishments, Optional<String> nextCursor) {
    public WebsiteAccountPunishmentPage {
        punishments = List.copyOf(punishments);
        if (nextCursor == null) throw new IllegalArgumentException("Cursor option is required");
    }
}
