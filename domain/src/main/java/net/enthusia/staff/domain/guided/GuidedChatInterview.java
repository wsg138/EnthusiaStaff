package net.enthusia.staff.domain.guided;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only chat incident interview. Never grants sanction authority. */
public final class GuidedChatInterview {
    public enum Key { MEDIUM, CONDUCT, EXTENT, CONTINUED, PERSISTENCE, TARGETING, THREAT }
    public record Question(Key id, String prompt, List<String> choices) {
        public Question { choices = List.copyOf(choices); }
    }
    public record Finding(String candidateId, int impact, List<String> factors,
                          boolean reviewRequired, String reviewReason) {
        public Finding { factors = List.copyOf(factors); }
    }

    private final Map<Key, String> answers;
    private GuidedChatInterview(Map<Key, String> answers) {
        this.answers = Map.copyOf(answers);
    }
    public static GuidedChatInterview start() { return new GuidedChatInterview(Map.of()); }
    public Map<Key, String> answers() { return answers; }

    public List<Question> path() {
        List<Question> questions = new ArrayList<>();
        questions.add(question(Key.MEDIUM));
        if (!answers.containsKey(Key.MEDIUM)) { return List.copyOf(questions); }
        questions.add(question(Key.CONDUCT));
        if (!answers.containsKey(Key.CONDUCT)) { return List.copyOf(questions); }
        switch (answers.get(Key.CONDUCT)) {
            case "spam", "advertising" -> {
                questions.add(question(Key.EXTENT));
                questions.add(question(Key.CONTINUED));
                if (List.of("sign", "book").contains(answers.get(Key.MEDIUM))) {
                    questions.add(question(Key.PERSISTENCE));
                }
            }
            case "harassment", "slur" -> questions.add(question(Key.TARGETING));
            case "threat-privacy" -> questions.add(question(Key.THREAT));
            default -> { }
        }
        // Evidence is an optional review attachment, not a mandatory survey page.
        // The wizard gathers facts; the remedy engine calculates type + duration.
        return List.copyOf(questions);
    }

    public Optional<Question> nextQuestion() {
        return path().stream().filter(q -> !answers.containsKey(q.id())).findFirst();
    }

    /** Editing an answer clears dependent answers, even if the new path differs. */
    public GuidedChatInterview answer(Key key, String value) {
        List<Question> questions = path();
        Question target = questions.stream().filter(q -> q.id() == key).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Irrelevant question"));
        if (!target.choices().contains(value)) {
            throw new IllegalArgumentException("Invalid incident answer");
        }
        if (!answers.containsKey(key) && nextQuestion().orElseThrow().id() != key) {
            throw new IllegalArgumentException("Cannot skip ahead of required questions");
        }
        Map<Key, String> result = new EnumMap<>(Key.class);
        for (Question q : questions) {
            if (q.id() == key) {
                result.put(key, value);
                break;
            }
            if (answers.containsKey(q.id())) { result.put(q.id(), answers.get(q.id())); }
        }
        return new GuidedChatInterview(result);
    }

    /** Preliminary impact is explanatory, NOT a punishment or approved policy. */
    public Finding finding() {
        if (nextQuestion().isPresent()) {
            throw new IllegalStateException("The incident interview is incomplete");
        }
        String medium = answers.get(Key.MEDIUM);
        String conduct = answers.get(Key.CONDUCT);
        int impact = switch (conduct) {
            case "spam" -> 10; case "advertising" -> 25;
            case "harassment" -> 35; case "slur" -> 55;
            case "threat-privacy" -> 60; default -> 0;
        };
        List<String> factors = new ArrayList<>();
        if ("sustained".equals(answers.get(Key.EXTENT))) {
            impact += 15; factors.add("Sustained behavior");
        }
        if ("large-scale".equals(answers.get(Key.EXTENT))) {
            impact += 30; factors.add("Large-scale behavior");
        }
        if ("yes".equals(answers.get(Key.CONTINUED))) {
            impact += 10; factors.add("Continued after warning");
        }
        if ("persistent".equals(answers.get(Key.PERSISTENCE))) {
            impact += 10; factors.add("Persistent placed content");
        }
        if ("targeted".equals(answers.get(Key.TARGETING))) {
            impact += 15; factors.add("Targeted impact");
        }
        String candidate = candidate(medium, conduct, answers.get(Key.EXTENT),
                answers.get(Key.TARGETING), answers.get(Key.THREAT));
        String review = reviewReason(medium, conduct, candidate);
        return new Finding(candidate, Math.min(100, impact), factors,
                !review.isEmpty(), review);
    }

    private String reviewReason(String medium, String conduct, String candidate) {
        if (candidate == null) {
            return "No validated offense matches these incident facts";
        }
        if ("unknown".equals(answers.get(Key.TARGETING))
                || "unknown".equals(answers.get(Key.THREAT))) {
            return "Targeting or threat context is unclear";
        }
        if (List.of("slur", "threat-privacy").contains(conduct)) {
            return "Sensitive conduct requires contextual evidence review";
        }
        return "";
    }

    private static String candidate(String medium, String conduct, String extent,
                                    String targeting, String threat) {
        if ("unknown".equals(medium) || "spam".equals(conduct) && "unknown".equals(extent)) {
            return null;
        }
        return switch (conduct) {
            case "spam" -> switch (medium) {
                case "public-chat" -> "sustained".equals(extent) || "large-scale".equals(extent)
                        ? "chat.spam.flooding" : "chat.spam.low-level";
                case "direct-message" -> "chat.spam.private-message";
                case "voice" -> "chat.disruption.noise";
                default -> null;
            };
            case "harassment" -> "targeted".equals(targeting)
                    ? "abuse.targeted-harassment" : null;
            case "slur" -> "hate.slur";
            case "threat-privacy" -> "credible".equals(threat)
                    ? "safety.credible-threat" : null;
            default -> null;
        };
    }

    private static Question question(Key key) {
        return switch (key) {
            case MEDIUM -> new Question(key, "Where did this happen?", List.of(
                    "public-chat", "direct-message", "sign", "book", "voice", "profile-item", "unknown"));
            case CONDUCT -> new Question(key, "What happened?", List.of(
                    "spam", "harassment", "slur", "threat-privacy", "advertising", "other"));
            case EXTENT -> new Question(key, "How extensive was it?", List.of(
                    "few", "sustained", "large-scale", "unknown"));
            case CONTINUED -> new Question(key, "Did it continue after a clear warning?", List.of(
                    "yes", "no", "not-applicable", "unknown"));
            case PERSISTENCE -> new Question(key, "Was placed content left up?", List.of(
                    "persistent", "removed", "unknown"));
            case TARGETING -> new Question(key, "Who was targeted?", List.of(
                    "targeted", "general", "unknown"));
            case THREAT -> new Question(key, "Is credible harm alleged?", List.of(
                    "credible", "not-credible", "unknown"));
        };
    }
}
