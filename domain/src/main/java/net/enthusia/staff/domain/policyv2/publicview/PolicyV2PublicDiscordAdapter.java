package net.enthusia.staff.domain.policyv2.publicview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.enthusia.staff.domain.policyv2.persistence.PolicyV2PublicProjection;

/**
 * Discord-neutral presentation built only from the canonical safe projection.
 */
public final class PolicyV2PublicDiscordAdapter {
    private static final int INITIAL_ONLY_TIMELINE_SIZE = 1;

    private PolicyV2PublicDiscordAdapter() {
    }

    public static Message toMessage(PolicyV2PublicProjection projection) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("Player", player(projection)));
        fields.add(new Field("Offense", projection.category() + " · " + projection.publicOffense()));
        fields.add(new Field("Status", status(projection)));
        addIfPresent(fields, "Sanction", joinedSanctions(projection));
        addIfPresent(fields, "Remedy", joinedRemedies(projection));
        addIfPresent(fields, "History", projection.relatedHistorySummary());
        fields.add(new Field("Issued", projection.issuedAt().toString()));
        addIfPresent(fields, "Expires", projection.expiresAt().map(Instant::toString));
        latestUpdate(projection).ifPresent(value -> fields.add(new Field("Latest update", value)));
        return new Message(
                "Punishment " + projection.caseId(),
                projection.publicReason(),
                List.copyOf(fields)
        );
    }

    private static String player(PolicyV2PublicProjection projection) {
        String current = projection.currentPlayerName().orElse("Player");
        return projection.incidentPlayerName()
                .filter(name -> !name.equalsIgnoreCase(current))
                .map(name -> current + " (at incident: " + name + ")")
                .orElse(current);
    }

    private static String status(PolicyV2PublicProjection projection) {
        return projection.status().name() + " · appeal " + projection.appealStatus().name();
    }

    private static Optional<String> joinedSanctions(PolicyV2PublicProjection projection) {
        if (projection.sanctions().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(projection.sanctions().stream()
                .map(sanction -> sanction.summary() + " (" + lower(sanction.status().name()) + ")")
                .collect(Collectors.joining(", ")));
    }

    private static Optional<String> joinedRemedies(PolicyV2PublicProjection projection) {
        if (projection.remedies().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(projection.remedies().stream()
                .map(remedy -> remedy.summary() + " (" + lower(remedy.status().name()) + ")")
                .collect(Collectors.joining(", ")));
    }

    private static Optional<String> latestUpdate(PolicyV2PublicProjection projection) {
        if (projection.timeline().size() <= INITIAL_ONLY_TIMELINE_SIZE) {
            return Optional.empty();
        }
        PolicyV2PublicProjection.PublicRevision latest = projection.timeline().getLast();
        return Optional.of(latest.summary() + " · " + latest.occurredAt());
    }

    private static String lower(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private static void addIfPresent(List<Field> fields, String name, Optional<String> value) {
        value.ifPresent(text -> fields.add(new Field(name, text)));
    }

    public record Message(String title, String description, List<Field> fields) {
        public Message {
            if (title == null || title.isBlank() || description == null || description.isBlank()
                    || fields == null || fields.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Discord public punishment message is invalid");
            }
            fields = List.copyOf(fields);
        }
    }

    public record Field(String name, String value) {
        public Field {
            if (name == null || name.isBlank() || value == null || value.isBlank()) {
                throw new IllegalArgumentException("Discord public punishment field is invalid");
            }
        }
    }
}
