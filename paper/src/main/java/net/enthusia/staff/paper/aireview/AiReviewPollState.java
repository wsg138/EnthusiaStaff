package net.enthusia.staff.paper.aireview;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewItem;
import net.enthusia.staff.paper.aireview.AiReviewModels.ReviewPriority;

final class AiReviewPollState {
    private static final Comparator<ReviewItem> ORDER =
            Comparator.comparing((ReviewItem item) -> item.reviewPriority() != ReviewPriority.URGENT)
                    .thenComparing(ReviewItem::occurredAt)
                    .thenComparing(ReviewItem::eventId);

    private final int notifiedLimit;
    private final Set<String> notified = new LinkedHashSet<>();
    private final AtomicReference<Snapshot> snapshot =
            new AtomicReference<>(new Snapshot(List.of(), null, false, "not refreshed"));

    AiReviewPollState(int notifiedLimit) {
        if (notifiedLimit < 1) {
            throw new IllegalArgumentException("notifiedLimit must be positive");
        }
        this.notifiedLimit = notifiedLimit;
    }

    synchronized Update success(List<ReviewItem> items, Instant fetchedAt) {
        List<ReviewItem> ordered = new ArrayList<>(items == null ? List.of() : items);
        ordered.sort(ORDER);
        List<ReviewItem> newItems = new ArrayList<>();
        for (ReviewItem item : ordered) {
            if (notified.add(item.eventId())) {
                newItems.add(item);
            }
        }
        trimNotified();
        Snapshot current = new Snapshot(List.copyOf(ordered), fetchedAt, true, null);
        snapshot.set(current);
        return new Update(current, List.copyOf(newItems));
    }

    void failure(String issue, Instant attemptedAt) {
        Snapshot previous = snapshot.get();
        snapshot.set(new Snapshot(
                previous.items(),
                previous.fetchedAt(),
                false,
                issue == null || issue.isBlank() ? "central review unavailable" : issue
        ));
    }

    Snapshot snapshot() {
        return snapshot.get();
    }

    private void trimNotified() {
        while (notified.size() > notifiedLimit) {
            String oldest = notified.iterator().next();
            notified.remove(oldest);
        }
    }

    record Update(Snapshot snapshot, List<ReviewItem> newlyDiscovered) {
    }

    record Snapshot(List<ReviewItem> items, Instant fetchedAt, boolean authoritative, String issue) {
        Snapshot {
            items = List.copyOf(items == null ? List.of() : items);
        }

        boolean fresh(Instant now, Duration staleAfter) {
            return authoritative
                    && fetchedAt != null
                    && !now.isAfter(fetchedAt.plus(staleAfter));
        }

        int urgentCount() {
            return (int) items.stream()
                    .filter(item -> item.reviewPriority() == ReviewPriority.URGENT)
                    .count();
        }
    }
}
