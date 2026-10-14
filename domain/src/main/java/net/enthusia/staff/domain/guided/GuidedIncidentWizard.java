package net.enthusia.staff.domain.guided;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, broad-category-first staff questionnaire. This is a shadow
 * diagnostic model: results are not permission to issue any punishment.
 */
public final class GuidedIncidentWizard {
    public record Choice(String id, String label) { }
    public record Question(String id, String text, List<Choice> choices) {
        public Question { choices = List.copyOf(choices); }
    }
    public record Finding(String categoryId, String conduct, String medium,
                          int preliminaryImpact, String candidateId,
                          boolean needsReview, List<String> facts) {
        public Finding { facts = List.copyOf(facts); }
    }
    private static final List<String> CATEGORIES =
            List.of("chat", "gameplay", "safety", "accounts", "community", "economy", "other");
    private final String category;
    private final Map<String, String> answers;

    private GuidedIncidentWizard(String category, Map<String, String> answers) {
        this.category = category;
        this.answers = Map.copyOf(answers);
    }

    public static GuidedIncidentWizard start(String category) {
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown guided incident category");
        }
        return new GuidedIncidentWizard(category, Map.of());
    }

    public String category() { return category; }
    public Map<String, String> answers() { return answers; }

    public Optional<Question> nextQuestion() {
        return path().stream().filter(q -> !answers.containsKey(q.id())).findFirst();
    }

    public List<Question> path() {
        List<Question> result = new ArrayList<>();
        result.add(mediumQuestion());
        if (!answers.containsKey("medium")) { return List.copyOf(result); }
        result.add(conductQuestion());
        if (!answers.containsKey("conduct")) { return List.copyOf(result); }
        String conduct = answers.get("conduct");
        List<Choice> detailed = GuidedOffenseClassifier.details(category, conduct);
        if (!detailed.isEmpty()) {
            result.add(new Question("detail", "Which behavior describes it?", detailed));
            if (!answers.containsKey("detail")) {
                return List.copyOf(result);
            }
        }
        if (List.of("harassment", "slur", "threat", "privacy").contains(conduct)) {
            result.add(new Question("targeting", "Was anyone specifically targeted?", List.of(
                    c("targeted", "Yes - specific player/group"),
                    c("general", "No - general conduct"), c("unknown", "Unclear / quoted"))));
            if (!answers.containsKey("targeting")) {
                return List.copyOf(result);
            }
        }
        if (!"safety".equals(category) && !"other".equals(conduct)) {
            result.add(new Question("extent", "How extensive was it?", List.of(
                    c("few", "Minor / isolated"), c("sustained", "Repeated / sustained"),
                    c("large-scale", "Severe / large scale"), c("unknown", "Not sure"))));
            if (!answers.containsKey("extent")) {
                return List.copyOf(result);
            }
            result.add(new Question("continued", "Did they continue after a warning?", List.of(
                    c("yes", "Yes - ignored warning"), c("no", "No - stopped after warning"),
                    c("not-applicable", "N/A - no warning given"), c("unknown", "Not sure"))));
            if (!answers.containsKey("continued")) {
                return List.copyOf(result);
            }
        }
        if (("sign".equals(answers.get("medium")) || "book".equals(answers.get("medium")))
                && List.of("spam", "advertising").contains(conduct)) {
            result.add(new Question("persistence", "Was the content left in place?", List.of(
                    c("persistent", "Yes"), c("removed", "Removed promptly"),
                    c("unknown", "Not sure"))));
        }
        return List.copyOf(result);
    }

    public GuidedIncidentWizard answer(String id, String choiceId) {
        List<Question> path = path();
        Question question = path.stream().filter(q -> q.id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Irrelevant question"));
        if (question.choices().stream().noneMatch(c -> c.id().equals(choiceId))) {
            throw new IllegalArgumentException("Invalid question choice");
        }
        if (!answers.containsKey(id) && !nextQuestion().orElseThrow().id().equals(id)) {
            throw new IllegalArgumentException("Earlier questions must be answered first");
        }
        Map<String, String> updated = new LinkedHashMap<>();
        for (Question prior : path) {
            if (prior.id().equals(id)) { updated.put(id, choiceId); break; }
            if (answers.containsKey(prior.id())) {
                updated.put(prior.id(), answers.get(prior.id()));
            }
        }
        return new GuidedIncidentWizard(category, updated);
    }

    public GuidedIncidentWizard back() {
        List<Question> path = path();
        String previous = null;
        for (Question question : path) {
            if (!answers.containsKey(question.id())) { break; }
            previous = question.id();
        }
        if (previous == null) { return this; }
        Map<String, String> updated = new LinkedHashMap<>(answers);
        updated.remove(previous);
        return new GuidedIncidentWizard(category, updated);
    }

    public Finding finding() {
        if (nextQuestion().isPresent()) {
            throw new IllegalStateException("The interview is incomplete");
        }
        String medium = answers.get("medium");
        String conduct = answers.get("conduct");
        int impact = 10;
        List<String> facts = new ArrayList<>();
        if ("sustained".equals(answers.get("extent"))) {
            impact += 15; facts.add("Sustained/repeated incident");
        } else if ("large-scale".equals(answers.get("extent"))) {
            impact += 30; facts.add("Large-scale incident");
        }
        if ("yes".equals(answers.get("continued"))) {
            impact += 10; facts.add("Continued after a verified warning");
        }
        if ("targeted".equals(answers.get("targeting"))) {
            impact += 15; facts.add("Directed at a person or group");
        }
        if ("persistent".equals(answers.get("persistence"))) {
            impact += 10; facts.add("Placed content was left up");
        }
        String candidate = GuidedOffenseClassifier.resolve(
                category, medium, conduct, answers.get("detail"),
                answers.get("extent"), answers.get("targeting"));
        boolean review = candidate == null || "safety".equals(category)
                || List.of("slur", "threat", "privacy", "hate").contains(conduct)
                || answers.values().contains("unknown")
                || "other".equals(conduct)
                || ("chat".equals(category) && !"public-chat".equals(medium)
                    && List.of("language", "politics").contains(conduct));
        return new Finding(category, conduct, medium, impact, candidate, review, facts);
    }

    private Question mediumQuestion() {
        if ("chat".equals(category)) {
            return new Question("medium", "Where did it happen?", List.of(
                    c("public-chat", "Public chat"), c("direct-message", "Private message"),
                    c("sign", "Sign"), c("book", "Book"),
                    c("voice", "Voice chat"), c("profile", "Profile / item"),
                    c("unknown", "Other / not sure")));
        }
        return new Question("medium", "Where did it happen?", List.of(
                c("in-game", "In game"), c("chat", "Chat / message"),
                c("discord", "Discord"), c("account", "Account / login"),
                c("market", "Trade / economy"), c("unknown", "Other / unsure")));
    }

    private Question conductQuestion() {
        List<Choice> options = switch (category) {
            case "chat" -> List.of(c("spam", "Spam / repetition"),
                    c("harassment", "Harassment"), c("slur", "Slur / discrimination"),
                    c("hate", "Hate or extremist content"),
                    c("politics", "Political content"), c("language", "Other language"),
                    c("other", "Other / unsure"));
            case "gameplay" -> List.of(c("cheating", "Cheating"),
                    c("exploit", "Exploits / duplication"),
                    c("mechanics", "Disruptive mechanics"),
                    c("complicity", "Assisted rule breaking"),
                    c("other", "Other / unsure"));
            case "safety" -> List.of(c("threat", "Threat / danger"),
                    c("privacy", "Private information"), c("other", "Other / unsure"));
            case "accounts" -> List.of(c("access", "Account access"),
                    c("evasion", "Punishment evasion"),
                    c("identity", "Impersonation / profile"), c("other", "Other / unsure"));
            case "community" -> List.of(c("advertising", "Advertising"),
                    c("reports", "Report abuse"), c("content", "Inappropriate content"),
                    c("staff", "Staff instructions"), c("dishonesty", "Dishonesty / evidence"),
                    c("other", "Other / unsure"));
            case "economy" -> List.of(c("market", "Market abuse"),
                    c("reputation", "Reputation manipulation"),
                    c("other", "Other / unsure"));
            default -> List.of(c("other", "Other / unclassified"));
        };
        return new Question("conduct", "What happened?", options);
    }

    private static Choice c(String id, String label) {
        return new Choice(id, label);
    }
}
