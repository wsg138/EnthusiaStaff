package net.enthusia.staff.domain.modcase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Private, content-free moderation review metadata; never a sanction or welfare disclosure. */
public record CaseNotice(UUID caseId, Origin origin, String scope, long revision,
                         Priority priority, State state, List<String> evidenceRefs,
                         Instant createdAt, Instant expiresAt, String claimant) {
    private static final Pattern SCOPE = Pattern.compile("[a-zA-Z0-9_:/.-]{1,96}");
    private static final Pattern REF = Pattern.compile("[a-zA-Z0-9_:/.-]{1,128}");
    private static final int MAX_EVIDENCE = 24;

    public enum Origin { MINECRAFT, DISCORD }
    public enum Priority { NORMAL, HIGH, URGENT }
    public enum State { OPEN, CLAIMED, RESOLVED }
    public enum Action { CLAIM, RESOLVE }

    public CaseNotice {
        if (caseId == null || origin == null || !valid(SCOPE, scope) || revision < 0
                || priority == null || state == null || evidenceRefs == null
                || evidenceRefs.isEmpty() || evidenceRefs.size() > MAX_EVIDENCE
                || evidenceRefs.stream().anyMatch(ref -> !valid(REF, ref))
                || evidenceRefs.stream().distinct().count() != evidenceRefs.size()
                || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)
                || (claimant != null && !valid(REF, claimant))
                || (state == State.OPEN && claimant != null)
                || (state != State.OPEN && claimant == null)) {
            throw new IllegalArgumentException("invalid scoped moderation case metadata");
        }
        evidenceRefs = List.copyOf(evidenceRefs);
    }

    private static boolean valid(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    public String identity() {
        return origin.name() + "/" + scope + "/" + caseId;
    }

    public CaseNotice apply(Action action, String actor) {
        if (!valid(REF, actor)) {
            throw new IllegalArgumentException("invalid staff actor");
        }
        if (action == Action.CLAIM && state == State.OPEN) {
            return new CaseNotice(caseId, origin, scope, revision + 1, priority,
                    State.CLAIMED, evidenceRefs, createdAt, expiresAt, actor);
        }
        if (action == Action.RESOLVE && state == State.CLAIMED && actor.equals(claimant)) {
            return new CaseNotice(caseId, origin, scope, revision + 1, priority,
                    State.RESOLVED, evidenceRefs, createdAt, expiresAt, actor);
        }
        throw new IllegalStateException("case action not applicable to current state");
    }

    public String safeTitle() {
        return "Moderation review · " + priority.name();
    }
}
