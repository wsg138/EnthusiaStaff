package net.enthusia.staff.paper.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaffInspectionAuthorityTest {
    @Test
    void donorWithAccidentalViewPermissionCannotReadStaffViews() {
        for (String view : Set.of("enthusiastaff.inventory.view", "enthusiastaff.inspect")) {
            Set<String> donor = Set.of(view, "devotee");
            assertFalse(StaffInspectionAuthority.allows(donor::contains, view));
            Set<String> staff = Set.of(view, "enthusiastaff.identity.helper");
            assertTrue(StaffInspectionAuthority.allows(staff::contains, view));
            assertFalse(StaffInspectionAuthority.allows("enthusiastaff.identity.helper"::equals, view));
        }
    }
}
