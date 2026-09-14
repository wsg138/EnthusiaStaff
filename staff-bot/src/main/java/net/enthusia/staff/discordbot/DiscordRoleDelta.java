package net.enthusia.staff.discordbot;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure managed-role delta calculation; unmanaged Discord roles are never removal candidates. */
record DiscordRoleDelta(Set<String> add, Set<String> remove) {
    DiscordRoleDelta {
        if (add == null || remove == null) {
            throw new IllegalArgumentException("role delta sets must be present");
        }
        add = Set.copyOf(add);
        remove = Set.copyOf(remove);
    }

    static DiscordRoleDelta calculate(Set<String> desired, Set<String> observed, Set<String> managed) {
        if (desired == null || observed == null || managed == null || !managed.containsAll(desired)) {
            throw new IllegalArgumentException("role delta inputs are invalid");
        }
        Set<String> add = new LinkedHashSet<>(desired);
        add.removeAll(observed);
        Set<String> remove = new LinkedHashSet<>(observed);
        remove.retainAll(managed);
        remove.removeAll(desired);
        return new DiscordRoleDelta(add, remove);
    }

    boolean empty() {
        return add.isEmpty() && remove.isEmpty();
    }
}
