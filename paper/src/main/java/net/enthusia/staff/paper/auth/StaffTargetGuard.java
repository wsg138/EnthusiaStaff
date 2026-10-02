package net.enthusia.staff.paper.auth;

import java.util.UUID;
import net.enthusia.staff.domain.auth.Actor;

@FunctionalInterface
public interface StaffTargetGuard {
    Result check(Actor actor, UUID targetId, boolean systemActor);

    record Result(boolean allowed, String message) {
        public Result {
            if (message == null) {
                throw new IllegalArgumentException("message must be present");
            }
        }

        public static Result allow() {
            return new Result(true, "");
        }

        public static Result deny(String message) {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("denial message must be present");
            }
            return new Result(false, message);
        }
    }
}
