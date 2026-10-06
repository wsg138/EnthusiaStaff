package net.enthusia.staff.paper.api;

import java.util.UUID;

/**
 * Answers whether a player currently has usable Staff mutation authority.
 *
 * <p>This is deliberately distinct from {@link StaffSessionService}: ordinary staff require a
 * usable Staff Mode session, while explicitly unrestricted identities may hold authority without
 * opening a durable Staff Mode snapshot.</p>
 */
@FunctionalInterface
public interface StaffAuthorityService {
    boolean hasAuthority(UUID staffId);
}
