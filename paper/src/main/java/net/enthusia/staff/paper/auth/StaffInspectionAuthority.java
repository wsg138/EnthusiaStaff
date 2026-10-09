package net.enthusia.staff.paper.auth;

import java.util.function.Predicate;

/** Sensitive player views require both a staff identity and explicit view authority. */
public final class StaffInspectionAuthority {
    private StaffInspectionAuthority() { }

    public static boolean allows(Predicate<String> permissions, String viewPermission) {
        return permissions.test(viewPermission) && PaperStaffRankResolver.resolve(permissions).isPresent();
    }
}
