package net.enthusia.staff.domain.website;

import java.util.List;
import java.util.Optional;

public record WebsiteAppealPage(
        List<WebsiteAppealView> items,
        Optional<String> nextCursor
) {
    public WebsiteAppealPage {
        if (items == null || nextCursor == null) {
            throw new IllegalArgumentException("Website appeal page fields are invalid");
        }
        items = List.copyOf(items);
    }
}
