package net.enthusia.staff.domain.guided;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String CHAT = "chat";
    private static final String SAFETY = "safety";
    private static final String MEDIUM = "medium";
    private static final String CONDUCT = "conduct";
    private static final String DETAIL = "detail";
    private static final String EXTENT = "extent";
    private static final String TARGETING = "targeting";
    private static final String CONTINUED = "continued";
    private static final String UNKNOWN = "unknown";
    private static final String OTHER_UNSURE = "Other / unsure";
    private static final String OTHER = "other";
    private static final List<String> CATEGORIES =
            List.of(CHAT, "gameplay", SAFETY, "accounts", "community", "economy", OTHER);
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
        if (appendUnanswered(result, mediumQuestion())) { return List.copyOf(result); }
        if (appendUnanswered(result, conductQuestion())) { return List.copyOf(result); }
        return detailPath(result);
    }

    private List<Question> detailPath(List<Question> result) {
        List<Choice> details = GuidedOffenseClassifier.details(category, answers.get(CONDUCT));
        if (!details.isEmpty() && appendUnanswered(result,
                new Question(DETAIL, "Which behavior describes it?", details))) {
            return List.copyOf(result);
        }
        return targetingPath(result);
    }

    private List<Question> targetingPath(List<Question> result) {
        if (List.of("harassment", "slur", "threat", "privacy").contains(answers.get(CONDUCT))
                && appendUnanswered(result, new Question(TARGETING,
                        "Was anyone specifically targeted?", List.of(
                        c("targeted", "Yes - specific player/group"),
                        c("general", "No - general conduct"), c(UNKNOWN, "Unclear / quoted"))))) {
            return List.copyOf(result);
        }
        return severityPath(result);
    }

    private List<Question> severityPath(List<Question> result) {
        if (!SAFETY.equals(category) && !OTHER.equals(answers.get(CONDUCT))) {
            if (appendUnanswered(result, new Question(EXTENT, "How extensive was it?",
                    List.of(c("few", "Minor / isolated"), c("sustained", "Repeated / sustained"),
                            c("large-scale", "Severe / large scale"), c(UNKNOWN, "Not sure"))))) {
                return List.copyOf(result);
            }
            if (appendUnanswered(result, new Question(CONTINUED,
                    "Did they continue after a warning?", List.of(
                    c("yes", "Yes - ignored warning"), c("no", "No - stopped after warning"),
                    c("not-applicable", "N/A - no warning given"), c(UNKNOWN, "Not sure"))))) {
                return List.copyOf(result);
            }
        }
        return persistencePath(result);
    }

    private List<Question> persistencePath(List<Question> result) {
        String medium = answers.get(MEDIUM);
        if (List.of("sign", "book").contains(medium)
                && List.of("spam", "advertising").contains(answers.get(CONDUCT))) {
            result.add(new Question("persistence", "Was the content left in place?",
                    List.of(c("persistent", "Yes"), c("removed", "Removed promptly"),
                            c(UNKNOWN, "Not sure"))));
        }
        return List.copyOf(result);
    }

    private boolean appendUnanswered(List<Question> result, Question question) {
        result.add(question);
        return !answers.containsKey(question.id());
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
        String medium = answers.get(MEDIUM);
        String conduct = answers.get(CONDUCT);
        int impact = 10;
        List<String> facts = new ArrayList<>();
        if ("sustained".equals(answers.get(EXTENT))) {
            impact += 15; facts.add("Sustained/repeated incident");
        } else if ("large-scale".equals(answers.get(EXTENT))) {
            impact += 30; facts.add("Large-scale incident");
        }
        if ("yes".equals(answers.get(CONTINUED))) {
            impact += 10; facts.add("Continued after a verified warning");
        }
        if ("targeted".equals(answers.get(TARGETING))) {
            impact += 15; facts.add("Directed at a person or group");
        }
        if ("persistent".equals(answers.get("persistence"))) {
            impact += 10; facts.add("Placed content was left up");
        }
        String candidate = GuidedOffenseClassifier.resolve(
                category, medium, conduct, answers.get(DETAIL),
                answers.get(EXTENT), answers.get(TARGETING));
        boolean review = candidate == null || SAFETY.equals(category)
                || List.of("slur", "threat", "privacy", "hate").contains(conduct)
                || UNKNOWN.equals(medium)
                || UNKNOWN.equals(answers.get(DETAIL))
                || UNKNOWN.equals(answers.get(EXTENT))
                || UNKNOWN.equals(answers.get(TARGETING))
                || OTHER.equals(conduct)
                || sensitiveCandidate(candidate)
                || !channelCompatible(medium, conduct);
        return new Finding(category, conduct, medium, impact, candidate, review, facts);
    }

    private static boolean sensitiveCandidate(String candidate) {
        if (candidate == null) {
            return false;
        }
        return List.of("harassment.sexual", "advertising.malicious-link",
                "exploit.server-crash-attempt", "account.theft",
                "safety.credible-threat", "privacy.doxxing",
                "dishonesty.falsifying-evidence").contains(candidate);
    }

    private boolean channelCompatible(String medium, String conduct) {
        if ("gameplay".equals(category)) {
            return "in-game".equals(medium);
        }
        if ("economy".equals(category)) {
            return "in-game".equals(medium) || "market".equals(medium);
        }
        if (CHAT.equals(category) && List.of("language", "politics").contains(conduct)) {
            return "public-chat".equals(medium);
        }
        return true;
    }

    private Question mediumQuestion() {
        if (CHAT.equals(category)) {
            return new Question(MEDIUM, "Where did it happen?", List.of(
                    c("public-chat", "Public chat"), c("direct-message", "Private message"),
                    c("sign", "Sign"), c("book", "Book"),
                    c("voice", "Voice chat"), c("profile", "Profile / item"),
                    c(UNKNOWN, "Other / not sure")));
        }
        return new Question(MEDIUM, "Where did it happen?", List.of(
                c("in-game", "In game"), c(CHAT, "Chat / message"),
                c("discord", "Discord"), c("account", "Account / login"),
                c("market", "Trade / economy"), c(UNKNOWN, OTHER_UNSURE)));
    }

    private Question conductQuestion() {
        List<Choice> options = switch (category) {
            case CHAT -> List.of(c("spam", "Spam / repetition"),
                    c("harassment", "Harassment"), c("slur", "Slur / discrimination"),
                    c("hate", "Hate or extremist content"),
                    c("politics", "Political content"), c("language", "Other language"),
                    c(OTHER, OTHER_UNSURE));
            case "gameplay" -> List.of(c("cheating", "Cheating"),
                    c("exploit", "Exploits / duplication"),
                    c("mechanics", "Disruptive mechanics"),
                    c("complicity", "Assisted rule breaking"),
                    c(OTHER, OTHER_UNSURE));
            case SAFETY -> List.of(c("threat", "Threat / danger"),
                    c("privacy", "Private information"), c(OTHER, OTHER_UNSURE));
            case "accounts" -> List.of(c("access", "Account access"),
                    c("evasion", "Punishment evasion"),
                    c("identity", "Impersonation / profile"), c(OTHER, OTHER_UNSURE));
            case "community" -> List.of(c("advertising", "Advertising"),
                    c("reports", "Report abuse"), c("content", "Inappropriate content"),
                    c("staff", "Staff instructions"), c("dishonesty", "Dishonesty / evidence"),
                    c(OTHER, OTHER_UNSURE));
            case "economy" -> List.of(c("market", "Market abuse"),
                    c("reputation", "Reputation manipulation"),
                    c(OTHER, OTHER_UNSURE));
            default -> List.of(c(OTHER, "Other / unclassified"));
        };
        return new Question(CONDUCT, "What happened?", options);
    }

    private static Choice c(String id, String label) {
        return new Choice(id, label);
    }
}
