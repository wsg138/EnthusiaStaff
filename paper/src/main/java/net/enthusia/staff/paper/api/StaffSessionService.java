package net.enthusia.staff.paper.api;

import java.util.UUID;

public interface StaffSessionService {
    boolean hasActiveSession(UUID staffId);
}
