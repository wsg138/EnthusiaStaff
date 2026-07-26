package net.enthusia.staff.domain.auth;

@FunctionalInterface
public interface AuthorizationPolicy {
    boolean permits(Actor actor, ModerationAction action);
}
