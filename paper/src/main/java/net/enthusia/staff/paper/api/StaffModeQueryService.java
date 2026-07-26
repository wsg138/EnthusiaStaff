package net.enthusia.staff.paper.api;

import java.util.UUID;

public interface StaffModeQueryService {
    boolean isInStaffMode(UUID staffId);
}
