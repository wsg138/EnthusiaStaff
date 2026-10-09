package net.enthusia.staff.paper.aireview;

import java.util.ArrayList;
import java.util.List;

/** Pure, bounded keyset cursor navigation; the API owns ordering. */
final class AiReviewHistoryNavigation {
    static final int MAX_PREVIOUS_PAGES = 50;

    private AiReviewHistoryNavigation() {
    }

    static Page previous(AiReviewGuiState.History history) {
        List<String> stack = history.previousCursors();
        if (stack.isEmpty()) {
            throw new IllegalStateException("there is no previous audit page");
        }
        String prior = stack.get(stack.size() - 1);
        return new Page(prior.isEmpty() ? null : prior, stack.subList(0, stack.size() - 1));
    }

    static Page next(AiReviewGuiState.History history) {
        if (history.nextCursor() == null
                || history.previousCursors().size() >= MAX_PREVIOUS_PAGES) {
            throw new IllegalStateException("there is no next audit page");
        }
        List<String> stack = new ArrayList<>(history.previousCursors());
        stack.add(history.cursor() == null ? "" : history.cursor());
        return new Page(history.nextCursor(), stack);
    }

    record Page(String cursor, List<String> previousCursors) {
        Page {
            previousCursors = List.copyOf(previousCursors);
        }
    }
}
